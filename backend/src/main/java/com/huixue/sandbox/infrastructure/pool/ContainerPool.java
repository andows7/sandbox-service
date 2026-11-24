package com.huixue.sandbox.infrastructure.pool;

import com.huixue.sandbox.common.enums.LanguageType;
import com.huixue.sandbox.config.DockerProperties;
import com.huixue.sandbox.infrastructure.docker.DockerContainerManager;
import com.huixue.sandbox.infrastructure.docker.DockerContainerSpec;
import com.huixue.sandbox.infrastructure.persistence.entity.ContainerTaskEntity;
import com.huixue.sandbox.infrastructure.persistence.mapper.ContainerTaskMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** One submission per container. Warming and cleanup reservations count toward both caps. */
@Slf4j
@Component
public class ContainerPool {
    private final DockerContainerManager manager;
    private final DockerProperties properties;
    private final ContainerTaskMapper tasks;
    private final Object lock = new Object();
    private final Map<LanguageType, ArrayDeque<PooledContainer>> idle = new EnumMap<>(LanguageType.class);
    private final Map<LanguageType, Integer> counts = new EnumMap<>(LanguageType.class);
    private final Map<String, PooledContainer> managed = new HashMap<>();
    private ThreadPoolExecutor warmer;
    private int total;
    private boolean closed;

    public ContainerPool(DockerContainerManager manager, DockerProperties properties, ContainerTaskMapper tasks) {
        this.manager = manager;
        this.properties = properties;
        this.tasks = tasks;
    }

    @PostConstruct
    public void init() {
        var p = properties.getPool();
        if (p.getCoreSize() < 0 || p.getMaxSize() < 1 || p.getMaxTotal() < 1
                || p.getCoreSize() > p.getMaxSize()
                || (long) p.getCoreSize() * LanguageType.values().length > p.getMaxTotal()) {
            throw new IllegalArgumentException("Invalid pool limits: core-size * languages must fit max-total");
        }
        for (LanguageType lang : LanguageType.values()) {
            idle.put(lang, new ArrayDeque<>());
            counts.put(lang, 0);
        }
        warmer = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(p.getMaxTotal()), r -> {
                    Thread thread = new Thread(r, "sandbox-pool-warmer");
                    thread.setDaemon(true);
                    return thread;
                });
        for (LanguageType lang : LanguageType.values()) {
            for (int i = 0; i < p.getCoreSize(); i++) replenishAsync(lang);
        }
        log.info("Container pool: core={} per language, max={} per language, global max={}",
                p.getCoreSize(), p.getMaxSize(), p.getMaxTotal());
    }

    private boolean reserve(LanguageType lang) {
        if (closed || total >= properties.getPool().getMaxTotal()
                || counts.get(lang) >= properties.getPool().getMaxSize()) return false;
        total++;
        counts.put(lang, counts.get(lang) + 1);
        return true;
    }

    private void release(LanguageType lang) {
        synchronized (lock) {
            total--;
            counts.put(lang, counts.get(lang) - 1);
            lock.notifyAll();
        }
    }

    public void replenishAsync(LanguageType lang) {
        synchronized (lock) {
            if (counts.get(lang) >= properties.getPool().getCoreSize() || !reserve(lang)) return;
        }
        try {
            warmer.execute(() -> {
                PooledContainer container;
                try {
                    container = createNewContainer(lang);
                } catch (Exception e) {
                    if (!(e instanceof CleanupPendingException)) release(lang);
                    log.warn("Failed to prewarm {} container", lang, e);
                    return;
                }
                synchronized (lock) {
                    if (!closed) {
                        managed.put(container.getContainerId(), container);
                        idle.get(lang).addLast(container);
                        lock.notifyAll();
                        return;
                    }
                }
                destroy(container);
            });
        } catch (java.util.concurrent.RejectedExecutionException e) {
            release(lang);
        }
    }

    public PooledContainer borrowContainer(LanguageType lang, long timeoutMs) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        synchronized (lock) {
            while (true) {
                if (closed) return null;
                PooledContainer container = idle.get(lang).pollFirst();
                if (container != null) {
                    allocate(container);
                    return container;
                }
                if (reserve(lang)) break;
                long left = deadline - System.nanoTime();
                if (left <= 0) return null;
                TimeUnit.NANOSECONDS.timedWait(lock, left);
            }
        }
        PooledContainer container;
        try {
            container = createNewContainer(lang);
        } catch (Exception e) {
            if (!(e instanceof CleanupPendingException)) release(lang);
            log.warn("Failed to create {} container", lang, e);
            return null;
        }
        synchronized (lock) {
            if (!closed) {
                managed.put(container.getContainerId(), container);
                allocate(container);
                return container;
            }
        }
        destroy(container);
        return null;
    }

    private void allocate(PooledContainer container) {
        container.setStatus(PooledContainer.ContainerStatus.RUNNING);
        container.setAllocateTime(System.currentTimeMillis());
        recordStatus(container.getContainerId(), "RUNNING");
    }

    public void returnContainer(PooledContainer container) {
        if (container == null) return;
        synchronized (lock) {
            if (managed.remove(container.getContainerId()) == null) return;
            idle.get(container.getLanguage()).remove(container);
            container.setStatus(PooledContainer.ContainerStatus.DEAD);
        }
        // Cleanup in the caller provides backpressure without creating more threads.
        destroy(container);
        replenishAsync(container.getLanguage());
    }

    private PooledContainer createNewContainer(LanguageType lang) throws IOException {
        String uuid = UUID.randomUUID().toString();
        Path dir = Files.createDirectories(Path.of(properties.getWorkDir()).resolve(uuid));
        String hostBase = properties.getHostWorkDir();
        String hostDir = hostBase == null || hostBase.isBlank() ? dir.toString() : Path.of(hostBase, uuid).toString();
        String image = switch (lang) {
            case JAVA -> "sandbox-java-openjdk17:1.0";
            case C -> "sandbox-c-gcc11:1.0";
            case CPP -> "sandbox-cpp-gcc11:1.0";
            case PYTHON -> "sandbox-python-python3.10:1.0";
        };
        String id = null;
        PooledContainer container = null;
        try {
            if (Files.getFileStore(dir).supportsFileAttributeView("posix")) {
                Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("rwxrwxrwx"));
            }
            var spec = DockerContainerSpec.builder()
                    .image(image).containerName("sandbox_" + lang.name().toLowerCase(java.util.Locale.ROOT) + "_" + uuid)
                    .buildDirHostPath(Path.of(System.getProperty("user.dir"), "docker-env", lang.name().toLowerCase(java.util.Locale.ROOT)).toString())
                    .workDirHostPath(hostDir).workDirContainerPath("/sandbox/workdir")
                    .cpuCount(properties.getCpuCount()).memoryLimitMb(properties.getMemoryMb())
                    .pidLimit(properties.getPidLimit()).build();
            id = manager.createContainer(spec);
            container = new PooledContainer();
            container.setContainerId(id);
            container.setLanguage(lang);
            container.setWorkDirHostPath(dir.toString());
            container.setWorkDirContainerPath(spec.getWorkDirContainerPath());
            container.setStatus(PooledContainer.ContainerStatus.IDLE);
            manager.startContainer(id);
            recordStatus(id, "IDLE");
            return container;
        } catch (Exception e) {
            if (id != null) {
                try { manager.stopAndRemoveContainer(id); }
                catch (RuntimeException cleanup) {
                    container.setStatus(PooledContainer.ContainerStatus.DEAD);
                    synchronized (lock) { managed.put(id, container); }
                    recordStatus(id, "DEAD");
                    throw new CleanupPendingException(cleanup);
                }
            }
            cleanWorkDir(dir);
            throw e;
        }
    }

    private void destroy(PooledContainer container) {
        try {
            manager.stopAndRemoveContainer(container.getContainerId());
        } catch (RuntimeException e) {
            // A failed deletion still consumes host resources: retain the reservation.
            container.setStatus(PooledContainer.ContainerStatus.DEAD);
            synchronized (lock) { managed.put(container.getContainerId(), container); }
            recordStatus(container.getContainerId(), "DEAD");
            log.warn("Container cleanup pending; capacity remains reserved: {}", container.getContainerId(), e);
            return;
        }
        try {
            cleanWorkDir(Path.of(container.getWorkDirHostPath()));
            try { tasks.deleteById(container.getContainerId()); }
            catch (Exception e) { log.warn("Failed to remove container status", e); }
        } finally {
            release(container.getLanguage());
        }
    }

    private void cleanWorkDir(Path directory) {
        Path base = Path.of(properties.getWorkDir()).toAbsolutePath().normalize();
        Path target = directory.toAbsolutePath().normalize();
        if (!target.startsWith(base) || target.equals(base)) throw new IllegalArgumentException("Invalid workdir");
        // Does not follow symlinks created by submitted programs.
        try {
            Files.walkFileTree(target, new java.nio.file.SimpleFileVisitor<>() {
                @Override
                public java.nio.file.FileVisitResult visitFile(Path file, java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return java.nio.file.FileVisitResult.CONTINUE;
                }
                @Override
                public java.nio.file.FileVisitResult postVisitDirectory(Path dir, IOException error) throws IOException {
                    if (error != null) throw error;
                    Files.delete(dir);
                    return java.nio.file.FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) { log.warn("Failed to clean workdir {}", target, e); }
    }

    private void recordStatus(String id, String status) {
        try {
            ContainerTaskEntity entity = new ContainerTaskEntity();
            entity.setContainerId(id);
            entity.setStatus(status);
            entity.setAllocateTime(LocalDateTime.now());
            if (tasks.selectById(id) == null) tasks.insert(entity);
            else tasks.updateById(entity);
        } catch (Exception e) { log.warn("Failed to record container status {}", id, e); }
    }

    public boolean isManaged(String id) {
        synchronized (lock) { return managed.containsKey(id); }
    }

    private static class CleanupPendingException extends RuntimeException {
        CleanupPendingException(Throwable cause) { super("Container cleanup is pending", cause); }
    }

    public int getTotalContainers() {
        synchronized (lock) { return total; }
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 30000)
    public void retryFailedCleanup() {
        java.util.List<PooledContainer> pending;
        synchronized (lock) {
            pending = managed.values().stream()
                    .filter(c -> c.getStatus() == PooledContainer.ContainerStatus.DEAD).toList();
        }
        pending.forEach(this::returnContainer);
    }

    @PreDestroy
    public void close() {
        java.util.List<PooledContainer> remaining;
        synchronized (lock) {
            closed = true;
            lock.notifyAll();
            remaining = java.util.List.copyOf(managed.values());
        }
        if (warmer != null) warmer.shutdown();
        remaining.forEach(this::returnContainer);
    }
}

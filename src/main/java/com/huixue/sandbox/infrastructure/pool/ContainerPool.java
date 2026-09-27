package com.huixue.sandbox.infrastructure.pool;

import com.huixue.sandbox.config.DockerProperties;
import com.huixue.sandbox.infrastructure.docker.DockerContainerManager;
import com.huixue.sandbox.infrastructure.docker.DockerContainerSpec;
import com.huixue.sandbox.infrastructure.persistence.entity.ContainerTaskEntity;
import com.huixue.sandbox.infrastructure.persistence.mapper.ContainerTaskMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class ContainerPool {

    private final DockerContainerManager containerManager;
    private final DockerProperties dockerProperties;
    private final ContainerTaskMapper containerTaskMapper;
    private BlockingQueue<PooledContainer> idlePool;
    private final AtomicInteger totalContainers = new AtomicInteger(0);

    public ContainerPool(DockerContainerManager containerManager, DockerProperties dockerProperties, ContainerTaskMapper containerTaskMapper) {
        this.containerManager = containerManager;
        this.dockerProperties = dockerProperties;
        this.containerTaskMapper = containerTaskMapper;
    }

    @PostConstruct
    public void init() {
        int coreSize = dockerProperties.getPool().getCoreSize();
        int maxSize = dockerProperties.getPool().getMaxSize();
        idlePool = new ArrayBlockingQueue<>(maxSize);
        
        // Clear all tasks from previous run in DB (optional since MEMORY table clears on restart, but good for hot reload)
        containerTaskMapper.delete(null);

        log.info("Initializing container pool. Prewarming {} containers...", coreSize);
        for (int i = 0; i < coreSize; i++) {
            replenishAsync();
        }
    }

    public void replenishAsync() {
        if (totalContainers.get() >= dockerProperties.getPool().getMaxSize()) {
            return;
        }
        totalContainers.incrementAndGet();
        new Thread(() -> {
            try {
                PooledContainer container = createNewContainer();
                idlePool.offer(container);
                log.info("Replenished container: {}. Total containers: {}", container.getContainerId(), totalContainers.get());
                recordContainerTaskStatus(container.getContainerId(), PooledContainer.ContainerStatus.IDLE);
            } catch (Exception e) {
                totalContainers.decrementAndGet();
                log.error("Failed to replenish container", e);
            }
        }).start();
    }

    private PooledContainer createNewContainer() {
        String uuid = UUID.randomUUID().toString();
        String hostWorkDir = System.getProperty("java.io.tmpdir") + File.separator + "sandbox_" + uuid;
        File dir = new File(hostWorkDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        DockerContainerSpec spec = DockerContainerSpec.builder()
                .image("openjdk:17-jdk-slim")
                .cpuCount(dockerProperties.getCpuCount())
                .memoryLimitMb(dockerProperties.getMemoryMb())
                .pidLimit(dockerProperties.getPidLimit())
                .workDirHostPath(hostWorkDir)
                .workDirContainerPath("/sandbox/workdir")
                .build();

        String containerId = containerManager.createContainer(spec);
        containerManager.startContainer(containerId);

        PooledContainer pc = new PooledContainer();
        pc.setContainerId(containerId);
        pc.setWorkDirHostPath(hostWorkDir);
        pc.setWorkDirContainerPath(spec.getWorkDirContainerPath());
        pc.setStatus(PooledContainer.ContainerStatus.IDLE);
        return pc;
    }

    public PooledContainer borrowContainer(long timeoutMs) throws InterruptedException {
        PooledContainer container = idlePool.poll(timeoutMs, TimeUnit.MILLISECONDS);
        if (container != null) {
            container.setStatus(PooledContainer.ContainerStatus.RUNNING);
            container.setAllocateTime(System.currentTimeMillis());
            recordContainerTaskStatus(container.getContainerId(), PooledContainer.ContainerStatus.RUNNING);
            log.info("Borrowed container: {}", container.getContainerId());
        } else {
            log.warn("Failed to borrow container within {} ms. Pool size: {}", timeoutMs, idlePool.size());
        }
        return container;
    }

    public void returnContainer(PooledContainer container) {
        if (container == null) return;
        
        container.setStatus(PooledContainer.ContainerStatus.DEAD);
        recordContainerTaskStatus(container.getContainerId(), PooledContainer.ContainerStatus.DEAD);
        
        log.info("Destroying returned container: {}", container.getContainerId());
        new Thread(() -> {
            try {
                containerManager.stopAndRemoveContainer(container.getContainerId());
                cleanHostWorkDir(new File(container.getWorkDirHostPath()));
            } finally {
                totalContainers.decrementAndGet();
                replenishAsync();
            }
        }).start();
    }

    private void cleanHostWorkDir(File dir) {
        if (dir.exists()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        cleanHostWorkDir(file);
                    } else {
                        file.delete();
                    }
                }
            }
            dir.delete();
        }
    }

    private void recordContainerTaskStatus(String containerId, PooledContainer.ContainerStatus status) {
        try {
            ContainerTaskEntity entity = containerTaskMapper.selectById(containerId);
            if (entity == null) {
                entity = new ContainerTaskEntity();
                entity.setContainerId(containerId);
                entity.setStatus(status.name());
                entity.setAllocateTime(LocalDateTime.now());
                containerTaskMapper.insert(entity);
            } else {
                entity.setStatus(status.name());
                if (status == PooledContainer.ContainerStatus.RUNNING) {
                    entity.setAllocateTime(LocalDateTime.now());
                }
                containerTaskMapper.updateById(entity);
            }
        } catch (Exception e) {
            log.warn("Failed to record container task status for {}", containerId, e);
        }
    }
}

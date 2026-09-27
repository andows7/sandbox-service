package com.huixue.sandbox.infrastructure.pool;

import com.huixue.sandbox.common.enums.LanguageType;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Map;

@Slf4j
@Component
public class ContainerPool {

    private final DockerContainerManager containerManager;
    private final DockerProperties dockerProperties;
    private final ContainerTaskMapper containerTaskMapper;
    
    private final Map<LanguageType, BlockingQueue<PooledContainer>> idlePools = new ConcurrentHashMap<>();
    private final Map<LanguageType, AtomicInteger> totalContainers = new ConcurrentHashMap<>();

    public ContainerPool(DockerContainerManager containerManager, DockerProperties dockerProperties, ContainerTaskMapper containerTaskMapper) {
        this.containerManager = containerManager;
        this.dockerProperties = dockerProperties;
        this.containerTaskMapper = containerTaskMapper;
    }

    @PostConstruct
    public void init() {
        int maxSize = dockerProperties.getPool().getMaxSize();
        
        for (LanguageType lang : LanguageType.values()) {
            idlePools.put(lang, new ArrayBlockingQueue<>(maxSize));
            totalContainers.put(lang, new AtomicInteger(0));
        }
        
        containerTaskMapper.delete(null);

        int coreSize = dockerProperties.getPool().getCoreSize();
        log.info("Initializing container pool. Prewarming {} containers per language...", coreSize);
        for (LanguageType lang : LanguageType.values()) {
            for (int i = 0; i < coreSize; i++) {
                replenishAsync(lang);
            }
        }
    }

    public void replenishAsync(LanguageType lang) {
        if (totalContainers.get(lang).get() >= dockerProperties.getPool().getMaxSize()) {
            return;
        }
        totalContainers.get(lang).incrementAndGet();
        new Thread(() -> {
            try {
                PooledContainer container = createNewContainer(lang);
                idlePools.get(lang).offer(container);
                log.info("Replenished {} container: {}. Total {} containers: {}", lang, container.getContainerId(), lang, totalContainers.get(lang).get());
                recordContainerTaskStatus(container.getContainerId(), PooledContainer.ContainerStatus.IDLE);
            } catch (Exception e) {
                totalContainers.get(lang).decrementAndGet();
                log.error("Failed to replenish {} container", lang, e);
            }
        }).start();
    }

    private PooledContainer createNewContainer(LanguageType lang) {
        String uuid = UUID.randomUUID().toString();
        String hostWorkDir = System.getProperty("user.dir") + File.separator + "sandbox_workdirs" + File.separator + uuid;
        File dir = new File(hostWorkDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        String image;
        String buildDirName;
        switch (lang) {
            case JAVA:
                image = "sandbox-java:1.0";
                buildDirName = "java";
                break;
            case CPP:
                image = "sandbox-cpp:1.0";
                buildDirName = "cpp";
                break;
            case PYTHON:
                image = "sandbox-python:1.0";
                buildDirName = "python";
                break;
            default:
                image = "ubuntu:22.04";
                buildDirName = "";
        }

        String buildDirHostPath = "";
        if (!buildDirName.isEmpty()) {
            buildDirHostPath = System.getProperty("user.dir") + File.separator + "docker-env" + File.separator + buildDirName;
        }

        DockerContainerSpec spec = DockerContainerSpec.builder()
                .image(image)
                .buildDirHostPath(buildDirHostPath)
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
        pc.setLanguage(lang);
        return pc;
    }

    public PooledContainer borrowContainer(LanguageType lang, long timeoutMs) throws InterruptedException {
        PooledContainer container = idlePools.get(lang).poll(timeoutMs, TimeUnit.MILLISECONDS);
        if (container != null) {
            container.setStatus(PooledContainer.ContainerStatus.RUNNING);
            container.setAllocateTime(System.currentTimeMillis());
            recordContainerTaskStatus(container.getContainerId(), PooledContainer.ContainerStatus.RUNNING);
            log.info("Borrowed {} container: {}", lang, container.getContainerId());
        } else {
            log.warn("Failed to borrow {} container within {} ms. Pool size: {}", lang, timeoutMs, idlePools.get(lang).size());
        }
        return container;
    }

    public void returnContainer(PooledContainer container) {
        if (container == null) return;
        
        LanguageType lang = container.getLanguage();
        container.setStatus(PooledContainer.ContainerStatus.DEAD);
        recordContainerTaskStatus(container.getContainerId(), PooledContainer.ContainerStatus.DEAD);
        
        log.info("Destroying returned container: {}", container.getContainerId());
        new Thread(() -> {
            try {
                containerManager.stopAndRemoveContainer(container.getContainerId());
                cleanHostWorkDir(new File(container.getWorkDirHostPath()));
            } finally {
                totalContainers.get(lang).decrementAndGet();
                replenishAsync(lang);
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

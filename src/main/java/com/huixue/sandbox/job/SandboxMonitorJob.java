package com.huixue.sandbox.job;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.huixue.sandbox.infrastructure.docker.DockerContainerManager;
import com.huixue.sandbox.infrastructure.persistence.entity.ContainerTaskEntity;
import com.huixue.sandbox.infrastructure.persistence.entity.ExecutionAuditEntity;
import com.huixue.sandbox.infrastructure.persistence.mapper.ContainerTaskMapper;
import com.huixue.sandbox.infrastructure.persistence.mapper.ExecutionAuditMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
public class SandboxMonitorJob {

    private final ExecutionAuditMapper executionAuditMapper;
    private final ContainerTaskMapper containerTaskMapper;
    private final DockerContainerManager dockerContainerManager;

    public SandboxMonitorJob(
            ExecutionAuditMapper executionAuditMapper, 
            ContainerTaskMapper containerTaskMapper,
            DockerContainerManager dockerContainerManager) {
        this.executionAuditMapper = executionAuditMapper;
        this.containerTaskMapper = containerTaskMapper;
        this.dockerContainerManager = dockerContainerManager;
    }

    // Run every 1 hour
    @Scheduled(cron = "0 0 * * * ?")
    public void monitorAndClean() {
        log.info("Starting scheduled task: Clean legacy containers and audit data...");
        
        // 1. Clean audit data older than 24 hours
        try {
            LocalDateTime auditThreshold = LocalDateTime.now().minusHours(24);
            QueryWrapper<ExecutionAuditEntity> query = new QueryWrapper<>();
            query.lt("create_time", auditThreshold);
            
            int deletedCount = executionAuditMapper.delete(query);
            log.info("Cleaned {} legacy audit records older than 24 hours.", deletedCount);
        } catch (Exception e) {
            log.error("Failed to clean audit data", e);
        }

        // 2. Clean stuck/idle containers older than 1 hour (ghost containers)
        try {
            LocalDateTime containerThreshold = LocalDateTime.now().minusHours(1);
            QueryWrapper<ContainerTaskEntity> containerQuery = new QueryWrapper<>();
            containerQuery.lt("allocate_time", containerThreshold);

            List<ContainerTaskEntity> ghostContainers = containerTaskMapper.selectList(containerQuery);
            for (ContainerTaskEntity ghost : ghostContainers) {
                log.warn("Found ghost container {}, killing it...", ghost.getContainerId());
                dockerContainerManager.stopAndRemoveContainer(ghost.getContainerId());
                containerTaskMapper.deleteById(ghost.getContainerId());
            }
            log.info("Cleaned {} ghost containers.", ghostContainers.size());
        } catch (Exception e) {
            log.error("Failed to clean ghost containers", e);
        }
    }
}

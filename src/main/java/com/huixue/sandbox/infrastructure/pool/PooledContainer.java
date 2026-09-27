package com.huixue.sandbox.infrastructure.pool;

import lombok.Data;

@Data
public class PooledContainer {
    private String containerId;
    private String workDirHostPath;
    private String workDirContainerPath;
    private volatile ContainerStatus status;
    private long allocateTime;

    public enum ContainerStatus {
        IDLE,
        RUNNING,
        DEAD
    }
}

package com.huixue.sandbox.infrastructure.pool;

import com.huixue.sandbox.common.enums.LanguageType;
import lombok.Data;

@Data
public class PooledContainer {
    private String containerId;
    private String workDirHostPath;
    private String workDirContainerPath;
    private volatile ContainerStatus status;
    private long allocateTime;
    private LanguageType language;

    public enum ContainerStatus {
        IDLE,
        RUNNING,
        DEAD
    }
}
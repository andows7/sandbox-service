package com.huixue.sandbox.infrastructure.docker;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DockerContainerSpec {
    private String image;
    private String containerName;
    private String buildDirHostPath;
    private String workDirHostPath;
    private String workDirContainerPath;
    private int memoryLimitMb;
    private int cpuCount;
    private int pidLimit;
}

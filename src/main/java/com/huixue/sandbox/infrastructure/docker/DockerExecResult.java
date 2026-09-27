package com.huixue.sandbox.infrastructure.docker;

import lombok.Data;

@Data
public class DockerExecResult {
    private long costTimeMs;
    private boolean timeout;
    private int exitCode;
}


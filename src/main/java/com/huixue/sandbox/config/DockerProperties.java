package com.huixue.sandbox.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "sandbox.docker")
public class DockerProperties {
    private String host = "unix:///var/run/docker.sock";
    private int cpuCount = 1;
    private int memoryMb = 256;
    private int pidLimit = 64;
    private long maxExecutionTimeMs = 10000;
    private Pool pool = new Pool();

    @Data
    public static class Pool {
        private int coreSize = 10;
        private int maxSize = 50;
    }
}

package com.huixue.sandbox.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "sandbox.docker")
public class DockerProperties {
    private String host;
    private int cpuCount = 1;
    private int memoryMb = 256;
    private int pidLimit = 64;
    private long maxExecutionTimeMs = 10000;
    private String workDir = System.getProperty("user.dir") + "/sandbox_workdirs";
    // Docker-out-of-Docker may use a different path on the Docker host.
    private String hostWorkDir;
    private Pool pool = new Pool();

    @Data
    public static class Pool {
        private int coreSize = 0;
        private int maxSize = 2;
        private int maxTotal = 4;
    }
}

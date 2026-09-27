package com.huixue.sandbox.infrastructure.docker;

import com.github.dockerjava.api.DockerClient;
import com.huixue.sandbox.config.DockerProperties;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DockerContainerManagerTest {

    @Test
    @Disabled("Requires Docker environment")
    void testCreateContainer() {
        DockerProperties properties = new DockerProperties();
        DockerClientFactory factory = new DockerClientFactory(properties);
        DockerClient dockerClient = factory.dockerClient();
        
        DockerContainerManager manager = new DockerContainerManager(dockerClient);
        

        DockerContainerSpec spec = DockerContainerSpec.builder()
                .image("openjdk:17-jdk-slim")
                .cpuCount(1)
                .memoryLimitMb(256)
                .pidLimit(64)
                .workDirHostPath(System.getProperty("java.io.tmpdir"))
                .workDirContainerPath("/sandbox/workdir")
                .build();

        String containerId = manager.createContainer(spec);
        assertNotNull(containerId);
        
        manager.startContainer(containerId);
        manager.stopAndRemoveContainer(containerId);
    }
}

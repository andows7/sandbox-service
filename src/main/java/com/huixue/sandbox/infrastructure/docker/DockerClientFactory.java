package com.huixue.sandbox.infrastructure.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.transport.DockerHttpClient;
import com.huixue.sandbox.config.DockerProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Duration;

@Slf4j
@Configuration
public class DockerClientFactory {

    private final DockerProperties dockerProperties;

    public DockerClientFactory(DockerProperties dockerProperties) {
        this.dockerProperties = dockerProperties;
    }

    @Bean
    public DockerClient dockerClient() {
        DefaultDockerClientConfig.Builder configBuilder = DefaultDockerClientConfig.createDefaultConfigBuilder();

        String configuredHost = dockerProperties.getHost();
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");

        if (configuredHost != null && !configuredHost.isBlank()) {
            if (isWindows && configuredHost.startsWith("unix://")) {
                log.warn("Unix socket '{}' is not supported on Windows. Falling back to default Windows Docker host.", configuredHost);
            } else {
                configBuilder.withDockerHost(configuredHost);
            }
        }

        DockerClientConfig config = configBuilder.build();
        log.info("Initialized Docker client with host: {}", config.getDockerHost());

        DockerHttpClient httpClient = new com.github.dockerjava.zerodep.ZerodepDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .maxConnections(100)
                .connectionTimeout(Duration.ofSeconds(30))
                .responseTimeout(Duration.ofSeconds(45))
                .build();

        return DockerClientImpl.getInstance(config, httpClient);
    }
}

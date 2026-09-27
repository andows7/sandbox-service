package com.huixue.sandbox.infrastructure.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Capability;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Volume;
import com.huixue.sandbox.config.DockerProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.List;

@Slf4j
@Component
public class DockerContainerManager {

    private final DockerClient dockerClient;
    private final DockerProperties dockerProperties;

    public DockerContainerManager(DockerClient dockerClient, DockerProperties dockerProperties) {
        this.dockerClient = dockerClient;
        this.dockerProperties = dockerProperties;
    }

    @PostConstruct
    public void init() {
        cleanZombieContainers();
    }

    public void cleanZombieContainers() {
        log.info("Cleaning up zombie sandbox containers...");
        try {
            List<Container> containers = dockerClient.listContainersCmd().withShowAll(true).exec();
            for (Container container : containers) {
                // Remove containers created by sandbox
                if (container.getNames() != null && container.getNames().length > 0) {
                    for (String name : container.getNames()) {
                        if (name.startsWith("/sandbox-exec-")) {
                            log.info("Removing zombie container: {}", name);
                            dockerClient.removeContainerCmd(container.getId())
                                    .withForce(true)
                                    .exec();
                            break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to clean zombie containers", e);
        }
    }

    public String createContainer(DockerContainerSpec spec) {
        try {
            return doCreateContainer(spec);
        } catch (NotFoundException e) {
            log.warn("Image {} not found locally. Attempting to pull...", spec.getImage());
            try {
                dockerClient.pullImageCmd(spec.getImage())
                        .exec(new PullImageResultCallback())
                        .awaitCompletion();
                log.info("Successfully pulled image: {}", spec.getImage());
                return doCreateContainer(spec);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while pulling image " + spec.getImage(), ie);
            }
        }
    }

    private String doCreateContainer(DockerContainerSpec spec) {
        String containerName = "sandbox-exec-" + System.currentTimeMillis() + "-" + (int)(Math.random() * 1000);

        HostConfig hostConfig = HostConfig.newHostConfig()
                .withMemory(spec.getMemoryLimitMb() * 1024 * 1024L)
                .withMemorySwap(spec.getMemoryLimitMb() * 1024 * 1024L) // Disable swap by setting it equal to memory
                .withCpuCount((long) spec.getCpuCount())
                .withPidsLimit((long) spec.getPidLimit())
                .withNetworkMode("none") // No network
                .withCapDrop(Capability.ALL) // Drop all capabilities
                .withBinds(new Bind(spec.getWorkDirHostPath(), new Volume(spec.getWorkDirContainerPath())))
                .withReadonlyRootfs(true); // Optional: readonly root fs

        CreateContainerResponse containerResponse = dockerClient.createContainerCmd(spec.getImage())
                .withName(containerName)
                .withHostConfig(hostConfig)
                .withNetworkDisabled(true)
                .withAttachStdin(true)
                .withAttachStdout(true)
                .withAttachStderr(true)
                .withTty(true)
                .withUser("1000:1000") // Run as non-root
                .withWorkingDir(spec.getWorkDirContainerPath())
                // Ensure it stays open for interaction
                .withStdinOpen(true)
                .withCmd("/bin/sh")
                .exec();

        String containerId = containerResponse.getId();
        log.info("Created container: {} (ID: {})", containerName, containerId);
        return containerId;
    }

    public void startContainer(String containerId) {
        dockerClient.startContainerCmd(containerId).exec();
        log.info("Started container: {}", containerId);
    }

    public void stopAndRemoveContainer(String containerId) {
        try {
            dockerClient.removeContainerCmd(containerId)
                    .withForce(true) // force kills and removes
                    .exec();
            log.info("Removed container: {}", containerId);
        } catch (Exception e) {
            log.error("Failed to remove container: {}", containerId, e);
        }
    }
}

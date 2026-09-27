package com.huixue.sandbox.infrastructure.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.command.BuildImageResultCallback;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.Collections;

@Slf4j
@Component
public class DockerContainerManager {

    private final DockerClient dockerClient;

    public DockerContainerManager(DockerClient dockerClient) {
        this.dockerClient = dockerClient;
    }

    public String createContainer(DockerContainerSpec spec) {
        try {
            dockerClient.inspectImageCmd(spec.getImage()).exec();
        } catch (NotFoundException e) {
            synchronized (spec.getImage().intern()) {
                try {
                    dockerClient.inspectImageCmd(spec.getImage()).exec();
                } catch (NotFoundException e2) {
                    if (StringUtils.isNotBlank(spec.getBuildDirHostPath())) {
                        File buildDir = new File(spec.getBuildDirHostPath());
                        if (buildDir.exists() && buildDir.isDirectory()) {
                            log.warn("Image {} not found locally. Building from {}...", spec.getImage(), spec.getBuildDirHostPath());
                            try {
                                String imageId = dockerClient.buildImageCmd(buildDir)
                                        .withTags(Collections.singleton(spec.getImage()))
                                        .exec(new BuildImageResultCallback())
                                        .awaitImageId();
                                log.info("Successfully built image {}: {}", spec.getImage(), imageId);
                            } catch (Exception ex) {
                                log.error("Failed to build image {}", spec.getImage(), ex);
                                throw new RuntimeException(ex);
                            }
                        } else {
                            log.error("Build directory {} does not exist!", spec.getBuildDirHostPath());
                            throw new RuntimeException("Build directory not found: " + spec.getBuildDirHostPath());
                        }
                    } else {
                        log.warn("Image {} not found locally. Attempting to pull...", spec.getImage());
                        try {
                            dockerClient.pullImageCmd(spec.getImage())
                                    .exec(new PullImageResultCallback())
                                    .awaitCompletion();
                            log.info("Successfully pulled image: {}", spec.getImage());
                        } catch (Exception ex) {
                            log.error("Failed to pull image {}", spec.getImage(), ex);
                            throw new RuntimeException(ex);
                        }
                    }
                }
            }
        }

        HostConfig hostConfig = HostConfig.newHostConfig()
                .withMemory(spec.getMemoryLimitMb() * 1024L * 1024L)
                .withCpuCount((long) spec.getCpuCount())
                .withPidsLimit((long) spec.getPidLimit())
                .withNetworkMode("none")
                .withBinds(new Bind(spec.getWorkDirHostPath(), new Volume(spec.getWorkDirContainerPath())));

        CreateContainerResponse response = dockerClient.createContainerCmd(spec.getImage())
                .withHostConfig(hostConfig)
                .withWorkingDir(spec.getWorkDirContainerPath())
                .withNetworkDisabled(true)
                .withAttachStdin(true)
                .withAttachStdout(true)
                .withAttachStderr(true)
                .withTty(true)
                .exec();

        String containerId = response.getId();
        log.info("Created container: {}", containerId);
        return containerId;
    }

    public void startContainer(String containerId) {
        dockerClient.startContainerCmd(containerId).exec();
        log.info("Started container: {}", containerId);
    }

    public void stopAndRemoveContainer(String containerId) {
        try {
            InspectContainerResponse inspect = dockerClient.inspectContainerCmd(containerId).exec();
            if (Boolean.TRUE.equals(inspect.getState().getRunning())) {
                dockerClient.stopContainerCmd(containerId).exec();
                log.info("Stopped container: {}", containerId);
            }
            dockerClient.removeContainerCmd(containerId).withForce(true).exec();
            log.info("Removed container: {}", containerId);
        } catch (NotFoundException e) {
            log.warn("Container {} already removed or not found", containerId);
        } catch (Exception e) {
            log.error("Failed to stop and remove container {}", containerId, e);
        }
    }

    public DockerExecResult execInContainer(String containerId, String[] cmd, long timeoutMs) {
        DockerExecResult result = new DockerExecResult();
        long start = System.currentTimeMillis();
        
        try {
            String execId = dockerClient.execCreateCmd(containerId)
                    .withCmd(cmd)
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .exec()
                    .getId();

            boolean completed = dockerClient.execStartCmd(execId)
                    .exec(new ExecStartResultCallback())
                    .awaitCompletion(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
            
            result.setCostTimeMs(System.currentTimeMillis() - start);

            if (!completed) {
                result.setTimeout(true);
                return result;
            }
            
            result.setTimeout(false);
            result.setExitCode(dockerClient.inspectExecCmd(execId).exec().getExitCodeLong().intValue());
            
        } catch (Exception e) {
            log.error("Failed to exec in container {}", containerId, e);
            result.setExitCode(-1);
            result.setCostTimeMs(System.currentTimeMillis() - start);
        }
        return result;
    }
}

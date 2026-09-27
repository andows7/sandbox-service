package com.huixue.sandbox.strategy.cpp;

import com.huixue.sandbox.common.util.PathSanitizer;
import com.huixue.sandbox.domain.model.CompileResult;
import com.huixue.sandbox.domain.strategy.CompilerStrategy;
import com.huixue.sandbox.infrastructure.docker.DockerContainerManager;
import com.huixue.sandbox.infrastructure.docker.DockerExecResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;

@Slf4j
@Component("cppCompilerStrategy")
public class CppCompilerStrategy implements CompilerStrategy {

    private final DockerContainerManager dockerContainerManager;

    public CppCompilerStrategy(DockerContainerManager dockerContainerManager) {
        this.dockerContainerManager = dockerContainerManager;
    }

    @Override
    public CompileResult compile(String sourceCode, String workDirHostPath, String containerId) {
        CompileResult result = new CompileResult();
        File dir = new File(workDirHostPath);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        String fileName = "main.cpp";
        File sourceFile = new File(dir, fileName);
        try (FileWriter writer = new FileWriter(sourceFile)) {
            writer.write(sourceCode);
        } catch (IOException e) {
            log.error("Failed to write source file", e);
            result.setSuccess(false);
            result.setCompileError("Failed to write source file");
            return result;
        }

        try {
            String[] cmd = {"sh", "-c", "g++ main.cpp -o main 2> compile_err.txt"};
            DockerExecResult execResult = dockerContainerManager.execInContainer(containerId, cmd, 10000);

            if (execResult.isTimeout()) {
                result.setSuccess(false);
                result.setCompileError("Compile timeout");
                return result;
            }

            if (execResult.getExitCode() == 0) {
                result.setSuccess(true);
                result.setExecuteTarget("./main");
            } else {
                result.setSuccess(false);
                File errFile = new File(dir, "compile_err.txt");
                if (errFile.exists()) {
                    String error = Files.readString(errFile.toPath());
                    result.setCompileError(PathSanitizer.sanitize(error));
                } else {
                    result.setCompileError("Compile failed");
                }
            }
        } catch (Exception e) {
            log.error("Compile process failed", e);
            result.setSuccess(false);
            result.setCompileError("Compile process failed");
        }

        return result;
    }
}

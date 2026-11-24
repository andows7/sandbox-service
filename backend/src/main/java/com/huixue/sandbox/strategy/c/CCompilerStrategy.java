package com.huixue.sandbox.strategy.c;

import com.huixue.sandbox.common.util.BoundedFiles;
import com.huixue.sandbox.common.util.PathSanitizer;
import com.huixue.sandbox.domain.model.CompileResult;
import com.huixue.sandbox.domain.strategy.CompilerStrategy;
import com.huixue.sandbox.infrastructure.docker.DockerContainerManager;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Component("cCompilerStrategy")
public class CCompilerStrategy implements CompilerStrategy {
    private final DockerContainerManager manager;

    public CCompilerStrategy(DockerContainerManager manager) { this.manager = manager; }

    @Override
    public CompileResult compile(String code, String workDir, String containerId) {
        CompileResult result = new CompileResult();
        try {
            Path dir = Files.createDirectories(Path.of(workDir));
            Files.writeString(dir.resolve("main.c"), code, StandardCharsets.UTF_8);
            var exec = manager.execInContainer(containerId, new String[]{"sh", "-c",
                    "ulimit -f 2048; gcc -std=c17 -O2 -Wall -Wextra main.c -o main -lm 2> compile_err.txt"}, 10000);
            result.setSuccess(!exec.isTimeout() && exec.getExitCode() == 0);
            if (result.isSuccess()) result.setExecuteTarget("./main");
            else result.setCompileError(exec.isTimeout() ? "Compile timeout" : PathSanitizer.sanitize(
                    BoundedFiles.readUtf8(dir.resolve("compile_err.txt"), BoundedFiles.ERROR_BYTES)));
        } catch (Exception e) {
            result.setSuccess(false);
            result.setCompileError("Compile failed or diagnostic output limit exceeded");
        }
        return result;
    }
}

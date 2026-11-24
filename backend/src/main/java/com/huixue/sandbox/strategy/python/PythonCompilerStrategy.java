package com.huixue.sandbox.strategy.python;

import com.huixue.sandbox.domain.model.CompileResult;
import com.huixue.sandbox.domain.strategy.CompilerStrategy;
import com.huixue.sandbox.common.util.BoundedFiles;
import com.huixue.sandbox.common.util.PathSanitizer;
import com.huixue.sandbox.infrastructure.docker.DockerContainerManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@Component("pythonCompilerStrategy")
public class PythonCompilerStrategy implements CompilerStrategy {
    private final DockerContainerManager manager;

    public PythonCompilerStrategy(DockerContainerManager manager) {
        this.manager = manager;
    }

    @Override
    public CompileResult compile(String sourceCode, String workDirHostPath, String containerId) {
        CompileResult result = new CompileResult();
        try {
            Path dir = Files.createDirectories(Path.of(workDirHostPath));
            BoundedFiles.writeUtf8(dir.resolve("main.py"), sourceCode);
            // Compile to bytecode inside the sandbox without executing top-level user code.
            var exec = manager.execInContainer(containerId, new String[]{"sh", "-c",
                    "ulimit -f 2048; python3 -m py_compile main.py 2> compile_err.txt"}, 10000);
            if (exec.isTimeout()) {
                result.setSuccess(false);
                result.setCompileError("Python syntax check timeout");
            } else if (exec.getExitCode() != 0) {
                result.setSuccess(false);
                String diagnostic = PathSanitizer.sanitize(
                        BoundedFiles.readUtf8(dir.resolve("compile_err.txt"), BoundedFiles.ERROR_BYTES));
                result.setCompileError(diagnostic.isBlank() ? "Python syntax check failed" : diagnostic);
            } else {
                result.setSuccess(true);
                result.setExecuteTarget("main.py");
            }
        } catch (Exception e) {
            log.error("Python syntax check failed", e);
            result.setSuccess(false);
            result.setCompileError("Python syntax check failed or diagnostic output limit exceeded");
        }
        return result;
    }
}

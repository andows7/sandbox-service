package com.huixue.sandbox.strategy.python;

import com.huixue.sandbox.domain.model.CompileResult;
import com.huixue.sandbox.domain.strategy.CompilerStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

@Slf4j
@Component("pythonCompilerStrategy")
public class PythonCompilerStrategy implements CompilerStrategy {
    @Override
    public CompileResult compile(String sourceCode, String workDirHostPath, String containerId) {
        CompileResult result = new CompileResult();
        File dir = new File(workDirHostPath);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        String fileName = "main.py";
        File sourceFile = new File(dir, fileName);
        try (FileWriter writer = new FileWriter(sourceFile)) {
            writer.write(sourceCode);
        } catch (IOException e) {
            log.error("Failed to write source file", e);
            result.setSuccess(false);
            result.setCompileError("Failed to write source file");
            return result;
        }

        result.setSuccess(true);
        result.setExecuteTarget("main.py");
        return result;
    }
}
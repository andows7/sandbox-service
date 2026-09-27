package com.huixue.sandbox.strategy.java;

import com.huixue.sandbox.common.util.PathSanitizer;
import com.huixue.sandbox.domain.model.CompileResult;
import com.huixue.sandbox.domain.strategy.CompilerStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.BufferedReader;

@Slf4j
@Component("javaCompilerStrategy")
public class JavaCompilerStrategy implements CompilerStrategy {

    @Override
    public CompileResult compile(String sourceCode, String workDir) {
        CompileResult result = new CompileResult();
        File dir = new File(workDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        String fileName = "Main.java";
        File sourceFile = new File(dir, fileName);
        try (FileWriter writer = new FileWriter(sourceFile)) {
            writer.write(sourceCode);
        } catch (IOException e) {
            log.error("Failed to write source file", e);
            result.setSuccess(false);
            result.setCompileError("系统错误：无法写入源码文件");
            return result;
        }

        try {
            ProcessBuilder pb = new ProcessBuilder("javac", "-J-Dfile.encoding=UTF-8", "-encoding", "UTF-8", fileName);
            pb.directory(dir);
            Process process = pb.start();

            StringBuilder errorOutput = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream(), "UTF-8"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    errorOutput.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();
            if (exitCode == 0) {
                result.setSuccess(true);
                result.setExecuteTarget("Main");
            } else {
                result.setSuccess(false);
                result.setCompileError(PathSanitizer.sanitize(errorOutput.toString()));
            }
        } catch (Exception e) {
            log.error("Compile process failed", e);
            result.setSuccess(false);
            result.setCompileError("系统错误：编译进程异常");
        }

        return result;
    }
}

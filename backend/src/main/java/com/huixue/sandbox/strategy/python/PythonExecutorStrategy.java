package com.huixue.sandbox.strategy.python;

import com.huixue.sandbox.common.enums.JudgeStatus;
import com.huixue.sandbox.common.util.PathSanitizer;
import com.huixue.sandbox.common.util.BoundedFiles;
import com.huixue.sandbox.common.util.MemoryUsage;
import com.huixue.sandbox.domain.model.ExecutionResult;
import com.huixue.sandbox.domain.model.ResourceLimit;
import com.huixue.sandbox.domain.model.TestCase;
import com.huixue.sandbox.domain.model.TestCaseResult;
import com.huixue.sandbox.domain.strategy.ExecutorStrategy;
import com.huixue.sandbox.infrastructure.docker.DockerContainerManager;
import com.huixue.sandbox.infrastructure.docker.DockerExecResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component("pythonExecutorStrategy")
public class PythonExecutorStrategy implements ExecutorStrategy {

    private final DockerContainerManager dockerContainerManager;

    public PythonExecutorStrategy(DockerContainerManager dockerContainerManager) {
        this.dockerContainerManager = dockerContainerManager;
    }

    @Override
    public ExecutionResult execute(String executeTarget, String workDirHostPath, String containerId, List<TestCase> testCases, ResourceLimit limit) {
        ExecutionResult executionResult = new ExecutionResult();
        List<TestCaseResult> caseResults = new ArrayList<>();
        long maxTimeMs = 0;
        long maxMemoryMb = 0;

        File dir = new File(workDirHostPath);

        for (int i = 0; i < testCases.size(); i++) {
            TestCase tc = testCases.get(i);
            TestCaseResult caseResult = new TestCaseResult();
            
            try {
                File inputFile = new File(dir, "input_" + i + ".txt");
                BoundedFiles.writeUtf8(inputFile.toPath(), tc.getInput());

                String cmdStr = String.format("ulimit -f 2048; /usr/bin/time -f '%%M' -o usage_%d.txt python3 %s < input_%d.txt > output_%d.txt 2> error_%d.txt", i, executeTarget, i, i, i);
                String[] cmd = {"sh", "-c", cmdStr};

                DockerExecResult execResult = dockerContainerManager.execInContainer(containerId, cmd, limit.getTimeLimitMs());
                
                maxTimeMs = Math.max(maxTimeMs, execResult.getCostTimeMs());
                maxMemoryMb = Math.max(maxMemoryMb, MemoryUsage.readMb(dir.toPath().resolve("usage_" + i + ".txt")));

                if (execResult.isTimeout()) {
                    executionResult.setStatus(JudgeStatus.TLE);
                    caseResult.setPassed(false);
                    caseResults.add(caseResult);
                    break;
                }

                File errorFile = new File(dir, "error_" + i + ".txt");
                String errorStr = "";
                if (errorFile.exists()) {
                    errorStr = BoundedFiles.readUtf8(errorFile.toPath(), BoundedFiles.ERROR_BYTES).trim();
                }

                if (execResult.getExitCode() != 0) {
                    if (execResult.getExitCode() == 137 || errorStr.contains("MemoryError")) {
                        executionResult.setStatus(JudgeStatus.MLE);
                        executionResult.setRuntimeError("Memory Limit Exceeded");
                    } else {
                        executionResult.setStatus(JudgeStatus.RE);
                        executionResult.setRuntimeError(PathSanitizer.sanitize(errorStr));
                    }
                    caseResult.setPassed(false);
                    caseResult.setErrorLine(extractErrorLine(errorStr));
                    caseResults.add(caseResult);
                    break;
                }

                File outputFile = new File(dir, "output_" + i + ".txt");
                String actualOutput = "";
                if (outputFile.exists()) {
                    actualOutput = BoundedFiles.readUtf8(outputFile.toPath(), BoundedFiles.OUTPUT_BYTES).trim();
                }
                
                String expectedOutput = tc.getExpectedOutput() != null ? tc.getExpectedOutput().trim() : "";
                caseResult.setActualOutput(actualOutput);

                if (actualOutput.equals(expectedOutput)) {
                    caseResult.setPassed(true);
                } else {
                    caseResult.setPassed(false);
                    executionResult.setStatus(JudgeStatus.WA);
                    caseResults.add(caseResult);
                    break;
                }
                caseResults.add(caseResult);

            } catch (Exception e) {
                log.error("Execution process failed", e);
                executionResult.setStatus(JudgeStatus.RE);
                executionResult.setRuntimeError("Execution process failed");
                caseResult.setPassed(false);
                caseResults.add(caseResult);
                break;
            }
        }

        if (executionResult.getStatus() == null) {
            executionResult.setStatus(JudgeStatus.AC);
        }
        executionResult.setExecutionTimeMs(maxTimeMs);
        executionResult.setMemoryUsedMb(maxMemoryMb);
        executionResult.setTestCaseResults(caseResults);

        return executionResult;
    }
    
    private Integer extractErrorLine(String errorStr) {
        if (StringUtils.isBlank(errorStr)) {
            return null;
        }
        Pattern pattern = Pattern.compile("line (\\d+)");
        Matcher matcher = pattern.matcher(errorStr);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }
}

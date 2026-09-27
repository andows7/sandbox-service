package com.huixue.sandbox.strategy.java;

import com.huixue.sandbox.common.enums.JudgeStatus;
import com.huixue.sandbox.common.util.PathSanitizer;
import com.huixue.sandbox.domain.model.ExecutionResult;
import com.huixue.sandbox.domain.model.ResourceLimit;
import com.huixue.sandbox.domain.model.TestCase;
import com.huixue.sandbox.domain.model.TestCaseResult;
import com.huixue.sandbox.domain.strategy.ExecutorStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.apache.commons.lang3.StringUtils;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component("javaExecutorStrategy")
public class JavaExecutorStrategy implements ExecutorStrategy {

    @Override
    public ExecutionResult execute(String executeTarget, String workDir, List<TestCase> testCases, ResourceLimit limit) {
        ExecutionResult executionResult = new ExecutionResult();
        List<TestCaseResult> caseResults = new ArrayList<>();
        long maxTimeMs = 0;
        long maxMemoryMb = 0; // Local simulation cannot precisely measure memory.

        for (TestCase tc : testCases) {
            TestCaseResult caseResult = new TestCaseResult();
            try {
                ProcessBuilder pb = new ProcessBuilder("java", "-Xmx" + limit.getMemoryLimitMb() + "m", executeTarget);
                pb.directory(new File(workDir));
                
                long startTime = System.currentTimeMillis();
                Process process = pb.start();

                // STDIN
                if (StringUtils.isNotBlank(tc.getInput())) {
                    try (OutputStreamWriter writer = new OutputStreamWriter(process.getOutputStream(), "UTF-8")) {
                        writer.write(tc.getInput());
                        writer.flush();
                    }
                }

                // STDOUT
                StringBuilder output = new StringBuilder();
                Thread outThread = new Thread(() -> {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), "UTF-8"))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            output.append(line).append("\n");
                        }
                    } catch (IOException e) {
                        log.error("Error reading stdout", e);
                    }
                });
                outThread.start();

                // STDERR
                StringBuilder error = new StringBuilder();
                Thread errThread = new Thread(() -> {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream(), "UTF-8"))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            error.append(line).append("\n");
                        }
                    } catch (IOException e) {
                        log.error("Error reading stderr", e);
                    }
                });
                errThread.start();

                boolean finished = process.waitFor(limit.getTimeLimitMs(), TimeUnit.MILLISECONDS);
                long costTime = System.currentTimeMillis() - startTime;
                maxTimeMs = Math.max(maxTimeMs, costTime);

                if (!finished) {
                    process.destroyForcibly();
                }

                outThread.join();
                errThread.join();

                if (!finished) {
                    executionResult.setStatus(JudgeStatus.TLE);
                    caseResult.setPassed(false);
                    caseResults.add(caseResult);
                    break;
                }

                int exitValue = process.exitValue();
                if (exitValue != 0) {
                    executionResult.setStatus(JudgeStatus.RE);
                    executionResult.setRuntimeError(PathSanitizer.sanitize(error.toString()));
                    caseResult.setPassed(false);
                    caseResult.setErrorLine(extractErrorLine(error.toString()));
                    caseResults.add(caseResult);
                    break; // Stop executing further cases
                }

                // Compare output
                String actualOutput = output.toString().trim();
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
                executionResult.setRuntimeError("系统错误：运行进程异常");
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
        Pattern pattern = Pattern.compile("Main\\.java:(\\d+)");
        Matcher matcher = pattern.matcher(errorStr);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }
}

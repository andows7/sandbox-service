package com.huixue.sandbox.domain.model;

import lombok.Data;
import java.util.List;
import com.huixue.sandbox.common.enums.JudgeStatus;

@Data
public class ExecutionResult {
    private JudgeStatus status;
    private long executionTimeMs;
    private long memoryUsedMb;
    private List<TestCaseResult> testCaseResults;
    private String runtimeError;
}

package com.huixue.sandbox.api.dto;

import com.huixue.sandbox.common.enums.JudgeStatus;
import lombok.Data;
import java.util.List;

@Data
public class SandboxExecuteResponse {
    private JudgeStatus status;
    private String compileError;
    private long executionTimeMs;
    private long memoryUsedMb;
    private List<TestCaseResultDto> testCaseResults;
}

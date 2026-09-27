package com.huixue.sandbox.domain.strategy;

import com.huixue.sandbox.domain.model.ExecutionResult;
import com.huixue.sandbox.domain.model.ResourceLimit;
import com.huixue.sandbox.domain.model.TestCase;
import java.util.List;

public interface ExecutorStrategy {
    ExecutionResult execute(String executeTarget, String workDirHostPath, String containerId, List<TestCase> testCases, ResourceLimit limit);
}

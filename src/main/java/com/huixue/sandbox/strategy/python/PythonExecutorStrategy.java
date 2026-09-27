package com.huixue.sandbox.strategy.python;

import com.huixue.sandbox.common.enums.JudgeStatus;
import com.huixue.sandbox.domain.model.ExecutionResult;
import com.huixue.sandbox.domain.model.ResourceLimit;
import com.huixue.sandbox.domain.model.TestCase;
import com.huixue.sandbox.domain.strategy.ExecutorStrategy;
import org.springframework.stereotype.Component;
import java.util.List;

@Component("pythonExecutorStrategy")
public class PythonExecutorStrategy implements ExecutorStrategy {
    @Override
    public ExecutionResult execute(String executeTarget, String workDir, List<TestCase> testCases, ResourceLimit limit) {
        ExecutionResult result = new ExecutionResult();
        result.setStatus(JudgeStatus.RE);
        result.setRuntimeError("Python executor not fully implemented yet");
        return result;
    }
}

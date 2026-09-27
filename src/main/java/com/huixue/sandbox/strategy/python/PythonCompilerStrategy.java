package com.huixue.sandbox.strategy.python;

import com.huixue.sandbox.domain.model.CompileResult;
import com.huixue.sandbox.domain.strategy.CompilerStrategy;
import org.springframework.stereotype.Component;

@Component("pythonCompilerStrategy")
public class PythonCompilerStrategy implements CompilerStrategy {
    @Override
    public CompileResult compile(String sourceCode, String workDir) {
        CompileResult result = new CompileResult();
        // Python doesn't need a strict compile step for our sandbox, just write the file
        result.setSuccess(true);
        result.setExecuteTarget("main.py");
        return result;
    }
}

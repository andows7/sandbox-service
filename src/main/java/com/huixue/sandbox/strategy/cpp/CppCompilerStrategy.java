package com.huixue.sandbox.strategy.cpp;

import com.huixue.sandbox.domain.model.CompileResult;
import com.huixue.sandbox.domain.strategy.CompilerStrategy;
import org.springframework.stereotype.Component;

@Component("cppCompilerStrategy")
public class CppCompilerStrategy implements CompilerStrategy {
    @Override
    public CompileResult compile(String sourceCode, String workDir) {
        CompileResult result = new CompileResult();
        result.setSuccess(false);
        result.setCompileError("CPP compiler not fully implemented yet");
        return result;
    }
}

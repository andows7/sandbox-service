package com.huixue.sandbox.domain.strategy;

import com.huixue.sandbox.domain.model.CompileResult;

public interface CompilerStrategy {
    CompileResult compile(String sourceCode, String workDir);
}

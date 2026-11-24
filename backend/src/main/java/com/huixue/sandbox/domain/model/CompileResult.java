package com.huixue.sandbox.domain.model;

import lombok.Data;

@Data
public class CompileResult {
    private boolean success;
    private String compileError;
    private String executeTarget; // e.g. Main (class name) or Main.exe
}

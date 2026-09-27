package com.huixue.sandbox.domain.model;

import lombok.Data;

@Data
public class TestCaseResult {
    private boolean passed;
    private String actualOutput;
    private Integer errorLine;
}

package com.huixue.sandbox.api.dto;

import lombok.Data;

@Data
public class TestCaseResultDto {
    private boolean passed;
    private String actualOutput;
    private Integer errorLine;
}

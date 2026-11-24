package com.huixue.sandbox.api.dto;

import lombok.Data;
import jakarta.validation.constraints.Size;

@Data
public class TestCaseDto {
    @Size(max = 16384, message = "单个输入不能超过 16384 字符")
    private String input;
    @Size(max = 65536, message = "单个预期输出不能超过 65536 字符")
    private String expectedOutput;
}

package com.huixue.sandbox.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.List;

@Data
public class SandboxExecuteRequest {

    @NotBlank(message = "submitId 不能为空")
    @Size(max = 64, message = "submitId 长度不能超过64")
    private String submitId;

    @NotBlank(message = "language 不能为空")
    private String language; // JAVA, CPP, PYTHON

    @NotBlank(message = "code 不能为空")
    private String code;

    private List<TestCaseDto> testCases;

    @Min(value = 100, message = "时间限制不能低于 100ms")
    @Max(value = 10000, message = "时间限制不能超过 10000ms")
    private Integer timeLimitMs = 10000;

    @Min(value = 16, message = "内存限制不能低于 16MB")
    @Max(value = 256, message = "内存限制不能超过 256MB")
    private Integer memoryLimitMb = 256;
}

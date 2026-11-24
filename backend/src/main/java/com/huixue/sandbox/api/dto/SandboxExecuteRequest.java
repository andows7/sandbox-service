package com.huixue.sandbox.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import lombok.Data;
import java.util.List;

@Data
public class SandboxExecuteRequest {

    @NotBlank(message = "submitId 不能为空")
    @Size(max = 64, message = "submitId 长度不能超过64")
    private String submitId;

    @NotBlank(message = "language 不能为空")
    private String language; // JAVA, C, CPP, PYTHON

    @NotBlank(message = "code 不能为空")
    @Size(max = 65536, message = "代码不能超过 65536 字符")
    private String code;

    @NotNull(message = "testCases 不能为空")
    @Size(min = 1, max = 50, message = "测试用例数量必须为 1 至 50")
    @Valid
    private List<@NotNull TestCaseDto> testCases;

    @Min(value = 100, message = "时间限制不能低于 100ms")
    @NotNull(message = "timeLimitMs 不能为空")
    @Max(value = 10000, message = "时间限制不能超过 10000ms")
    private Integer timeLimitMs = 10000;

    @Min(value = 16, message = "内存限制不能低于 16MB")
    @NotNull(message = "memoryLimitMb 不能为空")
    @Max(value = 256, message = "内存限制不能超过 256MB")
    private Integer memoryLimitMb = 256;
}

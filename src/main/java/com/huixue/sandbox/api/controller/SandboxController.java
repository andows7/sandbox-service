package com.huixue.sandbox.api.controller;

import com.huixue.sandbox.api.dto.ApiResponse;
import com.huixue.sandbox.api.dto.SandboxExecuteRequest;
import com.huixue.sandbox.api.dto.SandboxExecuteResponse;
import com.huixue.sandbox.domain.service.SandboxExecutionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping
@Tag(name = "沙箱服务接口", description = "供 API Gateway 内部路由使用的判题沙箱接口")
public class SandboxController {

    private final SandboxExecutionService executionService;

    public SandboxController(SandboxExecutionService executionService) {
        this.executionService = executionService;
    }

    @PostMapping({"/v1/api/sandbox/execute", "/api/sandbox/execute"})
    @Operation(summary = "提交代码执行评测", description = "接收代码、用例及资源限制，返回执行状态")
    public ApiResponse<SandboxExecuteResponse> executeCode(@Validated @RequestBody SandboxExecuteRequest request) {
        log.info("Received execution request for submitId: {}", request.getSubmitId());
        SandboxExecuteResponse response = executionService.execute(request);
        return ApiResponse.success(response);
    }
}

package com.huixue.sandbox.domain.service;

import com.huixue.sandbox.api.dto.SandboxExecuteRequest;
import com.huixue.sandbox.api.dto.SandboxExecuteResponse;
import com.huixue.sandbox.api.dto.TestCaseResultDto;
import com.huixue.sandbox.common.enums.JudgeStatus;
import com.huixue.sandbox.common.enums.LanguageType;
import com.huixue.sandbox.common.exception.BizException;
import com.huixue.sandbox.common.util.TraceIdUtils;
import com.huixue.sandbox.domain.model.CompileResult;
import com.huixue.sandbox.domain.model.ExecutionResult;
import com.huixue.sandbox.domain.model.ResourceLimit;
import com.huixue.sandbox.domain.model.TestCase;
import com.huixue.sandbox.domain.strategy.CompilerStrategy;
import com.huixue.sandbox.domain.strategy.ExecutorStrategy;
import com.huixue.sandbox.infrastructure.persistence.entity.ExecutionAuditEntity;
import com.huixue.sandbox.infrastructure.persistence.mapper.ExecutionAuditMapper;
import com.huixue.sandbox.infrastructure.pool.ContainerPool;
import com.huixue.sandbox.infrastructure.pool.PooledContainer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SandboxExecutionService {

    private final ContainerPool containerPool;
    private final ExecutionAuditMapper executionAuditMapper;
    private final CompilerStrategy javaCompilerStrategy;
    private final ExecutorStrategy javaExecutorStrategy;
    private final CompilerStrategy cppCompilerStrategy;
    private final ExecutorStrategy cppExecutorStrategy;
    private final CompilerStrategy pythonCompilerStrategy;
    private final ExecutorStrategy pythonExecutorStrategy;

    public SandboxExecutionService(
            ContainerPool containerPool,
            ExecutionAuditMapper executionAuditMapper,
            @Qualifier("javaCompilerStrategy") CompilerStrategy javaCompilerStrategy,
            @Qualifier("javaExecutorStrategy") ExecutorStrategy javaExecutorStrategy,
            @Qualifier("cppCompilerStrategy") CompilerStrategy cppCompilerStrategy,
            @Qualifier("cppExecutorStrategy") ExecutorStrategy cppExecutorStrategy,
            @Qualifier("pythonCompilerStrategy") CompilerStrategy pythonCompilerStrategy,
            @Qualifier("pythonExecutorStrategy") ExecutorStrategy pythonExecutorStrategy) {
        this.containerPool = containerPool;
        this.executionAuditMapper = executionAuditMapper;
        this.javaCompilerStrategy = javaCompilerStrategy;
        this.javaExecutorStrategy = javaExecutorStrategy;
        this.cppCompilerStrategy = cppCompilerStrategy;
        this.cppExecutorStrategy = cppExecutorStrategy;
        this.pythonCompilerStrategy = pythonCompilerStrategy;
        this.pythonExecutorStrategy = pythonExecutorStrategy;
    }

    public SandboxExecuteResponse execute(SandboxExecuteRequest request) {
        log.info("Starting execution for submitId: {}", request.getSubmitId());

        LanguageType lang;
        try {
            lang = LanguageType.valueOf(request.getLanguage().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BizException(400, "不支持的语言类型: " + request.getLanguage());
        }

        CompilerStrategy compiler;
        ExecutorStrategy executor;
        switch (lang) {
            case JAVA:
                compiler = javaCompilerStrategy;
                executor = javaExecutorStrategy;
                break;
            case CPP:
                compiler = cppCompilerStrategy;
                executor = cppExecutorStrategy;
                break;
            case PYTHON:
                compiler = pythonCompilerStrategy;
                executor = pythonExecutorStrategy;
                break;
            default:
                throw new BizException(400, "不支持的语言类型: " + lang);
        }

        PooledContainer container = null;
        try {
            container = containerPool.borrowContainer(lang, 5000);
            if (container == null) {
                throw new BizException(503, "评测服务繁忙，请稍后重试");
            }

            CompileResult compileResult = compiler.compile(request.getCode(), container.getWorkDirHostPath(), container.getContainerId());
            
            SandboxExecuteResponse response = new SandboxExecuteResponse();
            if (!compileResult.isSuccess()) {
                response.setStatus(JudgeStatus.CE);
                response.setCompileError(compileResult.getCompileError());
                recordAudit(request, container.getContainerId(), JudgeStatus.CE, 0);
                return response;
            }

            List<TestCase> testCases = request.getTestCases().stream().map(dto -> {
                TestCase tc = new TestCase();
                tc.setInput(dto.getInput());
                tc.setExpectedOutput(dto.getExpectedOutput());
                return tc;
            }).collect(Collectors.toList());

            ResourceLimit limit = new ResourceLimit(request.getTimeLimitMs(), request.getMemoryLimitMb());
            ExecutionResult executionResult = executor.execute(compileResult.getExecuteTarget(), container.getWorkDirHostPath(), container.getContainerId(), testCases, limit);

            response.setStatus(executionResult.getStatus());
            response.setExecutionTimeMs(executionResult.getExecutionTimeMs());
            response.setMemoryUsedMb(executionResult.getMemoryUsedMb());
            if (executionResult.getTestCaseResults() != null) {
                response.setTestCaseResults(executionResult.getTestCaseResults().stream().map(tr -> {
                    TestCaseResultDto dto = new TestCaseResultDto();
                    dto.setPassed(tr.isPassed());
                    dto.setActualOutput(tr.getActualOutput());
                    dto.setErrorLine(tr.getErrorLine());
                    return dto;
                }).collect(Collectors.toList()));
            }

            if (response.getStatus() == JudgeStatus.RE && executionResult.getRuntimeError() != null) {
                response.setCompileError(executionResult.getRuntimeError());
            }

            recordAudit(request, container.getContainerId(), response.getStatus(), response.getExecutionTimeMs());

            return response;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException(500, "系统被中断");
        } finally {
            if (container != null) {
                containerPool.returnContainer(container);
            }
        }
    }

    private void recordAudit(SandboxExecuteRequest request, String containerId, JudgeStatus status, long costTimeMs) {
        try {
            ExecutionAuditEntity audit = new ExecutionAuditEntity();
            String traceId = TraceIdUtils.getTraceId();
            if (traceId == null) {
                traceId = UUID.randomUUID().toString().replace("-", "");
            }
            audit.setTraceId(traceId);
            audit.setSubmitId(request.getSubmitId());
            audit.setContainerId(containerId);
            audit.setLanguage(request.getLanguage());
            audit.setResultStatus(status.name());
            audit.setCostTimeMs((int) costTimeMs);
            audit.setCreateTime(LocalDateTime.now());
            executionAuditMapper.insert(audit);
        } catch (Exception e) {
            log.error("Failed to record execution audit", e);
        }
    }
}

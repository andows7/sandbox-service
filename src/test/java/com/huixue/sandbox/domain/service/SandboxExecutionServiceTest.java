package com.huixue.sandbox.domain.service;

import com.huixue.sandbox.api.dto.SandboxExecuteRequest;
import com.huixue.sandbox.api.dto.SandboxExecuteResponse;
import com.huixue.sandbox.api.dto.TestCaseDto;
import com.huixue.sandbox.common.enums.JudgeStatus;
import com.huixue.sandbox.domain.model.CompileResult;
import com.huixue.sandbox.domain.model.ExecutionResult;
import com.huixue.sandbox.domain.model.TestCaseResult;
import com.huixue.sandbox.domain.strategy.CompilerStrategy;
import com.huixue.sandbox.domain.strategy.ExecutorStrategy;
import com.huixue.sandbox.infrastructure.persistence.mapper.ExecutionAuditMapper;
import com.huixue.sandbox.infrastructure.pool.ContainerPool;
import com.huixue.sandbox.infrastructure.pool.PooledContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class SandboxExecutionServiceTest {

    private SandboxExecutionService service;
    private ContainerPool pool;
    private CompilerStrategy javaCompiler;
    private ExecutorStrategy javaExecutor;

    @BeforeEach
    void setUp() throws InterruptedException {
        pool = Mockito.mock(ContainerPool.class);
        ExecutionAuditMapper auditMapper = Mockito.mock(ExecutionAuditMapper.class);
        javaCompiler = Mockito.mock(CompilerStrategy.class);
        javaExecutor = Mockito.mock(ExecutorStrategy.class);

        CompilerStrategy cppCompiler = Mockito.mock(CompilerStrategy.class);
        ExecutorStrategy cppExecutor = Mockito.mock(ExecutorStrategy.class);
        CompilerStrategy pythonCompiler = Mockito.mock(CompilerStrategy.class);
        ExecutorStrategy pythonExecutor = Mockito.mock(ExecutorStrategy.class);

        PooledContainer container = new PooledContainer();
        container.setContainerId("test-container-id");
        container.setWorkDirHostPath("/tmp/sandbox");
        when(pool.borrowContainer(any(), anyLong())).thenReturn(container);

        service = new SandboxExecutionService(
                pool, auditMapper,
                javaCompiler, javaExecutor,
                cppCompiler, cppExecutor,
                pythonCompiler, pythonExecutor
        );
    }

    @Test
    void testExecuteAC() {
        SandboxExecuteRequest request = new SandboxExecuteRequest();
        request.setSubmitId("sub-1");
        request.setLanguage("JAVA");
        request.setCode("public class Main {}");
        
        TestCaseDto tc = new TestCaseDto();
        tc.setInput("1");
        tc.setExpectedOutput("1");
        request.setTestCases(Collections.singletonList(tc));

        CompileResult cr = new CompileResult();
        cr.setSuccess(true);
        cr.setExecuteTarget("Main");
        when(javaCompiler.compile(anyString(), anyString(), anyString())).thenReturn(cr);

        ExecutionResult er = new ExecutionResult();
        er.setStatus(JudgeStatus.AC);
        er.setExecutionTimeMs(50);
        er.setMemoryUsedMb(20);
        TestCaseResult tcr = new TestCaseResult();
        tcr.setPassed(true);
        tcr.setActualOutput("1");
        er.setTestCaseResults(Collections.singletonList(tcr));
        
        when(javaExecutor.execute(anyString(), anyString(), anyString(), any(), any())).thenReturn(er);

        SandboxExecuteResponse response = service.execute(request);
        assertEquals(JudgeStatus.AC, response.getStatus());
        assertEquals(1, response.getTestCaseResults().size());
        assertEquals("1", response.getTestCaseResults().get(0).getActualOutput());
    }

    @Test
    void testExecuteCE() {
        SandboxExecuteRequest request = new SandboxExecuteRequest();
        request.setSubmitId("sub-2");
        request.setLanguage("JAVA");
        request.setCode("invalid code");

        CompileResult cr = new CompileResult();
        cr.setSuccess(false);
        cr.setCompileError("Main.java:1: error: expected ';'");
        when(javaCompiler.compile(anyString(), anyString(), anyString())).thenReturn(cr);

        SandboxExecuteResponse response = service.execute(request);
        assertEquals(JudgeStatus.CE, response.getStatus());
        assertEquals("Main.java:1: error: expected ';'", response.getCompileError());
    }
}

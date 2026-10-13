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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    private SandboxExecuteRequest buildRequest(String submitId) {
        SandboxExecuteRequest request = new SandboxExecuteRequest();
        request.setSubmitId(submitId);
        request.setLanguage("JAVA");
        request.setCode("public class Main { public static void main(String[] args){} }");
        TestCaseDto tc = new TestCaseDto();
        tc.setInput("1");
        tc.setExpectedOutput("1");
        request.setTestCases(Collections.singletonList(tc));
        request.setTimeLimitMs(1000);
        request.setMemoryLimitMb(128);
        return request;
    }

    private void mockCompileSuccess() {
        CompileResult cr = new CompileResult();
        cr.setSuccess(true);
        cr.setExecuteTarget("Main");
        when(javaCompiler.compile(anyString(), anyString(), anyString())).thenReturn(cr);
    }

    @Test
    @DisplayName("服务层评测: AC (答案正确)")
    void testExecuteAC() {
        SandboxExecuteRequest request = buildRequest("sub-ac");
        mockCompileSuccess();

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
        assertTrue(response.getTestCaseResults().get(0).isPassed());
        assertEquals("1", response.getTestCaseResults().get(0).getActualOutput());
    }

    @Test
    @DisplayName("服务层评测: WA (答案错误)")
    void testExecuteWA() {
        SandboxExecuteRequest request = buildRequest("sub-wa");
        mockCompileSuccess();

        ExecutionResult er = new ExecutionResult();
        er.setStatus(JudgeStatus.WA);
        er.setExecutionTimeMs(45);
        er.setMemoryUsedMb(20);
        TestCaseResult tcr = new TestCaseResult();
        tcr.setPassed(false);
        tcr.setActualOutput("wrong output");
        er.setTestCaseResults(Collections.singletonList(tcr));

        when(javaExecutor.execute(anyString(), anyString(), anyString(), any(), any())).thenReturn(er);

        SandboxExecuteResponse response = service.execute(request);
        assertEquals(JudgeStatus.WA, response.getStatus());
        assertEquals(1, response.getTestCaseResults().size());
        assertFalse(response.getTestCaseResults().get(0).isPassed());
        assertEquals("wrong output", response.getTestCaseResults().get(0).getActualOutput());
    }

    @Test
    @DisplayName("服务层评测: TLE (运行超时)")
    void testExecuteTLE() {
        SandboxExecuteRequest request = buildRequest("sub-tle");
        mockCompileSuccess();

        ExecutionResult er = new ExecutionResult();
        er.setStatus(JudgeStatus.TLE);
        er.setExecutionTimeMs(1005);
        er.setMemoryUsedMb(20);
        TestCaseResult tcr = new TestCaseResult();
        tcr.setPassed(false);
        er.setTestCaseResults(Collections.singletonList(tcr));

        when(javaExecutor.execute(anyString(), anyString(), anyString(), any(), any())).thenReturn(er);

        SandboxExecuteResponse response = service.execute(request);
        assertEquals(JudgeStatus.TLE, response.getStatus());
        assertFalse(response.getTestCaseResults().get(0).isPassed());
    }

    @Test
    @DisplayName("服务层评测: MLE (内存超限)")
    void testExecuteMLE() {
        SandboxExecuteRequest request = buildRequest("sub-mle");
        mockCompileSuccess();

        ExecutionResult er = new ExecutionResult();
        er.setStatus(JudgeStatus.MLE);
        er.setExecutionTimeMs(150);
        er.setMemoryUsedMb(130);
        TestCaseResult tcr = new TestCaseResult();
        tcr.setPassed(false);
        er.setTestCaseResults(Collections.singletonList(tcr));

        when(javaExecutor.execute(anyString(), anyString(), anyString(), any(), any())).thenReturn(er);

        SandboxExecuteResponse response = service.execute(request);
        assertEquals(JudgeStatus.MLE, response.getStatus());
        assertFalse(response.getTestCaseResults().get(0).isPassed());
    }

    @Test
    @DisplayName("服务层评测: RE (运行时异常)")
    void testExecuteRE() {
        SandboxExecuteRequest request = buildRequest("sub-re");
        mockCompileSuccess();

        ExecutionResult er = new ExecutionResult();
        er.setStatus(JudgeStatus.RE);
        er.setRuntimeError("java.lang.ArithmeticException: / by zero");
        er.setExecutionTimeMs(60);
        er.setMemoryUsedMb(20);
        TestCaseResult tcr = new TestCaseResult();
        tcr.setPassed(false);
        tcr.setErrorLine(5);
        er.setTestCaseResults(Collections.singletonList(tcr));

        when(javaExecutor.execute(anyString(), anyString(), anyString(), any(), any())).thenReturn(er);

        SandboxExecuteResponse response = service.execute(request);
        assertEquals(JudgeStatus.RE, response.getStatus());
        assertFalse(response.getTestCaseResults().get(0).isPassed());
        assertEquals(5, response.getTestCaseResults().get(0).getErrorLine());
    }

    @Test
    @DisplayName("服务层评测: CE (编译错误)")
    void testExecuteCE() {
        SandboxExecuteRequest request = new SandboxExecuteRequest();
        request.setSubmitId("sub-ce");
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

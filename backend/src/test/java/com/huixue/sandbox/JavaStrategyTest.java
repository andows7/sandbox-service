package com.huixue.sandbox;

import com.huixue.sandbox.common.enums.JudgeStatus;
import com.huixue.sandbox.domain.model.CompileResult;
import com.huixue.sandbox.domain.model.ExecutionResult;
import com.huixue.sandbox.domain.model.ResourceLimit;
import com.huixue.sandbox.domain.model.TestCase;
import com.huixue.sandbox.infrastructure.docker.DockerContainerManager;
import com.huixue.sandbox.infrastructure.docker.DockerExecResult;
import com.huixue.sandbox.strategy.java.JavaCompilerStrategy;
import com.huixue.sandbox.strategy.java.JavaExecutorStrategy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class JavaStrategyTest {

    private DockerContainerManager dockerContainerManager;
    private JavaCompilerStrategy compilerStrategy;
    private JavaExecutorStrategy executorStrategy;
    private String workDir;

    @BeforeEach
    void setUp() throws Exception {
        dockerContainerManager = Mockito.mock(DockerContainerManager.class);
        compilerStrategy = new JavaCompilerStrategy(dockerContainerManager);
        executorStrategy = new JavaExecutorStrategy(dockerContainerManager);
        Path tempDir = Files.createTempDirectory("sandbox_test");
        workDir = tempDir.toAbsolutePath().toString();
    }

    @AfterEach
    void tearDown() {
        deleteDirectory(new File(workDir));
    }

    private void deleteDirectory(File dir) {
        if (dir.exists()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
            dir.delete();
        }
    }

    /**
     * 1. 答案正确 (AC - Accepted)
     */
    @Test
    @DisplayName("测试 AC (答案正确): 编译成功且用例输出与预期完全匹配")
    void testStatus_AC() {
        String code = "import java.util.Scanner;\n" +
                "public class Main {\n" +
                "    public static void main(String[] args) {\n" +
                "        Scanner sc = new Scanner(System.in);\n" +
                "        int a = sc.nextInt();\n" +
                "        int b = sc.nextInt();\n" +
                "        System.out.println(a + b);\n" +
                "    }\n" +
                "}";

        DockerExecResult successResult = new DockerExecResult();
        successResult.setExitCode(0);
        successResult.setTimeout(false);
        successResult.setCostTimeMs(50);

        Mockito.when(dockerContainerManager.execInContainer(Mockito.anyString(), Mockito.any(), Mockito.anyLong()))
                .thenAnswer(invocation -> {
                    String[] cmd = invocation.getArgument(1);
                    if (cmd.length > 2 && cmd[2].contains("java ")) {
                        Files.writeString(Path.of(workDir, "output_0.txt"), "3\n");
                    }
                    return successResult;
                });

        CompileResult cr = compilerStrategy.compile(code, workDir, "dummy-container");
        assertTrue(cr.isSuccess());
        assertEquals("Main", cr.getExecuteTarget());

        TestCase tc = new TestCase();
        tc.setInput("1 2");
        tc.setExpectedOutput("3");

        ResourceLimit limit = new ResourceLimit(1000, 128);

        ExecutionResult er = executorStrategy.execute(cr.getExecuteTarget(), workDir, "dummy-container", Collections.singletonList(tc), limit);
        assertEquals(JudgeStatus.AC, er.getStatus());
        assertTrue(er.getTestCaseResults().get(0).isPassed());
        assertEquals("3", er.getTestCaseResults().get(0).getActualOutput());
    }

    /**
     * 2. 答案错误 (WA - Wrong Answer)
     */
    @Test
    @DisplayName("测试 WA (答案错误): 代码正常运行但输出与期望不符")
    void testStatus_WA() {
        String code = "public class Main {\n" +
                "    public static void main(String[] args) {\n" +
                "        System.out.println(100);\n" +
                "    }\n" +
                "}";

        DockerExecResult successResult = new DockerExecResult();
        successResult.setExitCode(0);
        successResult.setTimeout(false);
        successResult.setCostTimeMs(45);

        Mockito.when(dockerContainerManager.execInContainer(Mockito.anyString(), Mockito.any(), Mockito.anyLong()))
                .thenAnswer(invocation -> {
                    String[] cmd = invocation.getArgument(1);
                    if (cmd.length > 2 && cmd[2].contains("java ")) {
                        Files.writeString(Path.of(workDir, "output_0.txt"), "100\n");
                    }
                    return successResult;
                });

        CompileResult cr = compilerStrategy.compile(code, workDir, "dummy-container");
        assertTrue(cr.isSuccess());

        TestCase tc = new TestCase();
        tc.setInput("");
        tc.setExpectedOutput("200"); // 期望200，但实际输出100

        ResourceLimit limit = new ResourceLimit(1000, 128);

        ExecutionResult er = executorStrategy.execute(cr.getExecuteTarget(), workDir, "dummy-container", Collections.singletonList(tc), limit);
        assertEquals(JudgeStatus.WA, er.getStatus());
        assertFalse(er.getTestCaseResults().get(0).isPassed());
        assertEquals("100", er.getTestCaseResults().get(0).getActualOutput());
    }

    /**
     * 3. 运行超时 (TLE - Time Limit Exceeded)
     */
    @Test
    @DisplayName("测试 TLE (运行超时): 容器执行时限超出限制")
    void testStatus_TLE() {
        String code = "public class Main {\n" +
                "    public static void main(String[] args) {\n" +
                "        while(true) {}\n" +
                "    }\n" +
                "}";

        DockerExecResult successResult = new DockerExecResult();
        successResult.setExitCode(0);
        successResult.setTimeout(false);
        successResult.setCostTimeMs(50);

        DockerExecResult tleResult = new DockerExecResult();
        tleResult.setTimeout(true);
        tleResult.setCostTimeMs(500);

        Mockito.when(dockerContainerManager.execInContainer(Mockito.anyString(), Mockito.any(), Mockito.anyLong()))
                .thenAnswer(invocation -> {
                    String[] cmd = invocation.getArgument(1);
                    if (cmd.length > 2 && cmd[2].contains("javac")) {
                        return successResult;
                    } else {
                        return tleResult;
                    }
                });

        CompileResult cr = compilerStrategy.compile(code, workDir, "dummy-container");
        assertTrue(cr.isSuccess());

        TestCase tc = new TestCase();
        tc.setInput("");
        tc.setExpectedOutput("0");
        ResourceLimit limit = new ResourceLimit(500, 128);

        ExecutionResult er = executorStrategy.execute(cr.getExecuteTarget(), workDir, "dummy-container", Collections.singletonList(tc), limit);
        assertEquals(JudgeStatus.TLE, er.getStatus());
        assertFalse(er.getTestCaseResults().get(0).isPassed());
    }

    /**
     * 4. 内存超限 (MLE - Memory Limit Exceeded)
     */
    @Test
    @DisplayName("测试 MLE (内存超限): 捕获 OutOfMemoryError 或进程 OOM (exitCode 137)")
    void testStatus_MLE() {
        String code = "public class Main {\n" +
                "    public static void main(String[] args) {\n" +
                "        int[] arr = new int[100_000_000];\n" +
                "    }\n" +
                "}";

        DockerExecResult successResult = new DockerExecResult();
        successResult.setExitCode(0);
        successResult.setTimeout(false);
        successResult.setCostTimeMs(50);

        DockerExecResult mleResult = new DockerExecResult();
        mleResult.setExitCode(137); // Linux OOM Killer SIGKILL = 128 + 9 = 137
        mleResult.setTimeout(false);
        mleResult.setCostTimeMs(120);

        Mockito.when(dockerContainerManager.execInContainer(Mockito.anyString(), Mockito.any(), Mockito.anyLong()))
                .thenAnswer(invocation -> {
                    String[] cmd = invocation.getArgument(1);
                    if (cmd.length > 2 && cmd[2].contains("javac")) {
                        return successResult;
                    } else {
                        Files.writeString(Path.of(workDir, "error_0.txt"), "java.lang.OutOfMemoryError: Java heap space\n");
                        return mleResult;
                    }
                });

        CompileResult cr = compilerStrategy.compile(code, workDir, "dummy-container");
        assertTrue(cr.isSuccess());

        TestCase tc = new TestCase();
        tc.setInput("");
        tc.setExpectedOutput("0");
        ResourceLimit limit = new ResourceLimit(1000, 32);

        ExecutionResult er = executorStrategy.execute(cr.getExecuteTarget(), workDir, "dummy-container", Collections.singletonList(tc), limit);
        assertEquals(JudgeStatus.MLE, er.getStatus());
        assertFalse(er.getTestCaseResults().get(0).isPassed());
        assertEquals("Memory Limit Exceeded", er.getRuntimeError());
    }

    /**
     * 5. 运行时异常 (RE - Runtime Error)
     */
    @Test
    @DisplayName("测试 RE (运行时异常): 运行时除零等异常并提取错误行号")
    void testStatus_RE() {
        String code = "public class Main {\n" +
                "    public static void main(String[] args) {\n" +
                "        int a = 1 / 0;\n" +
                "    }\n" +
                "}";

        DockerExecResult successResult = new DockerExecResult();
        successResult.setExitCode(0);
        successResult.setTimeout(false);
        successResult.setCostTimeMs(50);

        DockerExecResult reResult = new DockerExecResult();
        reResult.setExitCode(1);
        reResult.setTimeout(false);
        reResult.setCostTimeMs(50);

        Mockito.when(dockerContainerManager.execInContainer(Mockito.anyString(), Mockito.any(), Mockito.anyLong()))
                .thenAnswer(invocation -> {
                    String[] cmd = invocation.getArgument(1);
                    if (cmd.length > 2 && cmd[2].contains("javac")) {
                        return successResult;
                    } else {
                        Files.writeString(Path.of(workDir, "error_0.txt"), "Exception in thread \"main\" java.lang.ArithmeticException: / by zero\n\tat Main.main(Main.java:3)\n");
                        return reResult;
                    }
                });

        CompileResult cr = compilerStrategy.compile(code, workDir, "dummy-container");
        assertTrue(cr.isSuccess());

        TestCase tc = new TestCase();
        tc.setInput("");
        tc.setExpectedOutput("0");
        ResourceLimit limit = new ResourceLimit(1000, 128);

        ExecutionResult er = executorStrategy.execute(cr.getExecuteTarget(), workDir, "dummy-container", Collections.singletonList(tc), limit);
        assertEquals(JudgeStatus.RE, er.getStatus());
        assertNotNull(er.getRuntimeError());
        assertEquals(3, er.getTestCaseResults().get(0).getErrorLine());
    }

    /**
     * 6. 编译错误 (CE - Compile Error)
     */
    @Test
    @DisplayName("测试 CE (编译错误): 语法不合法导致 javac 编译失败")
    void testStatus_CE() {
        String code = "public class Main { public static void main(String[] args) { invalid code } }";

        DockerExecResult failResult = new DockerExecResult();
        failResult.setExitCode(1);
        failResult.setTimeout(false);
        failResult.setCostTimeMs(50);

        Mockito.when(dockerContainerManager.execInContainer(Mockito.anyString(), Mockito.any(), Mockito.anyLong()))
                .thenAnswer(invocation -> {
                    Files.writeString(Path.of(workDir, "compile_err.txt"), "Main.java:1: error: ';' expected\n");
                    return failResult;
                });

        CompileResult cr = compilerStrategy.compile(code, workDir, "dummy-container");
        assertFalse(cr.isSuccess());
        assertNotNull(cr.getCompileError());
        assertFalse(cr.getCompileError().contains(workDir));
        assertTrue(cr.getCompileError().contains("Main.java"));
    }
}

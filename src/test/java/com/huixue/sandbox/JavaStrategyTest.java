package com.huixue.sandbox;

import com.huixue.sandbox.common.enums.JudgeStatus;
import com.huixue.sandbox.domain.model.CompileResult;
import com.huixue.sandbox.domain.model.ExecutionResult;
import com.huixue.sandbox.domain.model.ResourceLimit;
import com.huixue.sandbox.domain.model.TestCase;
import com.huixue.sandbox.strategy.java.JavaCompilerStrategy;
import com.huixue.sandbox.strategy.java.JavaExecutorStrategy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JavaStrategyTest {

    private JavaCompilerStrategy compilerStrategy;
    private JavaExecutorStrategy executorStrategy;
    private String workDir;

    @BeforeEach
    void setUp() throws Exception {
        compilerStrategy = new JavaCompilerStrategy();
        executorStrategy = new JavaExecutorStrategy();
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

    @Test
    void testCompileAndExecuteSuccess() {
        String code = "import java.util.Scanner;\n" +
                "public class Main {\n" +
                "    public static void main(String[] args) {\n" +
                "        Scanner sc = new Scanner(System.in);\n" +
                "        int a = sc.nextInt();\n" +
                "        int b = sc.nextInt();\n" +
                "        System.out.println(a + b);\n" +
                "    }\n" +
                "}";

        CompileResult cr = compilerStrategy.compile(code, workDir);
        assertTrue(cr.isSuccess());
        assertEquals("Main", cr.getExecuteTarget());

        TestCase tc = new TestCase();
        tc.setInput("1 2");
        tc.setExpectedOutput("3");

        ResourceLimit limit = new ResourceLimit(1000, 128);

        ExecutionResult er = executorStrategy.execute(cr.getExecuteTarget(), workDir, Collections.singletonList(tc), limit);
        assertEquals(JudgeStatus.AC, er.getStatus());
        assertTrue(er.getTestCaseResults().get(0).isPassed());
    }

    @Test
    void testCompileError() {
        String code = "public class Main { public static void main(String[] args) { invalid code } }";
        CompileResult cr = compilerStrategy.compile(code, workDir);
        assertFalse(cr.isSuccess());
        assertNotNull(cr.getCompileError());
        // Verify path is sanitized
        assertFalse(cr.getCompileError().contains(workDir));
        assertTrue(cr.getCompileError().contains("Main.java"));
    }

    @Test
    void testRuntimeError() {
        String code = "public class Main {\n" +
                "    public static void main(String[] args) {\n" +
                "        int a = 1 / 0;\n" +
                "    }\n" +
                "}";
        
        CompileResult cr = compilerStrategy.compile(code, workDir);
        assertTrue(cr.isSuccess());

        TestCase tc = new TestCase();
        tc.setInput("");
        tc.setExpectedOutput("0");
        ResourceLimit limit = new ResourceLimit(1000, 128);

        ExecutionResult er = executorStrategy.execute(cr.getExecuteTarget(), workDir, Collections.singletonList(tc), limit);
        assertEquals(JudgeStatus.RE, er.getStatus());
        assertNotNull(er.getRuntimeError());
        assertNotNull(er.getTestCaseResults().get(0).getErrorLine());
    }

    @Test
    void testTimeLimitExceeded() {
        String code = "public class Main {\n" +
                "    public static void main(String[] args) throws InterruptedException {\n" +
                "        while(true) {}\n" +
                "    }\n" +
                "}";

        CompileResult cr = compilerStrategy.compile(code, workDir);
        assertTrue(cr.isSuccess());

        TestCase tc = new TestCase();
        tc.setInput("");
        tc.setExpectedOutput("0");
        ResourceLimit limit = new ResourceLimit(500, 128); // 500ms limit

        ExecutionResult er = executorStrategy.execute(cr.getExecuteTarget(), workDir, Collections.singletonList(tc), limit);
        assertEquals(JudgeStatus.TLE, er.getStatus());
    }
}

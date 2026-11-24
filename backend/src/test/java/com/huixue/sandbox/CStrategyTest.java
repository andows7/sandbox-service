package com.huixue.sandbox;

import com.huixue.sandbox.common.enums.JudgeStatus;
import com.huixue.sandbox.domain.model.*;
import com.huixue.sandbox.infrastructure.docker.*;
import com.huixue.sandbox.strategy.c.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class CStrategyTest {
    @TempDir Path work;
    DockerContainerManager manager = mock(DockerContainerManager.class);
    CCompilerStrategy compiler = new CCompilerStrategy(manager);
    CExecutorStrategy executor = new CExecutorStrategy(manager);

    DockerExecResult exit(int code) { var r=new DockerExecResult(); r.setExitCode(code); return r; }
    TestCase example() { var t=new TestCase(); t.setInput("1 2\n"); t.setExpectedOutput("3"); return t; }

    @Test void c17CompileAndUtf8Source() throws Exception {
        when(manager.execInContainer(anyString(), any(), anyLong())).thenAnswer(i -> {
            String command=((String[])i.getArgument(1))[2];
            assertTrue(command.contains("gcc -std=c17"));
            assertFalse(command.contains("g++"));
            return exit(0);
        });
        assertTrue(compiler.compile("// 中文\nint main(void){return 0;}", work.toString(), "c").isSuccess());
        assertTrue(Files.readString(work.resolve("main.c")).contains("中文"));
    }

    @Test void compileErrorIsReturned() throws Exception {
        Files.writeString(work.resolve("compile_err.txt"), "main.c:1: error: invalid declaration");
        when(manager.execInContainer(anyString(), any(), anyLong())).thenReturn(exit(1));
        var result=compiler.compile("invalid", work.toString(), "c");
        assertFalse(result.isSuccess());
        assertTrue(result.getCompileError().contains("main.c:1"));
    }

    @Test void nativeAcceptedAndPeakMemory() throws Exception {
        when(manager.execInContainer(anyString(), any(), anyLong())).thenAnswer(i -> {
            Files.writeString(work.resolve("output_0.txt"), "3\n");
            Files.writeString(work.resolve("usage_0.txt"), "1536\n");
            return exit(0);
        });
        var result=executor.execute("./main",work.toString(),"c",List.of(example()),new ResourceLimit(1000,64));
        assertEquals(JudgeStatus.AC,result.getStatus());
        assertEquals(2,result.getMemoryUsedMb());
    }

    @Test void wrongAnswer() throws Exception {
        Files.writeString(work.resolve("output_0.txt"), "4");
        when(manager.execInContainer(anyString(),any(),anyLong())).thenReturn(exit(0));
        assertEquals(JudgeStatus.WA,executor.execute("./main",work.toString(),"c",List.of(example()),new ResourceLimit(1000,64)).getStatus());
    }

    @Test void timeoutAndMemoryAndRuntimeFailure() {
        for (var status : List.of(JudgeStatus.TLE,JudgeStatus.MLE,JudgeStatus.RE)) {
            var r=exit(status==JudgeStatus.MLE ? 137:139); r.setTimeout(status==JudgeStatus.TLE);
            when(manager.execInContainer(anyString(),any(),anyLong())).thenReturn(r);
            assertEquals(status,executor.execute("./main",work.toString(),"c",List.of(example()),new ResourceLimit(1000,64)).getStatus());
        }
    }

    @Test void oversizedOutputDoesNotLoadIntoHeap() throws Exception {
        Files.writeString(work.resolve("output_0.txt"), "x".repeat(65537));
        when(manager.execInContainer(anyString(),any(),anyLong())).thenReturn(exit(0));
        assertEquals(JudgeStatus.RE,executor.execute("./main",work.toString(),"c",List.of(example()),new ResourceLimit(1000,64)).getStatus());
    }
}

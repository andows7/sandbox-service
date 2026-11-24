package com.huixue.sandbox;

import com.huixue.sandbox.common.util.BoundedFiles;
import com.huixue.sandbox.infrastructure.docker.DockerContainerManager;
import com.huixue.sandbox.infrastructure.docker.DockerExecResult;
import com.huixue.sandbox.strategy.python.PythonCompilerStrategy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PythonCompilerStrategyTest {
    @TempDir Path work;
    private final DockerContainerManager manager = mock(DockerContainerManager.class);
    private final PythonCompilerStrategy compiler = new PythonCompilerStrategy(manager);

    private DockerExecResult exit(int code) {
        var result = new DockerExecResult();
        result.setExitCode(code);
        return result;
    }

    @Test void acceptsUtf8SourceAndPreservesScriptTarget() throws Exception {
        String code = "print('中文')\nraise RuntimeError('runtime only')\n";
        when(manager.execInContainer(anyString(), any(), anyLong())).thenReturn(exit(0));
        var result = compiler.compile(code, work.toString(), "python");
        assertTrue(result.isSuccess());
        assertEquals("main.py", result.getExecuteTarget());
        assertEquals(code, Files.readString(work.resolve("main.py")));
    }

    @Test void reportsSyntaxErrorsWithoutLeakingAbsolutePaths() throws Exception {
        Files.writeString(work.resolve("compile_err.txt"),
                "  File \"/sandbox/workdir/private/main.py\", line 1\nSyntaxError: expected ':'");
        when(manager.execInContainer(anyString(), any(), anyLong())).thenReturn(exit(1));
        var result = compiler.compile("if True\n    print(1)", work.toString(), "python");
        assertFalse(result.isSuccess());
        assertNull(result.getExecuteTarget());
        assertTrue(result.getCompileError().contains("SyntaxError"));
        assertTrue(result.getCompileError().contains("line 1"));
        assertFalse(result.getCompileError().contains("/sandbox/workdir/private"));
    }

    @Test void syntaxCheckTimeoutDoesNotPermitExecution() {
        var exec = exit(0);
        exec.setTimeout(true);
        when(manager.execInContainer(anyString(), any(), anyLong())).thenReturn(exec);
        var result = compiler.compile("print(1)", work.toString(), "python");
        assertFalse(result.isSuccess());
        assertNull(result.getExecuteTarget());
        assertTrue(result.getCompileError().contains("timeout"));
    }

    @Test void oversizedDiagnosticIsRejectedWithBoundedError() throws Exception {
        Files.writeString(work.resolve("compile_err.txt"), "x".repeat(BoundedFiles.ERROR_BYTES + 1));
        when(manager.execInContainer(anyString(), any(), anyLong())).thenReturn(exit(1));
        var result = compiler.compile("invalid python", work.toString(), "python");
        assertFalse(result.isSuccess());
        assertNull(result.getExecuteTarget());
        assertTrue(result.getCompileError().length() < 200);
    }
}

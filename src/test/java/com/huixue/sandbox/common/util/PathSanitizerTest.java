package com.huixue.sandbox.common.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PathSanitizerTest {

    @Test
    void testSanitizeJavaPath() {
        String errorMsg = "/sandbox/workdir/tmp_xx/Main.java:12: error: cannot find symbol";
        String expected = "Main.java:12: error: cannot find symbol";
        assertEquals(expected, PathSanitizer.sanitize(errorMsg));
    }

    @Test
    void testSanitizeCppPath() {
        String errorMsg = "/app/workdir/123/solution.cpp: In function 'int main()':";
        String expected = "solution.cpp: In function 'int main()':";
        assertEquals(expected, PathSanitizer.sanitize(errorMsg));
    }

    @Test
    void testSanitizeMultiplePaths() {
        String errorMsg = "File /sandbox/A.java:10 and File /sandbox/B.java:20";
        String expected = "File A.java:10 and File B.java:20";
        assertEquals(expected, PathSanitizer.sanitize(errorMsg));
    }

    @Test
    void testNoMatch() {
        String errorMsg = "Just a standard error with no paths";
        assertEquals(errorMsg, PathSanitizer.sanitize(errorMsg));
    }
}

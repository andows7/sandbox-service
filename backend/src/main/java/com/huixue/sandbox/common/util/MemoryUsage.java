package com.huixue.sandbox.common.util;

import java.nio.file.Path;

/** GNU time peak resident set in KiB, rounded up to MiB. Telemetry only; cgroups enforce memory. */
public final class MemoryUsage {
    private MemoryUsage() { }
    public static long readMb(Path file) {
        try {
            String value = BoundedFiles.readUtf8(file, 128).trim();
            // GNU time can prepend a signal/exit diagnostic.
            String[] lines = value.split("\\R");
            long kb = Long.parseLong(lines[lines.length - 1]);
            return kb >= 0 ? (kb + 1023) / 1024 : 0;
        } catch (Exception ignored) { return 0; }
    }
}

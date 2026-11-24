package com.huixue.sandbox.common.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/** Never load an untrusted output file in full or follow a container-created symlink. */
public final class BoundedFiles {
    public static final int OUTPUT_BYTES = 65536;
    public static final int ERROR_BYTES = 16384;

    private BoundedFiles() { }

    public static void writeUtf8(Path file, String value) throws IOException {
        try (var channel = Files.newByteChannel(file, java.util.Set.of(
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.WRITE,
                java.nio.file.StandardOpenOption.TRUNCATE_EXISTING, LinkOption.NOFOLLOW_LINKS))) {
            var bytes = StandardCharsets.UTF_8.encode(value == null ? "" : value);
            while (bytes.hasRemaining()) channel.write(bytes);
        }
    }

    public static String readUtf8(Path file, int maxBytes) throws IOException {
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return "";
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Output must be a regular file");
        }
        try (var in = Files.newByteChannel(file, java.util.Set.of(
                java.nio.file.StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS))) {
            var bytes = java.nio.ByteBuffer.allocate(maxBytes + 1);
            while (bytes.hasRemaining() && in.read(bytes) != -1) { }
            if (bytes.position() > maxBytes) throw new IOException("Output limit exceeded");
            return new String(bytes.array(), 0, bytes.position(), StandardCharsets.UTF_8);
        }
    }
}

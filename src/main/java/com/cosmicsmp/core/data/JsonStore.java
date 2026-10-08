package com.cosmicsmp.core.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/**
 * Crash-safe JSON persistence.
 * <ol>
 *     <li>The new content is written to {@code file.tmp} and flushed to disk (fsync).</li>
 *     <li>The current file is copied to {@code file.bak}.</li>
 *     <li>{@code file.tmp} atomically replaces {@code file}.</li>
 * </ol>
 * At any instant either the old or the new complete file exists; a power loss can never leave a half-written
 * file as the only copy. Reads fall back to the {@code .bak} copy if the main file is corrupt.
 */
public final class JsonStore {

    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private JsonStore() {
    }

    public static <T> T read(Path file, Type type) throws IOException {
        Path backup = sibling(file, ".bak");
        IOException failure = null;
        for (Path candidate : new Path[]{file, backup}) {
            if (!Files.exists(candidate)) {
                continue;
            }
            try (Reader reader = Files.newBufferedReader(candidate, StandardCharsets.UTF_8)) {
                T value = GSON.fromJson(reader, type);
                if (value != null) {
                    return value;
                }
            } catch (IOException | JsonParseException ex) {
                failure = new IOException("Corrupt data file " + candidate.getFileName() + ": " + ex.getMessage(), ex);
            }
        }
        if (failure != null) {
            throw failure;
        }
        return null;
    }

    public static boolean exists(Path file) {
        return Files.exists(file) || Files.exists(sibling(file, ".bak"));
    }

    public static void write(Path file, String json) throws IOException {
        Files.createDirectories(file.getParent());
        Path tmp = sibling(file, ".tmp");
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        try (FileChannel channel = FileChannel.open(tmp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
        }
        if (Files.exists(file)) {
            Files.copy(file, sibling(file, ".bak"), StandardCopyOption.REPLACE_EXISTING);
        }
        try {
            Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Moves an unreadable file aside so it can be inspected, instead of overwriting it. */
    public static void quarantine(Path file) {
        try {
            if (Files.exists(file)) {
                Files.move(file, sibling(file, ".corrupt-" + System.currentTimeMillis()), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ignored) {
            // best effort
        }
    }

    private static Path sibling(Path file, String suffix) {
        return file.resolveSibling(file.getFileName().toString() + suffix);
    }
}

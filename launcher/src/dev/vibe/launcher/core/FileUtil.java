package dev.vibe.launcher.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

/** File helpers: atomic writes, recursive deletes and properties files. */
public final class FileUtil {
    private FileUtil() { }

    public static Properties readProperties(Path file) {
        Properties values = new Properties();
        if (!Files.isRegularFile(file)) return values;
        try {
            InputStream input = Files.newInputStream(file);
            try { values.load(input); } finally { input.close(); }
        } catch (IOException | IllegalArgumentException ignored) {
            // A damaged optional preference file is treated as empty.
        }
        return values;
    }

    public static void writeProperties(Path file, Properties values, String comment) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path temporary = Files.createTempFile(file.toAbsolutePath().getParent(), "." + file.getFileName(), ".tmp");
        try {
            OutputStream output = Files.newOutputStream(temporary);
            try { values.store(output, comment); } finally { output.close(); }
            move(temporary, file);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public static void writeText(Path file, String text) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path temporary = Files.createTempFile(file.toAbsolutePath().getParent(), "." + file.getFileName(), ".tmp");
        try {
            Files.write(temporary, text.getBytes(StandardCharsets.UTF_8));
            move(temporary, file);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /** Replacing move that prefers an atomic rename. */
    public static void move(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Deletes a tree without following symbolic links out of it. */
    public static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return;
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                deleteWritable(file);
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult postVisitDirectory(Path directory, IOException error) throws IOException {
                if (error != null) throw error;
                deleteWritable(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public static boolean deleteTreeQuietly(Path root) {
        try { deleteTree(root); return true; } catch (IOException ignored) { return false; }
    }

    private static void deleteWritable(Path path) throws IOException {
        try {
            Files.delete(path);
        } catch (java.nio.file.AccessDeniedException error) {
            // Git and Gradle leave read-only files behind on Windows.
            if (!path.toFile().setWritable(true)) throw error;
            Files.delete(path);
        }
    }

    public static List<Path> children(Path directory) {
        if (!Files.isDirectory(directory)) return Collections.emptyList();
        List<Path> result = new ArrayList<Path>();
        try {
            DirectoryStream<Path> stream = Files.newDirectoryStream(directory);
            try { for (Path child : stream) result.add(child); } finally { stream.close(); }
        } catch (IOException ignored) {
            return Collections.emptyList();
        }
        Collections.sort(result);
        return result;
    }

    /** Resolves a slash-separated relative path, refusing anything that escapes {@code root}. */
    public static Path resolveInside(Path root, String relative) throws IOException {
        if (relative == null || relative.isEmpty() || relative.startsWith("/") || relative.startsWith("\\") || relative.contains(":")) {
            throw new IOException("Refusing unsafe path " + relative);
        }
        Path result = root;
        for (String segment : relative.split("/")) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..") || segment.contains("\\")) {
                throw new IOException("Refusing unsafe path " + relative);
            }
            result = result.resolve(segment);
        }
        Path normalized = result.normalize();
        if (!normalized.startsWith(root.normalize())) throw new IOException("Refusing unsafe path " + relative);
        return normalized;
    }
}

package dev.vibe.launcher.minecraft;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Unpacks LWJGL natives into a folder that {@code -Djava.library.path} points at.
 *
 * <p>Natives JARs contain DLLs, .so or .dylib files next to junk such as
 * {@code META-INF}; entries that would escape the target folder are refused.</p>
 */
public final class Natives {
    private static final long MAX_TOTAL = 512L * 1024L * 1024L;

    private Natives() { }

    public static void extract(Path jar, Path target, List<String> exclude) throws IOException {
        Path root = target.toAbsolutePath().normalize();
        Files.createDirectories(root);
        ZipFile zip = new ZipFile(jar.toFile());
        try {
            byte[] buffer = new byte[65536];
            long written = 0;
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                String name = entry.getName();
                if (excluded(name, exclude)) continue;
                Path output = safe(root, name);
                if (output == null) continue;
                Files.createDirectories(output.getParent());
                InputStream input = zip.getInputStream(entry);
                try {
                    OutputStream stream = Files.newOutputStream(output);
                    try {
                        int count;
                        while ((count = input.read(buffer)) != -1) {
                            written += count;
                            if (written > MAX_TOTAL) throw new IOException("The natives archive is too large to unpack safely.");
                            stream.write(buffer, 0, count);
                        }
                    } finally {
                        stream.close();
                    }
                } finally {
                    input.close();
                }
                output.toFile().setExecutable(true, false);
            }
        } finally {
            zip.close();
        }
    }

    public static boolean hasFiles(Path directory) {
        if (!Files.isDirectory(directory)) return false;
        try {
            java.nio.file.DirectoryStream<Path> stream = Files.newDirectoryStream(directory);
            try { return stream.iterator().hasNext(); } finally { stream.close(); }
        } catch (IOException ignored) {
            return false;
        }
    }

    private static boolean excluded(String name, List<String> exclude) {
        String clean = name.replace('\\', '/');
        if (clean.startsWith("META-INF/")) return true;
        if (exclude == null) return false;
        for (String prefix : exclude) {
            if (prefix != null && !prefix.isEmpty() && clean.startsWith(prefix.replace('\\', '/'))) return true;
        }
        return false;
    }

    /** {@code null} for names the game never needs, such as Unix symlink-style entries. */
    private static Path safe(Path root, String name) throws IOException {
        String clean = new String(name.replace('\\', '/').getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        if (clean.isEmpty() || clean.endsWith("/")) return null;
        while (clean.startsWith("./")) clean = clean.substring(2);
        if (clean.startsWith("/") || clean.contains(":")) return null;
        Path output = root.resolve(clean).normalize();
        return output.startsWith(root) ? output : null;
    }
}

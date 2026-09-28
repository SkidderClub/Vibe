package dev.vibe.launcher.install;

import dev.vibe.launcher.core.Progress;
import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Zip and tar.gz extraction that refuses entries escaping the target folder. */
final class Archives {
    private static final long MAX_TOTAL = 4L * 1024L * 1024L * 1024L;

    private Archives() { }

    static void extract(Path archive, Path target, String message, Progress progress) throws IOException {
        String name = archive.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        if (name.endsWith(".tar.gz") || name.endsWith(".tgz")) untarGz(archive, target, message, progress);
        else unzip(archive, target, message, progress);
    }

    static void unzip(Path archive, Path target, String message, Progress progress) throws IOException {
        Path root = target.toAbsolutePath().normalize();
        Files.createDirectories(root);
        ZipFile zip = new ZipFile(archive.toFile());
        try {
            int total = Math.max(1, zip.size()), done = 0;
            long written = 0;
            byte[] buffer = new byte[65536];
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Cancelled.");
                Path output = safe(root, entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(output);
                } else {
                    Files.createDirectories(output.getParent());
                    InputStream input = zip.getInputStream(entry);
                    try {
                        OutputStream stream = Files.newOutputStream(output);
                        try {
                            int count;
                            while ((count = input.read(buffer)) != -1) {
                                written += count;
                                if (written > MAX_TOTAL) throw new IOException("The archive is too large to extract safely.");
                                stream.write(buffer, 0, count);
                            }
                        } finally {
                            stream.close();
                        }
                    } finally {
                        input.close();
                    }
                    if (isExecutableName(output)) output.toFile().setExecutable(true, false);
                }
                if (++done % 64 == 0 || done == total) progress.update(message, done + " / " + total, (double) done / total);
            }
        } finally {
            zip.close();
        }
    }

    /** Reads ustar/GNU/PAX archives as published by Adoptium for Linux and macOS. */
    static void untarGz(Path archive, Path target, String message, Progress progress) throws IOException {
        Path root = target.toAbsolutePath().normalize();
        Files.createDirectories(root);
        long archiveSize = Math.max(1, Files.size(archive));
        CountingInputStream counting = new CountingInputStream(new BufferedInputStream(Files.newInputStream(archive), 65536));
        InputStream input = new BufferedInputStream(new GZIPInputStream(counting, 65536), 65536);
        try {
            byte[] header = new byte[512];
            byte[] buffer = new byte[65536];
            String longName = null, longLink = null, paxPath = null, paxLink = null;
            long written = 0;
            int entries = 0;
            while (true) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Cancelled.");
                if (!readFully(input, header)) break;
                if (isZeroBlock(header)) break;
                String name = cString(header, 0, 100);
                String prefix = cString(header, 345, 155);
                if (!prefix.isEmpty() && "ustar".equals(cString(header, 257, 5))) name = prefix + "/" + name;
                long size = octalOrBinary(header, 124, 12);
                int mode = (int) octalOrBinary(header, 100, 8);
                char type = (char) header[156];
                String link = cString(header, 157, 100);
                if (longName != null) { name = longName; longName = null; }
                if (paxPath != null) { name = paxPath; paxPath = null; }
                if (longLink != null) { link = longLink; longLink = null; }
                if (paxLink != null) { link = paxLink; paxLink = null; }

                if (type == 'L' || type == 'K' || type == 'x') {
                    byte[] data = readData(input, size);
                    if (type == 'L') longName = cString(data, 0, data.length);
                    else if (type == 'K') longLink = cString(data, 0, data.length);
                    else {
                        String[] pax = parsePax(data);
                        paxPath = pax[0];
                        paxLink = pax[1];
                    }
                    skipPadding(input, size);
                    continue;
                }
                if (type == 'g' || name.isEmpty() || name.equals("./")) {
                    skip(input, size);
                    skipPadding(input, size);
                    continue;
                }
                Path output = safe(root, name);
                if (type == '5') {
                    Files.createDirectories(output);
                } else if (type == '2') {
                    // Symbolic links may only point inside the extracted runtime.
                    Path resolved = output.getParent().resolve(link).normalize();
                    if (resolved.startsWith(root) && !link.startsWith("/")) {
                        Files.createDirectories(output.getParent());
                        Files.deleteIfExists(output);
                        try { Files.createSymbolicLink(output, output.getParent().relativize(resolved)); }
                        catch (UnsupportedOperationException | IOException ignored) { /* not needed for Java to start */ }
                    }
                } else if (type == '1') {
                    Path source = safe(root, link);
                    if (Files.isRegularFile(source)) {
                        Files.createDirectories(output.getParent());
                        Files.copy(source, output, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                } else if (type == '0' || type == '\0' || type == '7') {
                    Files.createDirectories(output.getParent());
                    OutputStream stream = Files.newOutputStream(output);
                    try {
                        long remaining = size;
                        while (remaining > 0) {
                            int count = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                            if (count < 0) throw new EOFException("The runtime archive is truncated.");
                            written += count;
                            if (written > MAX_TOTAL) throw new IOException("The archive is too large to extract safely.");
                            stream.write(buffer, 0, count);
                            remaining -= count;
                        }
                    } finally {
                        stream.close();
                    }
                    if ((mode & 0111) != 0 || isExecutableName(output)) output.toFile().setExecutable(true, false);
                    skipPadding(input, size);
                    if (++entries % 64 == 0) progress.update(message, null, Math.min(1, (double) counting.count / archiveSize));
                    continue;
                } else {
                    skip(input, size);
                }
                skipPadding(input, size);
            }
            progress.update(message, null, 1);
        } finally {
            input.close();
        }
    }

    private static Path safe(Path root, String name) throws IOException {
        String clean = name.replace('\\', '/');
        while (clean.startsWith("./")) clean = clean.substring(2);
        if (clean.startsWith("/") || clean.contains(":")) throw new IOException("Unsafe path in archive: " + name);
        Path output = root.resolve(clean).normalize();
        if (!output.startsWith(root)) throw new IOException("Unsafe path in archive: " + name);
        return output;
    }

    private static boolean isExecutableName(Path file) {
        Path parent = file.getParent();
        String name = file.getFileName().toString();
        return name.equals("gradlew") || name.equals("jspawnhelper") || (parent != null && parent.getFileName() != null
                && parent.getFileName().toString().equals("bin") && !name.contains("."));
    }

    private static String[] parsePax(byte[] data) {
        String[] result = new String[2];
        String text = new String(data, StandardCharsets.UTF_8);
        int position = 0;
        while (position < text.length()) {
            int space = text.indexOf(' ', position);
            if (space < 0) break;
            int length;
            try { length = Integer.parseInt(text.substring(position, space)); } catch (NumberFormatException ignored) { break; }
            if (length <= 0) break;
            // PAX lengths count bytes; entries are ASCII in practice, so characters match.
            int end = Math.min(text.length(), position + length);
            String record = text.substring(space + 1, Math.max(space + 1, end - 1));
            int equals = record.indexOf('=');
            if (equals > 0) {
                String key = record.substring(0, equals), value = record.substring(equals + 1);
                if (key.equals("path")) result[0] = value;
                else if (key.equals("linkpath")) result[1] = value;
            }
            position = end;
        }
        return result;
    }

    private static boolean readFully(InputStream input, byte[] block) throws IOException {
        int offset = 0;
        while (offset < block.length) {
            int count = input.read(block, offset, block.length - offset);
            if (count < 0) {
                if (offset == 0) return false;
                throw new EOFException("The runtime archive is truncated.");
            }
            offset += count;
        }
        return true;
    }

    private static byte[] readData(InputStream input, long size) throws IOException {
        if (size < 0 || size > 1024 * 1024) throw new IOException("The runtime archive has an oversized header.");
        byte[] data = new byte[(int) size];
        if (size > 0 && !readFully(input, data)) throw new EOFException("The runtime archive is truncated.");
        return data;
    }

    private static void skip(InputStream input, long size) throws IOException {
        long remaining = size;
        while (remaining > 0) {
            long skipped = input.skip(remaining);
            if (skipped <= 0) {
                if (input.read() < 0) throw new EOFException("The runtime archive is truncated.");
                skipped = 1;
            }
            remaining -= skipped;
        }
    }

    private static void skipPadding(InputStream input, long size) throws IOException {
        long padding = (512 - (size % 512)) % 512;
        skip(input, padding);
    }

    private static boolean isZeroBlock(byte[] block) {
        for (byte value : block) if (value != 0) return false;
        return true;
    }

    private static String cString(byte[] data, int offset, int length) {
        int end = offset;
        while (end < offset + length && end < data.length && data[end] != 0) end++;
        return new String(data, offset, end - offset, StandardCharsets.UTF_8).trim();
    }

    private static long octalOrBinary(byte[] header, int offset, int length) {
        if ((header[offset] & 0x80) != 0) {
            long value = 0;
            for (int index = offset + 1; index < offset + length; index++) value = (value << 8) | (header[index] & 0xFF);
            return value;
        }
        long value = 0;
        for (int index = offset; index < offset + length; index++) {
            byte c = header[index];
            if (c == 0 || c == ' ') { if (value != 0) break; continue; }
            if (c < '0' || c > '7') break;
            value = value * 8 + (c - '0');
        }
        return value;
    }

    private static final class CountingInputStream extends java.io.FilterInputStream {
        long count;
        CountingInputStream(InputStream input) { super(input); }
        @Override public int read() throws IOException { int value = super.read(); if (value >= 0) count++; return value; }
        @Override public int read(byte[] buffer, int offset, int length) throws IOException {
            int value = super.read(buffer, offset, length);
            if (value > 0) count += value;
            return value;
        }
        @Override public long skip(long amount) throws IOException { long value = super.skip(amount); count += value; return value; }
    }
}

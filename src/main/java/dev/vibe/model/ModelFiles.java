package dev.vibe.model;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** File helpers shared by the loaders, the folder scanner and the Sketchfab downloader. */
public final class ModelFiles {

    /** Extensions a model entry may have directly; directories are searched for these too. */
    public static final List<String> MODEL_EXTENSIONS = Collections.unmodifiableList(Arrays.asList(".glb", ".gltf", ".obj"));
    public static final String ARCHIVE_EXTENSION = ".zip";
    /** Sidecar written by the downloader with name, author and license of a Sketchfab model. */
    public static final String INFO_FILE = "vibe-model.json";
    private static final long MAX_ARCHIVE_BYTES = 2L * 1024L * 1024L * 1024L;

    private ModelFiles() {
    }

    public static boolean isModelFile(File file) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        for (String extension : MODEL_EXTENSIONS) if (name.endsWith(extension)) return true;
        return false;
    }

    public static boolean isArchive(File file) {
        return file.getName().toLowerCase(Locale.ROOT).endsWith(ARCHIVE_EXTENSION);
    }

    /** Display name of a file entry: its name without the extension. */
    public static String baseName(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        return file.isDirectory() || dot <= 0 ? name : name.substring(0, dot);
    }

    static byte[] read(File file, long limit) throws IOException {
        long length = file.length();
        if (length > limit) throw new IOException(file.getName() + " is larger than " + (limit >> 20) + " MB");
        return Files.readAllBytes(file.toPath());
    }

    /**
     * Finds the model file inside an extracted archive or model folder. glTF and GLB win over
     * OBJ because they carry materials reliably; shallower files win over deeper ones.
     */
    public static File findModelFile(File directory) {
        List<File> candidates = new ArrayList<File>();
        collect(directory, 0, candidates);
        File best = null;
        int bestScore = Integer.MAX_VALUE;
        for (File candidate : candidates) {
            String name = candidate.getName().toLowerCase(Locale.ROOT);
            int score = depth(directory, candidate) * 10;
            if (name.endsWith(".obj")) score += 5;
            if (name.equals("scene.gltf") || name.equals("scene.glb")) score -= 3;
            if (score < bestScore || (score == bestScore && candidate.getPath().compareTo(best.getPath()) < 0)) {
                best = candidate;
                bestScore = score;
            }
        }
        return best;
    }

    private static void collect(File directory, int depth, List<File> output) {
        File[] files = directory.listFiles();
        if (files == null || depth > 4) return;
        for (File file : files) {
            if (file.getName().startsWith(".")) continue;
            if (file.isDirectory()) collect(file, depth + 1, output);
            else if (isModelFile(file)) output.add(file);
        }
    }

    private static int depth(File root, File file) {
        int depth = 0;
        File current = file.getParentFile();
        while (current != null && !current.equals(root)) {
            depth++;
            current = current.getParentFile();
        }
        return depth;
    }

    /**
     * Resolves a path written inside a model file. Exported OBJ/MTL files often carry absolute
     * paths from the artist's machine, so a missing file is searched by name below the model.
     */
    static File resolveRelative(File directory, String path) {
        String normalized = path.replace('\\', '/').trim();
        if (normalized.isEmpty()) return null;
        File direct = new File(directory, normalized);
        if (!new File(normalized).isAbsolute() && direct.isFile() && isInside(directory.getParentFile(), direct)) return direct;
        String name = normalized.substring(normalized.lastIndexOf('/') + 1);
        if (name.isEmpty()) return null;
        File root = directory;
        // Look in the model folder and, for files nested in "source/", its parent folder.
        for (int level = 0; level < 2 && root != null; level++, root = root.getParentFile()) {
            File found = findByName(root, name, 0);
            if (found != null) return found;
        }
        return null;
    }

    private static File findByName(File directory, String name, int depth) {
        File[] files = directory.listFiles();
        if (files == null || depth > 4) return null;
        for (File file : files) if (file.isFile() && file.getName().equalsIgnoreCase(name)) return file;
        for (File file : files) {
            if (file.isDirectory() && !file.getName().startsWith(".")) {
                File found = findByName(file, name, depth + 1);
                if (found != null) return found;
            }
        }
        return null;
    }

    static boolean isInside(File root, File file) {
        if (root == null) return true;
        try {
            String base = root.getCanonicalPath() + File.separator;
            return file.getCanonicalPath().startsWith(base);
        } catch (IOException error) {
            return false;
        }
    }

    /** Extracts a zip archive, rejecting entries that would escape the target folder. */
    public static void extract(File archive, File target) throws IOException {
        if (!target.isDirectory() && !target.mkdirs()) throw new IOException("Cannot create " + target);
        long written = 0L;
        byte[] buffer = new byte[64 * 1024];
        ZipInputStream input = new ZipInputStream(new BufferedInputStream(new FileInputStream(archive)));
        try {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                String name = entry.getName().replace('\\', '/');
                if (name.isEmpty() || name.startsWith("/") || name.contains("../") || name.contains(":")) continue;
                File file = new File(target, name);
                if (!isInside(target, file)) continue;
                if (entry.isDirectory()) {
                    file.mkdirs();
                    continue;
                }
                File parent = file.getParentFile();
                if (parent != null && !parent.isDirectory()) parent.mkdirs();
                OutputStream output = new FileOutputStream(file);
                try {
                    int read;
                    while ((read = input.read(buffer)) > 0) {
                        written += read;
                        if (written > MAX_ARCHIVE_BYTES) throw new IOException("Archive expands beyond 2 GB");
                        output.write(buffer, 0, read);
                    }
                } finally {
                    output.close();
                }
            }
        } finally {
            input.close();
        }
    }

    /** A folder or file name that is safe on Windows, macOS and Linux. */
    public static String safeName(String name) {
        String cleaned = name == null ? "" : name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", " ").replaceAll("\\s+", " ").trim();
        while (cleaned.endsWith(".")) cleaned = cleaned.substring(0, cleaned.length() - 1).trim();
        if (cleaned.isEmpty()) cleaned = "model";
        return cleaned.length() > 80 ? cleaned.substring(0, 80).trim() : cleaned;
    }

    static void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        int read;
        while ((read = input.read(buffer)) > 0) output.write(buffer, 0, read);
    }
}

package dev.vibe.launcher.game;

import dev.vibe.launcher.core.FileUtil;
import dev.vibe.launcher.core.I18n;
import dev.vibe.launcher.core.Json;
import dev.vibe.launcher.core.Platform;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Custom Forge mods in the profile's {@code mods} folder. Disabled mods keep
 * their file with a {@code .disabled} suffix, which Forge ignores.
 */
public final class ModLibrary {
    public static final class Mod {
        public final Path file;
        public final String fileName, name, version, minecraftVersion, authors, description;
        public final boolean enabled;
        public final long size;

        Mod(Path file, ModInfo info, boolean enabled, long size) {
            this.file = file;
            this.fileName = file.getFileName().toString();
            this.name = info.name.isEmpty() ? displayName(fileName) : info.name;
            this.version = info.version;
            this.minecraftVersion = info.minecraftVersion;
            this.authors = info.authors;
            this.description = info.description;
            this.enabled = enabled;
            this.size = size;
        }

        /** Whether mcmod.info declares a Minecraft version other than 1.8.9. */
        public boolean wrongVersion() {
            return !minecraftVersion.isEmpty() && !minecraftVersion.contains("1.8.9") && !minecraftVersion.matches("\\[?1\\.8(\\.x)?\\]?");
        }
    }

    public static final class ImportResult {
        public final List<String> added = new ArrayList<String>();
        public final List<String> rejected = new ArrayList<String>();
    }

    static final class ModInfo {
        String id = "", name = "", version = "", minecraftVersion = "", authors = "", description = "";
        boolean optifine, fabric, modernForge;
    }

    private static final long MAX_SIZE = 300L * 1024L * 1024L;
    private static final String DISABLED = ".disabled";

    private final Path folder;

    public ModLibrary(Path folder) { this.folder = folder; }

    public Path folder() { return folder; }

    public List<Mod> list() {
        List<Mod> result = new ArrayList<Mod>();
        for (Path file : FileUtil.children(folder)) {
            String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
            boolean enabled = name.endsWith(".jar") || name.endsWith(".zip");
            boolean disabled = name.endsWith(".jar" + DISABLED) || name.endsWith(".zip" + DISABLED);
            if (!Files.isRegularFile(file) || (!enabled && !disabled)) continue;
            long size = file.toFile().length();
            result.add(new Mod(file, read(file), enabled, size));
        }
        result.sort((left, right) -> left.name.compareToIgnoreCase(right.name));
        return result;
    }

    public ImportResult importFiles(List<File> files) throws IOException {
        ImportResult result = new ImportResult();
        Files.createDirectories(folder);
        for (File source : files) {
            if (source == null) continue;
            String name = source.getName();
            String lower = name.toLowerCase(Locale.ROOT);
            if (!source.isFile() || !(lower.endsWith(".jar") || lower.endsWith(".zip"))) {
                result.rejected.add(I18n.t("{0}: not a .jar mod file", name));
                continue;
            }
            if (source.length() > MAX_SIZE) {
                result.rejected.add(I18n.t("{0}: file is too large", name));
                continue;
            }
            ModInfo info;
            try { info = inspect(source.toPath()); }
            catch (IOException error) {
                result.rejected.add(I18n.t("{0}: not a valid mod archive", name));
                continue;
            }
            if (info.optifine) { result.rejected.add(I18n.t("{0}: OptiFine is already included with Vibe", name)); continue; }
            if ("vibe".equalsIgnoreCase(info.id)) { result.rejected.add(I18n.t("{0}: Vibe is loaded automatically", name)); continue; }
            if (info.fabric) { result.rejected.add(I18n.t("{0}: Fabric mods do not work with Forge 1.8.9", name)); continue; }
            if (info.modernForge) { result.rejected.add(I18n.t("{0}: made for a newer Minecraft version", name)); continue; }
            Path target = uniqueTarget(name);
            if (target == null) {
                result.rejected.add(I18n.t("{0}: already installed", name));
                continue;
            }
            Files.copy(source.toPath(), target, StandardCopyOption.COPY_ATTRIBUTES);
            result.added.add(info.name.isEmpty() ? displayName(name) : info.name);
        }
        return result;
    }

    public void setEnabled(Mod mod, boolean enabled) throws IOException {
        if (mod.enabled == enabled) return;
        String name = mod.fileName;
        String next = enabled ? name.substring(0, name.length() - DISABLED.length()) : name + DISABLED;
        Path target = mod.file.resolveSibling(next);
        if (Files.exists(target)) throw new IOException(I18n.t("{0} already exists in the mods folder.", next));
        Files.move(mod.file, target);
    }

    public void delete(Mod mod) throws IOException { Platform.trashOrDelete(mod.file); }

    /** Returns {@code null} when an identical file is already present. */
    private Path uniqueTarget(String fileName) {
        String safe = fileName.replaceAll("[^A-Za-z0-9._ ()+-]", "_");
        Path target = folder.resolve(safe);
        if (Files.exists(target) || Files.exists(target.resolveSibling(safe + DISABLED))) {
            // Same name: treat as a duplicate rather than piling up copies.
            return null;
        }
        return target;
    }

    private static ModInfo read(Path file) {
        try { return inspect(file); } catch (IOException ignored) { return new ModInfo(); }
    }

    static ModInfo inspect(Path file) throws IOException {
        ModInfo info = new ModInfo();
        ZipFile zip = new ZipFile(file.toFile());
        try {
            info.optifine = zip.getEntry("optifine/OptiFineTweaker.class") != null || zip.getEntry("optifine/Installer.class") != null;
            info.fabric = zip.getEntry("fabric.mod.json") != null;
            info.modernForge = zip.getEntry("META-INF/mods.toml") != null;
            ZipEntry entry = zip.getEntry("mcmod.info");
            if (entry != null && entry.getSize() < 256 * 1024) {
                InputStream input = zip.getInputStream(entry);
                byte[] data;
                try { data = readAll(input, 256 * 1024); } finally { input.close(); }
                parseMcmodInfo(new String(data, StandardCharsets.UTF_8), info);
            }
        } finally {
            zip.close();
        }
        return info;
    }

    /** Accepts both the plain array format and version 2's {@code {"modList": [...]}}. */
    static void parseMcmodInfo(String text, ModInfo info) {
        try {
            Object root = Json.parse(text.trim());
            List<Object> list = root instanceof List ? Json.array(root) : Json.array(Json.object(root), "modList");
            if (list.isEmpty()) return;
            Map<String, Object> mod = Json.object(list.get(0));
            info.id = Json.string(mod, "modid");
            info.name = clean(Json.string(mod, "name"));
            info.version = clean(Json.string(mod, "version"));
            info.minecraftVersion = clean(Json.string(mod, "mcversion"));
            info.description = clean(Json.string(mod, "description"));
            StringBuilder authors = new StringBuilder();
            for (Object author : Json.array(mod, "authorList")) {
                if (!(author instanceof String)) continue;
                if (authors.length() > 0) authors.append(", ");
                authors.append(author);
            }
            if (authors.length() == 0) authors.append(Json.string(mod, "authors"));
            info.authors = clean(authors.toString());
            // Unexpanded Gradle placeholders say nothing useful.
            if (info.version.contains("${")) info.version = "";
            if (info.minecraftVersion.contains("${")) info.minecraftVersion = "";
        } catch (IOException ignored) {
            // Many mods ship a slightly broken mcmod.info; the file name is enough.
        }
    }

    private static String clean(String value) {
        return value.replaceAll("§.", "").replaceAll("\\s+", " ").trim();
    }

    private static String displayName(String fileName) {
        String name = fileName.replaceFirst("(?i)\\.(jar|zip)(\\.disabled)?$", "");
        return name.replace('_', ' ');
    }

    private static byte[] readAll(InputStream input, int limit) throws IOException {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (output.size() + count > limit) throw new IOException("mcmod.info is too large.");
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }
}

package dev.vibe.launcher.minecraft;

import dev.vibe.launcher.core.Json;
import dev.vibe.launcher.core.Platform;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Maven coordinates of Minecraft and Forge libraries, plus the library
 * {@code rules} that decide whether a library applies on this computer.
 */
public final class MavenLibrary {
    private MavenLibrary() { }

    /**
     * "group:artifact:version[:classifier][@ext]" → a path under
     * {@code libraries/}, for example {@code com/mojang/brigadier/1.0.6/brigadier-1.0.6.jar}.
     */
    public static String path(String coordinate) {
        String name = coordinate == null ? "" : coordinate.trim();
        String extension = "jar";
        String extClassifier = "";
        int at = name.indexOf('@');
        if (at >= 0) {
            String ext = name.substring(at + 1);
            name = name.substring(0, at);
            int dot = ext.lastIndexOf('.');
            if (dot > 0) {
                // Legacy notation such as "mcp:1.8.9@srg.zip" carries a classifier and an extension.
                extClassifier = ext.substring(0, dot);
                extension = ext.substring(dot + 1);
            } else {
                extension = ext;
            }
        }
        String[] parts = name.split(":");
        if (parts.length < 3) throw new IllegalArgumentException("Bad Maven coordinate " + coordinate);
        String group = parts[0], artifact = parts[1], version = parts[2];
        String classifier = parts.length > 3 ? parts[3] : extClassifier;
        String file = artifact + "-" + version + (classifier.isEmpty() ? "" : "-" + classifier) + "." + extension;
        return group.replace('.', '/') + "/" + artifact + "/" + version + "/" + file;
    }

    /** The artifact file name without folders. */
    public static String fileName(String coordinate) {
        String path = path(coordinate);
        return path.substring(path.lastIndexOf('/') + 1);
    }

    /** "group:artifact:classifier" with the version removed. */
    public static String key(String coordinate) {
        String name = coordinate == null ? "" : coordinate.trim();
        int at = name.indexOf('@');
        if (at >= 0) name = name.substring(0, at);
        String[] parts = name.split(":");
        if (parts.length < 3) return name;
        return parts[0] + ":" + parts[1] + (parts.length > 3 ? ":" + parts[3] : "");
    }

    /** The version segment, or "" when the coordinate is malformed. */
    public static String version(String coordinate) {
        String[] parts = (coordinate == null ? "" : coordinate).split(":");
        return parts.length > 2 ? parts[2] : "";
    }

    /**
     * Evaluates Mojang's {@code rules} array: the last matching allow/disallow
     * rule wins and libraries without rules are allowed everywhere.
     */
    public static boolean allowed(List<Object> rules) {
        if (rules == null || rules.isEmpty()) return true;
        boolean allowed = false;
        for (Object item : rules) {
            Map<String, Object> rule = Json.object(item);
            if (!matches(rule)) continue;
            allowed = "allow".equals(Json.string(rule, "action"));
        }
        return allowed;
    }

    private static boolean matches(Map<String, Object> rule) {
        Map<String, Object> os = Json.object(rule, "os");
        if (!os.isEmpty()) {
            String name = Json.string(os, "name");
            if (!name.isEmpty() && !name.equalsIgnoreCase(osName())) return false;
            String arch = Json.string(os, "arch");
            if (!arch.isEmpty() && !Pattern.compile(arch).matcher(archName()).matches()) return false;
            String version = Json.string(os, "version");
            if (!version.isEmpty() && !Pattern.compile(version).matcher(System.getProperty("os.version", "")).matches()) return false;
        }
        Map<String, Object> features = Json.object(rule, "features");
        if (!features.isEmpty()) {
            // The launcher offers no feature flags (demo mode, custom resolution, …).
            for (String name : features.keySet()) if (Json.bool(features, name)) return false;
        }
        return true;
    }

    /** Mojang's {@code os.name}: windows, osx or linux. */
    public static String osName() {
        switch (Platform.os()) {
            case WINDOWS: return "windows";
            case MAC: return "osx";
            default: return "linux";
        }
    }

    /** Mojang's {@code os.arch}: x86, x86_64 or arm64. */
    public static String archName() {
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        if (arch.equals("aarch64") || arch.equals("arm64")) return "arm64";
        if (arch.equals("x86") || arch.equals("i386") || arch.equals("i686")) return "x86";
        return "x86_64";
    }

    /** Forge's classifier for native libraries on this platform, e.g. "natives-windows". */
    public static String nativesClassifier() {
        return "natives-" + osName();
    }
}

package dev.vibe.launcher.core;

import java.awt.Rectangle;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Launcher preferences in {@code launcher.properties}. Keys written by the
 * first launcher (selected account, runtimes, source revision) keep their names.
 */
public final class Settings {
    public enum AfterLaunch { KEEP_OPEN, MINIMIZE, CLOSE }

    private final Path file;
    private final AppLog log;
    private final Properties values;

    public Settings(Path file, AppLog log) {
        this.file = file;
        this.log = log;
        this.values = FileUtil.readProperties(file);
        // Keys of the first launcher that no longer have a meaning.
        values.remove("projectRoot");
        values.remove("installed");
        values.remove("updateRepository");
    }

    // ---- preferences -----------------------------------------------------

    public String language() { return get("language", "auto"); }
    public void setLanguage(String value) { set("language", value); }

    public String theme() { return get("theme", "LAVENDER"); }
    public void setTheme(String value) { set("theme", value); }

    public int memoryMb() {
        int fallback = defaultMemoryMb();
        try { return clampMemory(Integer.parseInt(get("memoryMb", Integer.toString(fallback)))); }
        catch (NumberFormatException ignored) { return fallback; }
    }
    public void setMemoryMb(int value) { set("memoryMb", Integer.toString(clampMemory(value))); }

    public static int maxMemoryMb() {
        long total = Platform.totalMemoryMb();
        return (int) Math.max(2048, Math.min(16384, (total - 1536) / 512 * 512));
    }

    private static int clampMemory(int value) { return Math.max(1024, Math.min(maxMemoryMb(), value / 256 * 256)); }

    private static int defaultMemoryMb() {
        long total = Platform.totalMemoryMb();
        return clampMemory(total >= 12000 ? 4096 : total >= 7000 ? 3072 : 2048);
    }

    public AfterLaunch afterLaunch() {
        try { return AfterLaunch.valueOf(get("afterLaunch", AfterLaunch.KEEP_OPEN.name())); }
        catch (IllegalArgumentException ignored) { return AfterLaunch.KEEP_OPEN; }
    }
    public void setAfterLaunch(AfterLaunch value) { set("afterLaunch", value.name()); }

    public boolean autoUpdate() { return !"false".equals(get("autoUpdate", "true")); }
    public void setAutoUpdate(boolean value) { set("autoUpdate", Boolean.toString(value)); }

    public boolean animations() { return !"false".equals(get("animations", "true")); }
    public void setAnimations(boolean value) { set("animations", Boolean.toString(value)); }

    public boolean espPreview() { return !"false".equals(get("espPreview", "true")); }
    public void setEspPreview(boolean value) { set("espPreview", Boolean.toString(value)); }

    public String launchMode() { return get("launchMode", "VIBE"); }
    public void setLaunchMode(String value) { set("launchMode", value); }

    public String selectedUuid() { return get("selectedUuid", ""); }
    public String selectedName() { return get("selectedName", ""); }
    public void setSelectedAccount(String uuid, String name) {
        values.setProperty("selectedUuid", uuid == null ? "" : uuid);
        values.setProperty("selectedName", name == null ? "" : name);
        save();
    }

    // ---- installation state ----------------------------------------------

    public String sourceRevision() { return get("sourceRevision", ""); }
    public void setSourceRevision(String revision) { set("sourceRevision", revision); }
    /** True after an interrupted delta update; the next update downloads everything. */
    public boolean sourceIncomplete() { return "true".equals(get("sourceIncomplete", "false")); }
    public void setSourceIncomplete(boolean value) { set("sourceIncomplete", Boolean.toString(value)); }
    public long sourceUpdatedAt() { return parseLong(get("sourceUpdatedAt", "0")); }
    public void setSourceUpdatedAt(long value) { set("sourceUpdatedAt", Long.toString(value)); }
    public String sourceMessage() { return get("sourceMessage", ""); }
    public long sourceCommitTime() { return parseLong(get("sourceCommitTime", "0")); }
    public void setSourceCommit(String message, long time) {
        values.setProperty("sourceMessage", message == null ? "" : message);
        values.setProperty("sourceCommitTime", Long.toString(time));
        save();
    }

    public String java8Home() { return get("java8Home", ""); }
    public void setJava8Home(String value) { set("java8Home", value); }
    public String jdk21Home() { return get("jdk21Home", ""); }
    public void setJdk21Home(String value) { set("jdk21Home", value); }

    public Rectangle windowBounds() {
        String[] parts = get("window", "").split(",");
        if (parts.length != 4) return null;
        try {
            return new Rectangle(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
    public void setWindowBounds(Rectangle bounds) { set("window", bounds.x + "," + bounds.y + "," + bounds.width + "," + bounds.height); }
    public boolean windowMaximized() { return "true".equals(get("windowMaximized", "false")); }
    public void setWindowMaximized(boolean value) { set("windowMaximized", Boolean.toString(value)); }

    // ---- storage ---------------------------------------------------------

    private String get(String key, String fallback) {
        String value = values.getProperty(key);
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private void set(String key, String value) {
        values.setProperty(key, value == null ? "" : value);
        save();
    }

    public synchronized void save() {
        try { FileUtil.writeProperties(file, values, "Vibe Launcher settings"); }
        catch (IOException error) { log.warn("Could not save launcher settings", error); }
    }

    private static long parseLong(String value) {
        try { return Long.parseLong(value); } catch (NumberFormatException ignored) { return 0; }
    }
}

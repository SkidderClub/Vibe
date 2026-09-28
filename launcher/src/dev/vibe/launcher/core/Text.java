package dev.vibe.launcher.core;

import java.util.Locale;

/** Formatting helpers shared by services and the interface. */
public final class Text {
    private Text() { }

    public static boolean blank(String value) { return value == null || value.trim().isEmpty(); }

    /** A short, user-presentable description of an error, never a stack trace. */
    public static String describe(Throwable error) {
        if (error == null) return "";
        Throwable cause = error;
        while (cause.getCause() != null && blank(cause.getMessage())) cause = cause.getCause();
        String message = cause.getMessage();
        if (blank(message)) message = cause.getClass().getSimpleName();
        return shorten(message.trim(), 220);
    }

    public static String shorten(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, Math.max(0, max - 1)) + "…";
    }

    public static String firstLine(String value) {
        if (value == null) return "";
        int end = value.indexOf('\n');
        return (end < 0 ? value : value.substring(0, end)).trim();
    }

    public static String bytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024L * 1024L) return String.format(Locale.ROOT, "%.0f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024L * 1024L) return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format(Locale.ROOT, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    public static String shortSha(String sha) { return sha == null ? "" : sha.length() > 7 ? sha.substring(0, 7) : sha; }
}

package dev.vibe.launcher.core;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * Operating-system specifics. The launcher is compiled for Java 8 but uses the
 * Java 9+ process API through reflection when it is available, so a running
 * game can be stopped together with the Gradle process that started it.
 */
public final class Platform {
    public enum Os { WINDOWS, MAC, LINUX }

    private static final Os OS = detectOs();

    private Platform() { }

    public static Os os() { return OS; }
    public static boolean windows() { return OS == Os.WINDOWS; }

    private static Os detectOs() {
        String name = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (name.contains("win")) return Os.WINDOWS;
        if (name.contains("mac") || name.contains("darwin")) return Os.MAC;
        return Os.LINUX;
    }

    /** Architecture name as used by the Adoptium API. */
    public static String architecture() {
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        if (arch.equals("aarch64") || arch.equals("arm64")) return "aarch64";
        return "x64";
    }

    public static String adoptiumOs() {
        switch (OS) {
            case WINDOWS: return "windows";
            case MAC: return "mac";
            default: return "linux";
        }
    }

    public static Path javaExecutable(Path home, boolean windowless) {
        if (windows()) return home.resolve("bin").resolve(windowless ? "javaw.exe" : "java.exe");
        return home.resolve("bin").resolve("java");
    }

    public static Path javacExecutable(Path home) {
        return home.resolve("bin").resolve(windows() ? "javac.exe" : "javac");
    }

    /** The Java runtime running this launcher, preferring javaw on Windows. */
    public static Path currentJava(boolean windowless) {
        Path home = Paths.get(System.getProperty("java.home"));
        Path candidate = javaExecutable(home, windowless);
        return Files.isRegularFile(candidate) ? candidate : javaExecutable(home, false);
    }

    /** The launcher JAR itself, or {@code null} when running from class folders. */
    public static Path currentJar() {
        try {
            Path location = Paths.get(Platform.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return Files.isRegularFile(location) && location.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar") ? location : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    public static File nullDevice() { return new File(windows() ? "NUL" : "/dev/null"); }

    public static void openFolder(Path folder) throws IOException {
        Files.createDirectories(folder);
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            try { Desktop.getDesktop().open(folder.toFile()); return; } catch (Exception ignored) { /* fall through */ }
        }
        run(windows() ? new String[] { "explorer.exe", folder.toString() }
                : OS == Os.MAC ? new String[] { "open", folder.toString() } : new String[] { "xdg-open", folder.toString() });
    }

    public static void browse(String url) throws IOException {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            try { Desktop.getDesktop().browse(URI.create(url)); return; } catch (Exception ignored) { /* fall through */ }
        }
        run(windows() ? new String[] { "rundll32", "url.dll,FileProtocolHandler", url }
                : OS == Os.MAC ? new String[] { "open", url } : new String[] { "xdg-open", url });
    }

    private static void run(String[] command) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        builder.redirectOutput(ProcessBuilder.Redirect.appendTo(nullDevice()));
        builder.start();
    }

    /** Moves a file to the recycle bin when the platform supports it, otherwise deletes it. */
    public static void trashOrDelete(Path file) throws IOException {
        try {
            Method moveToTrash = Desktop.class.getMethod("moveToTrash", File.class);
            if (Desktop.isDesktopSupported() && (Boolean) moveToTrash.invoke(Desktop.getDesktop(), file.toFile())) return;
        } catch (Exception ignored) {
            // Java 8, or no trash on this desktop.
        }
        Files.deleteIfExists(file);
    }

    public static long totalMemoryMb() {
        try {
            Object bean = ManagementFactory.getOperatingSystemMXBean();
            // Resolve through the exported interface: the implementation class is not accessible on Java 16+.
            Class<?> type = Class.forName("com.sun.management.OperatingSystemMXBean");
            if (!type.isInstance(bean)) return 8192;
            return ((Number) type.getMethod("getTotalPhysicalMemorySize").invoke(bean)).longValue() / (1024L * 1024L);
        } catch (Exception ignored) {
            return 8192;
        }
    }

    // ---- process tree handling (Java 9+ via reflection) --------------------

    public static long pid(Process process) {
        try { return ((Number) Process.class.getMethod("pid").invoke(process)).longValue(); }
        catch (Exception ignored) { return -1; }
    }

    public static boolean processApiAvailable() {
        try { Class.forName("java.lang.ProcessHandle"); return true; } catch (ClassNotFoundException ignored) { return false; }
    }

    /** Whether a process started by an earlier launcher run is still alive. {@code false} on Java 8. */
    public static boolean isAlive(long pid) {
        Object handle = handle(pid);
        if (handle == null) return false;
        try { return (Boolean) Class.forName("java.lang.ProcessHandle").getMethod("isAlive").invoke(handle); }
        catch (Exception ignored) { return false; }
    }

    /** Stops a process and all its descendants: Gradle, its daemon and the Minecraft JVM. */
    public static void destroyTree(Process process) {
        if (process == null) return;
        long pid = pid(process);
        if (pid > 0) destroyTree(pid);
        process.destroy();
    }

    public static void destroyTree(long pid) {
        Object handle = handle(pid);
        if (handle == null) {
            if (windows() && pid > 0) {
                try { new ProcessBuilder("taskkill", "/PID", Long.toString(pid), "/T", "/F").redirectErrorStream(true)
                        .redirectOutput(ProcessBuilder.Redirect.appendTo(nullDevice())).start().waitFor(); }
                catch (Exception ignored) { /* best effort */ }
            }
            return;
        }
        try {
            // Methods come from the public interface; the implementation class is not accessible.
            Class<?> type = Class.forName("java.lang.ProcessHandle");
            Method destroy = type.getMethod("destroy");
            List<Object> children = new ArrayList<Object>();
            Iterator<?> iterator = ((java.util.stream.Stream<?>) type.getMethod("descendants").invoke(handle)).iterator();
            while (iterator.hasNext()) children.add(iterator.next());
            // Deepest descendants come last: stop the Minecraft JVM before Gradle.
            for (int index = children.size() - 1; index >= 0; index--) destroy.invoke(children.get(index));
            destroy.invoke(handle);
        } catch (Exception ignored) {
            // The process may already have exited.
        }
    }

    private static Object handle(long pid) {
        if (pid <= 0) return null;
        try {
            Class<?> type = Class.forName("java.lang.ProcessHandle");
            java.util.Optional<?> optional = (java.util.Optional<?>) type.getMethod("of", long.class).invoke(null, pid);
            return optional.isPresent() ? optional.get() : null;
        } catch (Exception ignored) {
            return null;
        }
    }
}

package dev.vibe.launcher.core;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Every folder the launcher owns. Windows keeps the historic
 * {@code %APPDATA%\VibeLauncher} location so existing installs, runtimes and
 * game profiles are picked up unchanged.
 */
public final class AppPaths {
    private final Path root;

    private AppPaths(Path root) { this.root = root.toAbsolutePath().normalize(); }

    public static AppPaths detect() {
        String override = System.getProperty("vibe.launcher.home");
        if (override != null && !override.trim().isEmpty()) return new AppPaths(Paths.get(override.trim()));
        String home = System.getProperty("user.home");
        Path base;
        switch (Platform.os()) {
            case WINDOWS: {
                String appData = System.getenv("APPDATA");
                base = appData == null || appData.trim().isEmpty() ? Paths.get(home, "AppData", "Roaming") : Paths.get(appData);
                break;
            }
            case MAC:
                base = Paths.get(home, "Library", "Application Support");
                break;
            default: {
                String xdg = System.getenv("XDG_DATA_HOME");
                base = xdg == null || xdg.trim().isEmpty() ? Paths.get(home, ".local", "share") : Paths.get(xdg);
                break;
            }
        }
        return new AppPaths(base.resolve("VibeLauncher"));
    }

    public Path root() { return root; }
    public Path settingsFile() { return root.resolve("launcher.properties"); }
    public Path lockFile() { return root.resolve("launcher.lock"); }
    /** The managed Vibe checkout. Its {@code run/client} folder is the persistent game profile. */
    public Path source() { return root.resolve("source"); }
    public Path runtimes() { return root.resolve("runtime"); }
    public Path logs() { return root.resolve("logs"); }
    public Path gameLogs() { return logs().resolve("game"); }
    public Path cache() { return root.resolve("cache"); }
    public Path skinCache() { return cache().resolve("skins"); }
    public Path staging() { return root.resolve("staging"); }
    public Path sessionFile() { return root.resolve("session.properties"); }
    public Path profile() { return source().resolve("run").resolve("client"); }
}

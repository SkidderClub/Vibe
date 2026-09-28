package dev.vibe.launcher.game;

import dev.vibe.launcher.core.FileUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Vibe's persistent Forge profile ({@code run/client}) and the small files the
 * launcher shares with the client: the credential-free launch bridge, the menu
 * theme and the local gamertag.
 */
public final class GameProfile {
    private final Path directory;

    public GameProfile(Path directory) { this.directory = directory; }

    public Path directory() { return directory; }
    public Path mods() { return directory.resolve("mods"); }
    public Path resourcePacks() { return directory.resolve("resourcepacks"); }
    public Path screenshots() { return directory.resolve("screenshots"); }
    public Path crashReports() { return directory.resolve("crash-reports"); }
    public Path vibe() { return directory.resolve("vibe"); }
    public Path accounts() { return vibe().resolve("accounts"); }
    private Path bridge() { return vibe().resolve("launcher.properties"); }
    private Path menu() { return vibe().resolve("menu.properties"); }

    /** Read by {@code dev.vibe.launcher.LauncherBridge} and {@code AccountManager} when Vibe starts. */
    public void writeBridge(LaunchMode mode, String selectedUuid, String selectedName) throws IOException {
        Properties bridge = new Properties();
        bridge.setProperty("mode", mode.bridgeValue);
        bridge.setProperty("selectedUuid", selectedUuid == null ? "" : selectedUuid);
        bridge.setProperty("selectedName", selectedName == null ? "" : selectedName);
        FileUtil.writeProperties(bridge(), bridge, "Vibe Launcher bridge - no credentials");
    }

    /** The theme Vibe's main menu uses; {@code null} before the first launch. */
    public Theme readTheme() {
        if (!Files.isRegularFile(menu())) return null;
        String value = FileUtil.readProperties(menu()).getProperty("theme");
        return value == null ? null : Theme.parse(value);
    }

    public void writeTheme(Theme theme) throws IOException {
        Properties values = FileUtil.readProperties(menu());
        values.setProperty("theme", theme.name());
        FileUtil.writeProperties(menu(), values, "Vibe menu colours");
    }

    /** The local display name chosen on Vibe's first start. */
    public String gamertag() { return FileUtil.readProperties(vibe().resolve("identity.properties")).getProperty("gamertag", "").trim(); }

    public String clientLanguage() { return FileUtil.readProperties(vibe().resolve("identity.properties")).getProperty("language", "").trim(); }

    public Path latestCrashReport() {
        Path latest = null;
        long newest = 0;
        for (Path file : FileUtil.children(crashReports())) {
            long modified = file.toFile().lastModified();
            if (file.getFileName().toString().endsWith(".txt") && modified > newest) {
                newest = modified;
                latest = file;
            }
        }
        return latest;
    }

    /**
     * Copies options and resource packs from the regular Minecraft folder once,
     * never overwriting anything that already exists in the Vibe profile.
     */
    public int importFromMinecraft() throws IOException {
        Path minecraft = defaultMinecraftDirectory();
        if (!Files.isDirectory(minecraft)) throw new IOException("No Minecraft folder was found at " + minecraft + ".");
        int copied = 0;
        Files.createDirectories(directory);
        for (Path file : FileUtil.children(minecraft)) {
            String name = file.getFileName().toString();
            if (Files.isRegularFile(file) && name.startsWith("options") && name.endsWith(".txt") && !Files.exists(directory.resolve(name))) {
                Files.copy(file, directory.resolve(name));
                copied++;
            }
        }
        for (Path pack : FileUtil.children(minecraft.resolve("resourcepacks"))) {
            Path target = resourcePacks().resolve(pack.getFileName().toString());
            if (Files.exists(target)) continue;
            Files.createDirectories(resourcePacks());
            if (Files.isDirectory(pack)) copyTree(pack, target);
            else Files.copy(pack, target);
            copied++;
        }
        return copied;
    }

    private static void copyTree(Path source, Path target) throws IOException {
        Files.createDirectories(target);
        for (Path child : FileUtil.children(source)) {
            Path destination = target.resolve(child.getFileName().toString());
            if (Files.isDirectory(child)) copyTree(child, destination);
            else Files.copy(child, destination);
        }
    }

    public static Path defaultMinecraftDirectory() {
        String home = System.getProperty("user.home");
        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            return (appData == null ? Paths.get(home, "AppData", "Roaming") : Paths.get(appData)).resolve(".minecraft");
        }
        if (os.contains("mac")) return Paths.get(home, "Library", "Application Support", "minecraft");
        return Paths.get(home, ".minecraft");
    }
}

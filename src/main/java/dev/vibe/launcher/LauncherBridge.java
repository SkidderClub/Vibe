package dev.vibe.launcher;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Credential-free handoff from the standalone launcher to the Vibe profile. */
public final class LauncherBridge {
    private static volatile boolean gameVisible;
    private LauncherBridge() { }

    public static Properties read(File minecraftDirectory) {
        Properties values = new Properties();
        Path file = file(minecraftDirectory);
        if (!Files.isRegularFile(file)) return values;
        try (InputStream input = Files.newInputStream(file)) {
            values.load(input);
        } catch (Exception ignored) {
            // Optional launcher preferences must never block Vibe itself.
        }
        return values;
    }

    public static boolean gta7Requested(File minecraftDirectory) {
        return "gta7".equalsIgnoreCase(read(minecraftDirectory).getProperty("mode", ""));
    }

    /** Consumes a one-shot request to show Vibe's native Alt Manager. */
    public static boolean consumeAccountsRequest(File minecraftDirectory) {
        return consume(minecraftDirectory, "accounts");
    }

    /** Prevent world-selection cancellation from continually reopening the GTA7 route. */
    public static boolean consumeGta7Request(File minecraftDirectory) {
        return consume(minecraftDirectory, "gta7");
    }

    /** Clear only the one-shot game route after GTA7 accepts it. */
    public static void clearGta7Request(File minecraftDirectory) {
        consume(minecraftDirectory, "gta7");
    }

    /** Lets run.bat close its visible handoff console only after a client GUI is rendered. */
    public static void markGameVisible(File minecraftDirectory) {
        if (gameVisible || minecraftDirectory == null) return;
        gameVisible = true;
        try {
            Path ready = minecraftDirectory.toPath().resolve("vibe").resolve("launcher").resolve("game-visible");
            Files.createDirectories(ready.getParent());
            Files.write(ready, new byte[] { 'o', 'k' });
        } catch (Exception ignored) {
            // The game must remain usable even if the optional launcher
            // handoff marker cannot be written.
        }
    }

    private static boolean consume(File minecraftDirectory, String expectedMode) {
        Properties values = read(minecraftDirectory);
        if (!expectedMode.equalsIgnoreCase(values.getProperty("mode", ""))) return false;
        values.setProperty("mode", "vibe");
        Path file = file(minecraftDirectory);
        try {
            Files.createDirectories(file.getParent());
            try (OutputStream output = Files.newOutputStream(file)) {
                values.store(output, "Vibe Launcher bridge - no credentials");
            }
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static Path file(File minecraftDirectory) {
        return minecraftDirectory.toPath().resolve("vibe").resolve("launcher.properties");
    }
}

package dev.vibe.ui;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.minecraft.client.Minecraft;

/** Local colour preference shared by the menu and account screens. */
final class MenuThemes {
    enum Preset {
        LAVENDER("Lavender", 0xFFB4A0FF, 0xFF111218, 0xFF191B24, 0xFF9398AD),
        OCEAN("Ocean", 0xFF79BFFF, 0xFF10151C, 0xFF18222D, 0xFF91A4B8),
        MINT("Mint", 0xFF7DDDC3, 0xFF101817, 0xFF182522, 0xFF91AAA3),
        ROSE("Rose", 0xFFF0A0BA, 0xFF191216, 0xFF281C23, 0xFFB099A5),
        AMBER("Amber", 0xFFE8BF7A, 0xFF191611, 0xFF272219, 0xFFAEA28D),
        GRAPHITE("Graphite", 0xFFBEC5D0, 0xFF131416, 0xFF202226, 0xFF9A9FA8);

        final String label;
        final int accent, background, surface, muted;
        Preset(String label, int accent, int background, int surface, int muted) {
            this.label = label; this.accent = accent; this.background = background; this.surface = surface; this.muted = muted;
        }
    }

    private static Preset selected = read();

    private MenuThemes() { }
    static Preset current() { return selected; }
    private static Path file() { return Minecraft.getMinecraft().mcDataDir.toPath().resolve("vibe/menu.properties"); }

    private static Preset read() {
        if (Files.isRegularFile(file())) {
            try (InputStream input = Files.newInputStream(file())) {
                Properties settings = new Properties();
                settings.load(input);
                return Preset.valueOf(settings.getProperty("theme", "LAVENDER"));
            } catch (Exception ignored) { }
        }
        return Preset.LAVENDER;
    }

    static boolean select(Preset preset) {
        selected = preset;
        AccountScreenStyle.applyTheme(preset);
        try {
            Files.createDirectories(file().getParent());
            Properties settings = new Properties();
            settings.setProperty("theme", preset.name());
            try (OutputStream output = Files.newOutputStream(file())) { settings.store(output, "Vibe menu colours"); }
            return true;
        } catch (Exception ignored) { return false; }
    }
}

package dev.vibe.ui.menu;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Menu colours available before Minecraft, its resource manager or fonts are initialized. */
public enum MenuPalette {
    LAVENDER("Lavender", 0xFFB4A0FF, 0xFF111218, 0xFF191B24, 0xFFA8A4BA, 0xFF8751DF, 0xFFD7BCFF),
    OCEAN("Ocean", 0xFF79BFFF, 0xFF10151C, 0xFF18222D, 0xFFA0B4C8, 0xFF087DBB, 0xFF91E2FF),
    MINT("Mint", 0xFF7DDDC3, 0xFF101817, 0xFF182522, 0xFFA0BCB1, 0xFF18A67C, 0xFFA3F5D0),
    ROSE("Rose", 0xFFF0A0BA, 0xFF191216, 0xFF281C23, 0xFFC0A7B3, 0xFFCC4F82, 0xFFFFB8D1),
    AMBER("Amber", 0xFFE8BF7A, 0xFF191611, 0xFF272219, 0xFFC1B29C, 0xFFC18623, 0xFFFFD58C),
    GRAPHITE("Graphite", 0xFFBEC5D0, 0xFF131416, 0xFF202226, 0xFFB0B5BD, 0xFF64707D, 0xFFCAD1D9);

    public final String label;
    public final int accent, background, surface, muted, lava, light;

    MenuPalette(String label, int accent, int background, int surface, int muted, int lava, int light) {
        this.label = label; this.accent = accent; this.background = background;
        this.surface = surface; this.muted = muted; this.lava = lava; this.light = light;
    }

    public static MenuPalette read(Path gameDirectory) {
        Path file = gameDirectory.resolve("vibe/menu.properties");
        try (InputStream input = Files.newInputStream(file)) {
            Properties settings = new Properties(); settings.load(input);
            return valueOf(settings.getProperty("theme", "OCEAN"));
        } catch (Exception ignored) { return OCEAN; }
    }
}

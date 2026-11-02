package dev.vibe.ui.menu;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.minecraft.client.Minecraft;

/** Local colour preference shared by the menu and account screens. */
final class MenuThemes {
    private static MenuPalette selected = MenuPalette.read(Minecraft.getMinecraft().mcDataDir.toPath());

    private MenuThemes() { }
    static MenuPalette current() { return selected; }
    private static Path file() { return Minecraft.getMinecraft().mcDataDir.toPath().resolve("vibe/menu.properties"); }

    static boolean select(MenuPalette preset) {
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

package dev.vibe.launcher.game;

/** The six colour presets shared with Vibe's main menu ({@code dev.vibe.ui.MenuThemes}). */
public enum Theme {
    LAVENDER("Lavender", 0xB4A0FF, 0x111218, 0x191B24, 0x9398AD),
    OCEAN("Ocean", 0x79BFFF, 0x10151C, 0x18222D, 0x91A4B8),
    MINT("Mint", 0x7DDDC3, 0x101817, 0x182522, 0x91AAA3),
    ROSE("Rose", 0xF0A0BA, 0x191216, 0x281C23, 0xB099A5),
    AMBER("Amber", 0xE8BF7A, 0x191611, 0x272219, 0xAEA28D),
    GRAPHITE("Graphite", 0xBEC5D0, 0x131416, 0x202226, 0x9A9FA8);

    public final String label;
    public final int accent, background, surface, muted;

    Theme(String label, int accent, int background, int surface, int muted) {
        this.label = label;
        this.accent = accent;
        this.background = background;
        this.surface = surface;
        this.muted = muted;
    }

    public static Theme parse(String name) {
        if (name != null) for (Theme theme : values()) if (theme.name().equalsIgnoreCase(name.trim())) return theme;
        return LAVENDER;
    }
}

package dev.vibe.launcher.game;

/** What Vibe opens after its main menu has loaded; written to the launcher bridge as {@code mode}. */
public enum LaunchMode {
    VIBE("vibe", "Vibe", "Forge 1.8.9 + OptiFine"),
    GTA7("gta7", "GTA7", "Opens the GTA7 city"),
    GTA8("gta8", "GTA8: Los Vibes", "Opens the Los Vibes city"),
    /** Not selectable as a play mode: opens Vibe's Alt Manager for sign-in. */
    ACCOUNTS("accounts", "Alt Manager", "Opens Vibe's account manager");

    public final String bridgeValue, label, description;

    LaunchMode(String bridgeValue, String label, String description) {
        this.bridgeValue = bridgeValue;
        this.label = label;
        this.description = description;
    }

    public static LaunchMode[] playable() { return new LaunchMode[] { VIBE, GTA7, GTA8 }; }

    public static LaunchMode parse(String name) {
        for (LaunchMode mode : playable()) if (mode.name().equalsIgnoreCase(name)) return mode;
        return VIBE;
    }
}

package dev.vibe.model;

/** What a custom model replaces; each kind has its own folder below {@code .minecraft/vibe/models}. */
public enum ModelKind {
    SWORDS("swords"),
    PLAYERS("players");

    public final String id;

    ModelKind(String id) {
        this.id = id;
    }

    public static ModelKind byId(String id) {
        if (id == null) return null;
        String value = id.trim().toLowerCase(java.util.Locale.ROOT);
        for (ModelKind kind : values()) {
            if (kind.id.equals(value) || kind.id.equals(value + "s")) return kind;
        }
        if (value.equals("sword") || value.equals("knife") || value.equals("knives") || value.equals("items")) return SWORDS;
        if (value.equals("player") || value.equals("playermodel") || value.equals("character") || value.equals("characters")) return PLAYERS;
        return null;
    }
}

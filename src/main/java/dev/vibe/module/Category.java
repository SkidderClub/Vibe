package dev.vibe.module;

import dev.vibe.language.LanguageManager;

public enum Category {
    COMBAT("Combat"),
    VISUAL("Visual"),
    MOVEMENT("Movement"),
    WORLD("World"),
    MEME("Meme"),
    CLIENT("Client"),
    /**
     * Runtime Java scripts are deliberately kept separate from Client tools.
     * This mirrors Raven BS's Scripts workspace and prevents user scripts
     * from being lost among Vibe's built-in modules.
     */
    SCRIPTS("Scripts");

    private final String label;

    Category(String label) {
        this.label = label;
    }

    public String getLabel() {
        return LanguageManager.translate(label);
    }
}

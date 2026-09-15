package dev.vibe.module.impl;

import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.setting.ModeSetting;
import org.lwjgl.input.Keyboard;

/** Persistent client language selection used by Vibe's local UI. */
public final class LanguageModule extends Module {

    private final ModeSetting language = addSetting(new ModeSetting("Language", "English",
            "English", "Chinese", "Russian", "Japanese", "Bavarian"));

    public LanguageModule() {
        super("Language", "Choose Vibe's interface language", Category.CLIENT, Keyboard.KEY_NONE);
        setEnabled(true);
    }

    public ModeSetting getLanguage() { return language; }

    @Override public boolean isToggleable() { return false; }
}

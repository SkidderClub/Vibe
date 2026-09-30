package dev.vibe.ui.menu;

import dev.vibe.Vibe;
import dev.vibe.language.LanguageManager;
import dev.vibe.module.impl.client.LanguageModule;

/** Keeps the persistent identity and the active language module in sync. */
public final class LanguageSelector {
    private LanguageSelector() { }

    public static void select(String language) {
        Vibe vibe = Vibe.getInstance();
        if (vibe == null) return;
        String selected = LanguageManager.normalizeLanguage(language);
        if (vibe.getIdentity() != null) vibe.getIdentity().setLanguage(selected);
        if (vibe.getModuleManager() == null) return;
        LanguageModule module = vibe.getModuleManager().getModule(LanguageModule.class);
        if (module != null) module.getLanguage().setValue(selected);
        if (vibe.getConfig() != null) vibe.getConfig().save(vibe.getModuleManager());
    }
}

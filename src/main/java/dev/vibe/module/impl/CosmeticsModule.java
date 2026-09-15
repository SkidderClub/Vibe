package dev.vibe.module.impl;

import dev.vibe.module.Category;
import dev.vibe.module.Module;
import org.lwjgl.input.Keyboard;

/** Enables the active preset's client-side cosmetics. Presets live in Cosmetics Editor. */
public final class CosmeticsModule extends Module {
    public CosmeticsModule() {
        super("Cosmetics", "Render the selected local and friend cosmetic presets", Category.VISUAL, Keyboard.KEY_NONE);
    }
}

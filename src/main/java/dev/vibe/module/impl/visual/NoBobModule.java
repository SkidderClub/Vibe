package dev.vibe.module.impl.visual;

import dev.vibe.features.FeatureModule;
import dev.vibe.module.Category;
import dev.vibe.module.Module;
import org.lwjgl.input.Keyboard;

public final class NoBobModule extends Module {
    public NoBobModule() {
        super("NoBob", "Suppress walking camera bobbing", Category.VISUAL, Keyboard.KEY_NONE);
    }

    public static boolean suppressHook() {
        NoBobModule module = FeatureModule.module(NoBobModule.class);
        return module != null && module.isEnabled();
    }
}

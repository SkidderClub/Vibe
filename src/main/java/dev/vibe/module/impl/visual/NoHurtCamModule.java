package dev.vibe.module.impl.visual;

import dev.vibe.Vibe;
import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.module.impl.meme.HypixelModule;
import org.lwjgl.input.Keyboard;

/** Skips only the camera effect; damage, knockback and entity hurt timers stay untouched. */
public final class NoHurtCamModule extends Module {
    public NoHurtCamModule() {
        super("NoHurtCam", "Remove the camera tilt when taking damage", Category.VISUAL, Keyboard.KEY_NONE);
    }

    public static boolean suppressHook() {
        Vibe vibe = Vibe.getInstance();
        if (vibe == null || vibe.getModuleManager() == null) return false;
        NoHurtCamModule module = vibe.getModuleManager().getModule(NoHurtCamModule.class);
        return module != null && module.isEnabled() && !HypixelModule.visualsSuppressed();
    }
}

package dev.vibe.module.impl;

import dev.vibe.Vibe;
import dev.vibe.module.Category;
import dev.vibe.module.Module;
import org.lwjgl.input.Keyboard;

public final class FullBrightModule extends Module {

    private float previousGamma = -1.0F;

    public FullBrightModule() {
        super("FullBright", "Maximise local brightness", Category.VISUAL, Keyboard.KEY_NONE);
    }

    @Override
    protected void onEnable() {
        if (Vibe.getInstance().getMinecraft().gameSettings == null) {
            return;
        }
        previousGamma = Vibe.getInstance().getMinecraft().gameSettings.gammaSetting;
        Vibe.getInstance().getMinecraft().gameSettings.gammaSetting = 1000.0F;
    }

    @Override
    protected void onDisable() {
        if (previousGamma >= 0.0F && Vibe.getInstance().getMinecraft().gameSettings != null) {
            Vibe.getInstance().getMinecraft().gameSettings.gammaSetting = previousGamma;
        }
        previousGamma = -1.0F;
    }
}

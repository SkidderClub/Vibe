package dev.vibe.module.impl;

import dev.vibe.Vibe;
import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.setting.NumberSetting;
import org.lwjgl.input.Keyboard;

public final class FovChangerModule extends Module {

    private final NumberSetting fov = addSetting(new NumberSetting("FOV", 105.0D, 30.0D, 130.0D, 1.0D));
    private float previousFov = -1.0F;

    public FovChangerModule() {
        super("FOV Changer", "Set a custom client field of view", Category.VISUAL, Keyboard.KEY_NONE);
    }

    public void tick() {
        if (isEnabled() && Vibe.getInstance().getMinecraft().gameSettings != null) {
            Vibe.getInstance().getMinecraft().gameSettings.fovSetting = HypixelModule.visualsSuppressed() && previousFov >= 0 ? previousFov : fov.getFloat();
        }
    }

    @Override
    protected void onEnable() {
        previousFov = Vibe.getInstance().getMinecraft().gameSettings.fovSetting;
    }

    @Override
    protected void onDisable() {
        if (previousFov >= 0.0F && Vibe.getInstance().getMinecraft().gameSettings != null) {
            Vibe.getInstance().getMinecraft().gameSettings.fovSetting = previousFov;
        }
        previousFov = -1.0F;
    }

    public NumberSetting getFov() {
        return fov;
    }
}

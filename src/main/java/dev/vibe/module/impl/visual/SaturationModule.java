package dev.vibe.module.impl.visual;

import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.setting.NumberSetting;
import dev.vibe.ui.effect.SaturationRenderer;
import org.lwjgl.input.Keyboard;

/** RavenBS's independent post-process saturation pass. */
public final class SaturationModule extends Module {
    private final NumberSetting saturation = addSetting(new NumberSetting("Saturation", 1.0D, -1.0D, 5.0D, 0.05D));

    public SaturationModule() {
        super("Saturation", "Adjust world colour saturation", Category.VISUAL, Keyboard.KEY_NONE);
    }

    @Override
    protected void onEnable() {
        SaturationRenderer.update(true, saturation.getFloat());
    }

    @Override
    protected void onDisable() {
        SaturationRenderer.update(false, saturation.getFloat());
    }

    /** Recreates a lost shader after a world/display change and updates its uniform. */
    public void tick() {
        SaturationRenderer.update(isEnabled(), saturation.getFloat());
    }

    public NumberSetting getSaturation() { return saturation; }
}

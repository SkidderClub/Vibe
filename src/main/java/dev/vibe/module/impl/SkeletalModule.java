package dev.vibe.module.impl;

import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.setting.BooleanSetting;
import dev.vibe.setting.ColorSetting;
import dev.vibe.setting.NumberSetting;
import org.lwjgl.input.Keyboard;

/** Draws a pose-aware wire skeleton for visible player models. */
public final class SkeletalModule extends Module {

    private final ColorSetting color = addSetting(new ColorSetting("Color", 0xFF2DE2C2));
    private final BooleanSetting rainbow = addSetting(new BooleanSetting("Rainbow", false));
    private final NumberSetting lineWidth = addSetting(new NumberSetting("Line Width", 2.0D, 1.0D, 5.0D, 0.5D));
    private final BooleanSetting onlyTargets = addSetting(new BooleanSetting("Only Targets", false));
    private final BooleanSetting throughWalls = addSetting(new BooleanSetting("Through Walls", true));
    private final BooleanSetting depthBackplate = addSetting(new BooleanSetting("Depth Backplate", true));

    public SkeletalModule() {
        super("Skeletal", "Render animated player skeletons", Category.VISUAL, Keyboard.KEY_NONE);
    }

    public ColorSetting getColor() { return color; }
    public BooleanSetting getRainbow() { return rainbow; }
    public NumberSetting getLineWidth() { return lineWidth; }
    public BooleanSetting getOnlyTargets() { return onlyTargets; }
    public BooleanSetting getThroughWalls() { return throughWalls; }
    public BooleanSetting getDepthBackplate() { return depthBackplate; }
}

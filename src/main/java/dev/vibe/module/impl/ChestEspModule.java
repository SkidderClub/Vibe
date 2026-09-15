package dev.vibe.module.impl;

import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.setting.BooleanSetting;
import dev.vibe.setting.ColorSetting;
import dev.vibe.setting.NumberSetting;
import org.lwjgl.input.Keyboard;

public final class ChestEspModule extends Module {

    private final BooleanSetting normal = addSetting(new BooleanSetting("Normal Chests", true));
    private final ColorSetting normalOutline = addSetting(new ColorSetting("Normal Outline", 0xFFFFD166,
            () -> normal.isEnabled()));
    private final ColorSetting normalFill = addSetting(new ColorSetting("Normal Fill", 0x35FFD166,
            () -> normal.isEnabled()));
    private final BooleanSetting redstone = addSetting(new BooleanSetting("Redstone Chests", true));
    private final ColorSetting redstoneOutline = addSetting(new ColorSetting("Redstone Outline", 0xFFFF5B6E,
            () -> redstone.isEnabled()));
    private final ColorSetting redstoneFill = addSetting(new ColorSetting("Redstone Fill", 0x35FF5B6E,
            () -> redstone.isEnabled()));
    private final BooleanSetting ender = addSetting(new BooleanSetting("Ender Chests", true));
    private final ColorSetting enderOutline = addSetting(new ColorSetting("Ender Outline", 0xFFB388FF,
            () -> ender.isEnabled()));
    private final ColorSetting enderFill = addSetting(new ColorSetting("Ender Fill", 0x35B388FF,
            () -> ender.isEnabled()));
    private final NumberSetting lineWidth = addSetting(new NumberSetting("Line Width", 1.5D, 1.0D, 5.0D, 0.5D));
    private final NumberSetting maxDistance = addSetting(new NumberSetting("Max Distance", 96.0D, 8.0D, 256.0D, 1.0D));
    private final BooleanSetting colorFade = addSetting(new BooleanSetting("Distance Color Fade", false));
    private final ColorSetting nearColor = addSetting(new ColorSetting("Near Color", 0xFF2DE2C2,
            () -> colorFade.isEnabled()));
    private final ColorSetting farColor = addSetting(new ColorSetting("Far Color", 0xFFA855F7,
            () -> colorFade.isEnabled()));
    private final BooleanSetting fadeAlpha = addSetting(new BooleanSetting("Fade Alpha At Max", true));

    public ChestEspModule() {
        super("ChestESP", "Highlight normal, redstone, and ender chests", Category.VISUAL, Keyboard.KEY_NONE);
    }

    public BooleanSetting getNormal() { return normal; }
    public ColorSetting getNormalOutline() { return normalOutline; }
    public ColorSetting getNormalFill() { return normalFill; }
    public BooleanSetting getRedstone() { return redstone; }
    public ColorSetting getRedstoneOutline() { return redstoneOutline; }
    public ColorSetting getRedstoneFill() { return redstoneFill; }
    public BooleanSetting getEnder() { return ender; }
    public ColorSetting getEnderOutline() { return enderOutline; }
    public ColorSetting getEnderFill() { return enderFill; }
    public NumberSetting getLineWidth() { return lineWidth; }
    public NumberSetting getMaxDistance() { return maxDistance; }
    public BooleanSetting getColorFade() { return colorFade; }
    public ColorSetting getNearColor() { return nearColor; }
    public ColorSetting getFarColor() { return farColor; }
    public BooleanSetting getFadeAlpha() { return fadeAlpha; }
}

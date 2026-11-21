package dev.vibe.module.impl.visual;

import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.setting.BooleanSetting;
import dev.vibe.setting.ColorSetting;
import dev.vibe.setting.ModeSetting;
import dev.vibe.setting.NumberSetting;
import org.lwjgl.input.Keyboard;

public final class ChestEspModule extends Module {

    public enum Type {
        NORMAL("Normal"), TRAPPED("Trapped"), ENDER("Ender");

        public final String label;
        Type(String label) { this.label = label; }
    }

    private final ModeSetting mode = addSetting(new ModeSetting("Mode", "3D", "3D", "2D"));
    private final ChestStyle normal = new ChestStyle("Normal", 0xFFFFD166, 0x35FFD166);
    private final ChestStyle redstone = new ChestStyle("Redstone", 0xFFFF5B6E, 0x35FF5B6E);
    private final ChestStyle ender = new ChestStyle("Ender", 0xFFB388FF, 0x35B388FF);
    private final ModeSetting boxStyle = addSetting(new ModeSetting("2D Box Style", "Corners", this::is2D, "Corners", "Full box"));
    private final NumberSetting cornerLength = addSetting(new NumberSetting("Corner Length", .25, .05, .5, .01,
            () -> is2D() && boxStyle.is("Corners")));
    private final NumberSetting rounding = addSetting(new NumberSetting("Corner Radius", 0, 0, 8, .25, this::is2D));
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

    public ModeSetting getMode() { return mode; }
    public boolean is2D() { return mode.is("2D"); }
    public ModeSetting getBoxStyle() { return boxStyle; }
    public NumberSetting getCornerLength() { return cornerLength; }
    public NumberSetting getRounding() { return rounding; }
    public BooleanSetting getNormal() { return normal.enabled; }
    public ColorSetting getNormalOutline() { return normal.outline; }
    public ColorSetting getNormalFill() { return normal.fill; }
    public BooleanSetting getRedstone() { return redstone.enabled; }
    public ColorSetting getRedstoneOutline() { return redstone.outline; }
    public ColorSetting getRedstoneFill() { return redstone.fill; }
    public BooleanSetting getEnder() { return ender.enabled; }
    public ColorSetting getEnderOutline() { return ender.outline; }
    public ColorSetting getEnderFill() { return ender.fill; }
    public NumberSetting getLineWidth() { return lineWidth; }
    public NumberSetting getMaxDistance() { return maxDistance; }
    public BooleanSetting getColorFade() { return colorFade; }
    public ColorSetting getNearColor() { return nearColor; }
    public ColorSetting getFarColor() { return farColor; }
    public BooleanSetting getFadeAlpha() { return fadeAlpha; }

    public ChestStyle getStyle(Type type) {
        switch (type) {
            case NORMAL: return normal;
            case TRAPPED: return redstone;
            case ENDER: return ender;
            default: throw new AssertionError(type);
        }
    }

    public final class ChestStyle {
        public final BooleanSetting enabled;
        public final ColorSetting outline, fill;

        private ChestStyle(String prefix, int lineColor, int fillColor) {
            enabled = addSetting(new BooleanSetting(prefix + " Chests", true));
            outline = addSetting(new ColorSetting(prefix + " Outline", lineColor, enabled::isEnabled));
            fill = addSetting(new ColorSetting(prefix + " Fill", fillColor, enabled::isEnabled));
        }
    }
}

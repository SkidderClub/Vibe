package dev.vibe.module.impl.visual;

import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.setting.*;
import dev.vibe.ui.RenderUtils;
import java.awt.Color;
import org.lwjgl.input.Keyboard;

/** First-person bloom, shared by vanilla held items and the VibeOS offhand. */
public final class ItemGlowModule extends Module {
    private final long visualStarted = System.nanoTime();
    public final BooleanSetting heldItem = addSetting(new BooleanSetting("Held Item", true));
    public final BooleanSetting phone = addSetting(new BooleanSetting("Phone", true));
    public final BooleanSetting emptyHand = addSetting(new BooleanSetting("Empty Hand", false, () -> heldItem.isEnabled()));
    public final ModeSetting colorMode = addSetting(new ModeSetting("Color Mode", "Gradient", "Static", "Gradient", "Rainbow"));
    public final ColorSetting color = addSetting(new ColorSetting("Color", 0xFF73DCCF, () -> !colorMode.is("Rainbow")));
    public final ColorSetting secondaryColor = addSetting(new ColorSetting("Secondary Color", 0xFFAA91FF, () -> colorMode.is("Gradient")));
    public final NumberSetting colorSpeed = addSetting(new NumberSetting("Color Speed", .3, 0, 2, .05, () -> !colorMode.is("Static")));
    public final NumberSetting radius = addSetting(new NumberSetting("Radius", 10, 2, 24, 1));
    public final NumberSetting intensity = addSetting(new NumberSetting("Intensity", .85, 0, 2.5, .05));
    public final NumberSetting opacity = addSetting(new NumberSetting("Opacity", .8, 0, 1, .05));
    public final NumberSetting falloff = addSetting(new NumberSetting("Falloff", 1.4, .5, 3, .1));
    public final NumberSetting innerGlow = addSetting(new NumberSetting("Inner Glow", 0, 0, .4, .02));
    public final ModeSetting innerMode = addSetting(new ModeSetting("Inner Mode", "Tint", "Tint", "Soft", "Rim", "Pulse", "Scan"));
    public final NumberSetting innerSpeed = addSetting(new NumberSetting("Inner Speed", .35, 0, 3, .05,
            () -> innerGlow.getDouble() > 0 && (innerMode.is("Pulse") || innerMode.is("Scan"))));
    public final NumberSetting innerWidth = addSetting(new NumberSetting("Inner Width", .12, .03, .5, .01,
            () -> innerGlow.getDouble() > 0 && innerMode.is("Scan")));
    public final BooleanSetting fade = addSetting(new BooleanSetting("Fade", true));
    public final NumberSetting fadeSpeed = addSetting(new NumberSetting("Fade Speed", .6, .1, 3, .1, () -> fade.isEnabled()));
    public final NumberSetting minimumGlow = addSetting(new NumberSetting("Minimum Glow", .65, 0, 1, .05, () -> fade.isEnabled()));
    public final ModeSetting quality = addSetting(new ModeSetting("Quality", "Balanced", "Performance", "Balanced", "High"));

    public ItemGlowModule() { super("ItemGlow", "Soft customizable glow around held items and the Phone", Category.VISUAL, Keyboard.KEY_NONE); }

    public float fadeAt(double seconds) {
        if (!fade.isEnabled()) return 1;
        float wave = (float) (.5 + .5 * Math.sin(seconds * Math.PI * 2 * fadeSpeed.getDouble()));
        return minimumGlow.getFloat() + (1 - minimumGlow.getFloat()) * wave;
    }
    public int colorAt(double seconds, float position) {
        if (colorMode.is("Rainbow")) return Color.HSBtoRGB((float) (seconds * colorSpeed.getDouble() * .1 + position * .12) % 1, .58F, 1);
        if (colorMode.is("Static")) return color.getArgb();
        float mix = Math.max(0, Math.min(1, position + (float) Math.sin(seconds * colorSpeed.getDouble()) * .2F));
        return RenderUtils.blend(color.getArgb(), secondaryColor.getArgb(), mix);
    }
    public int downsample() { return quality.is("Performance") ? 4 : quality.is("High") ? 2 : 3; }
    public int blurLimit() { return quality.is("Performance") ? 640 : quality.is("High") ? 1440 : 960; }
    public int innerModeIndex() { return innerMode.is("Soft") ? 1 : innerMode.is("Rim") ? 2 : innerMode.is("Pulse") ? 3 : innerMode.is("Scan") ? 4 : 0; }
    public float innerPhaseAt(double seconds) { double phase = seconds * innerSpeed.getDouble(); return (float) (phase - Math.floor(phase)); }
    public double seconds() { return (System.nanoTime() - visualStarted) / 1.0e9; }
    @Override protected void onDisable() { dev.vibe.ui.render.ItemGlowRenderer.requestRelease(); }
}

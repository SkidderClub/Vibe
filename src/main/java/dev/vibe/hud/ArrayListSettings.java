package dev.vibe.hud;

import dev.vibe.setting.*;
import java.util.*;
import java.util.function.Consumer;

/** Registered on HUD so profiles and every ClickGUI share the same controls. */
public final class ArrayListSettings {
    public final ModeSetting preset = new ModeSetting("Array Preset", "Custom", "Custom", "Rose Cards", "Purple Rail", "Rounded White", "Green Fade", "Pixel Outline", "Dark Teal");
    public final ModeSetting font = new ModeSetting("Array Font", "Minecraft", "Minecraft", "Smooth", "Smooth Bold");
    public final NumberSetting scale = new NumberSetting("Array Text Scale", 1, .6, 2, .05);
    public final ModeSetting casing = new ModeSetting("Array Name Case", "Original", "Original", "Lowercase", "Uppercase");
    public final BooleanSetting spaces = new BooleanSetting("Array Name Spaces", true);
    public final BooleanSetting bold = new BooleanSetting("Array Bold", false, () -> font.is("Minecraft"));
    public final BooleanSetting shadow = new BooleanSetting("Array Text Shadow", true);
    public final ModeSetting suffix = new ModeSetting("Array Suffix", "Mode", "None", "Mode", "All Modes");
    public final ModeSetting separator = new ModeSetting("Array Suffix Separator", "Space", () -> !suffix.is("None"), "Space", "Dash", "Brackets", "Parentheses");
    public final ColorSetting suffixColor = new ColorSetting("Array Suffix Color", 0xFFBBBBBB, () -> !suffix.is("None"));
    public final BooleanSetting suffixAccent = new BooleanSetting("Array Suffix Accent", false, () -> !suffix.is("None"));
    public final ModeSetting sorting = new ModeSetting("Array Sort", "Width", "Width", "Alphabetical", "Category");
    public final ModeSetting alignment = new ModeSetting("Array Alignment", "Auto", "Auto", "Left", "Right");
    public final NumberSetting rowHeight = new NumberSetting("Array Row Height", 12, 9, 24, .5);
    public final NumberSetting gap = new NumberSetting("Array Row Gap", 0, 0, 8, .5);
    public final NumberSetting padding = new NumberSetting("Array Padding", 4, 0, 12, .5);
    public final ModeSetting background = new ModeSetting("Array Background Shape", "Steps", "None", "Steps", "Rectangle");
    public final NumberSetting radius = new NumberSetting("Array Corner Radius", 0, 0, 8, .5);
    public final ModeSetting rail = new ModeSetting("Array Accent Rail", "Outer", "None", "Outer", "Inner", "Both");
    public final NumberSetting railWidth = new NumberSetting("Array Rail Width", 1, .5, 4, .5, () -> !rail.is("None"));
    public final ModeSetting outline = new ModeSetting("Array Outline Shape", "Steps", "Steps", "Rectangle", "Rows");
    public final BooleanSetting outlineAccent = new BooleanSetting("Array Outline Accent", true);
    public final ColorSetting outlineColor = new ColorSetting("Array Outline Color", 0xFF2DE2C2, () -> !outlineAccent.isEnabled());
    public final NumberSetting outlineWidth = new NumberSetting("Array Outline Width", 1, .5, 3, .5);
    public final ModeSetting color = new ModeSetting("Array Color Mode", "Wave", "Static", "Gradient", "Wave", "Rainbow", "Fade");
    public final NumberSetting speed = new NumberSetting("Array Color Speed", 1, 0, 5, .05);
    public final NumberSetting spread = new NumberSetting("Array Color Spread", 1, .1, 4, .1);
    public final BooleanSetting horizontal = new BooleanSetting("Array Horizontal Gradient", false);
    public final NumberSetting saturation = new NumberSetting("Array Rainbow Saturation", .8, 0, 1, .05, () -> color.is("Rainbow"));
    public final NumberSetting brightness = new NumberSetting("Array Rainbow Brightness", 1, .1, 1, .05, () -> color.is("Rainbow"));
    public final NumberSetting fade = new NumberSetting("Array Bottom Fade", 0, 0, 1, .05);
    public final NumberSetting opacity = new NumberSetting("Array Opacity", 1, .05, 1, .05);
    public final BooleanSetting glow = new BooleanSetting("Array Glow", false);
    public final NumberSetting glowRadius = new NumberSetting("Array Glow Radius", 5, 1, 12, 1, () -> glow.isEnabled());
    public final NumberSetting glowStrength = new NumberSetting("Array Glow Strength", .5, .05, 1, .05, () -> glow.isEnabled());
    public final BooleanSetting textGlow = new BooleanSetting("Array Text Glow", false);
    public final NumberSetting textGlowRadius = new NumberSetting("Array Text Glow Radius", 1.5, .5, 4, .5, () -> textGlow.isEnabled());
    public final NumberSetting textGlowStrength = new NumberSetting("Array Text Glow Strength", .35, .05, 1, .05, () -> textGlow.isEnabled());
    public final List<Setting<?>> all;

    public ArrayListSettings(Consumer<Setting<?>> register) {
        all = Collections.unmodifiableList(Arrays.<Setting<?>>asList(preset, font, scale, casing, spaces, bold, shadow,
                suffix, separator, suffixColor, suffixAccent, sorting, alignment, rowHeight, gap, padding,
                background, radius, rail, railWidth, outline, outlineAccent, outlineColor, outlineWidth,
                color, speed, spread, horizontal, saturation, brightness, fade, opacity, glow, glowRadius,
                glowStrength, textGlow, textGlowRadius, textGlowStrength));
        all.forEach(register);
    }
}

package dev.vibe.module.impl;

import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.setting.ColorSetting;
import dev.vibe.setting.MultiSelectSetting;
import dev.vibe.setting.NumberSetting;
import java.util.Arrays;
import org.lwjgl.input.Keyboard;

/** Per-mode combat target markers with independently tuneable gradients. */
public final class TargetEspModule extends Module {

    public static final String HELIX = "Helix";
    public static final String SIMS = "Sims";
    public static final String CIRCLE = "Circle";
    public static final String SIGMA = "Sigma";
    public static final String TIRE = "Swimming Tire";
    public static final String TRACER = "Tracer";
    public static final String HIZZY = "Hizzy";

    private final MultiSelectSetting modes = addSetting(new MultiSelectSetting("Modes",
            Arrays.asList(HELIX, SIMS, CIRCLE, SIGMA, TIRE, TRACER, HIZZY), Arrays.asList(CIRCLE)));
    private final ColorSetting helixPrimary = color("Helix Color 1", HELIX, 0xFF2DE2C2);
    private final ColorSetting helixSecondary = color("Helix Color 2", HELIX, 0xFFA855F7);
    private final ColorSetting simsPrimary = color("Sims Color 1", SIMS, 0xFF68FF9B);
    private final ColorSetting simsSecondary = color("Sims Color 2", SIMS, 0xFF16B85A);
    private final ColorSetting circlePrimary = color("Circle Color 1", CIRCLE, 0xFF2DE2C2);
    private final ColorSetting circleSecondary = color("Circle Color 2", CIRCLE, 0xFFA855F7);
    private final ColorSetting sigmaPrimary = color("Sigma Color 1", SIGMA, 0xFF2DE2C2);
    private final ColorSetting sigmaSecondary = color("Sigma Color 2", SIGMA, 0xFFA855F7);
    private final ColorSetting tirePrimary = color("Tire Color 1", TIRE, 0xFF2DE2C2);
    private final ColorSetting tireSecondary = color("Tire Color 2", TIRE, 0xFFA855F7);
    private final ColorSetting tracerPrimary = color("Tracer Color 1", TRACER, 0xFFFFFFFF);
    private final ColorSetting tracerSecondary = color("Tracer Color 2", TRACER, 0xFFFFFFFF);
    private final ColorSetting hizzyPrimary = color("Hizzy Color 1", HIZZY, 0xFFFF3BD4);
    private final ColorSetting hizzySecondary = color("Hizzy Color 2", HIZZY, 0xFF3BE7FF);
    private final NumberSetting lineWidth = addSetting(new NumberSetting("Line Width", 2.0D, 1.0D, 5.0D, 0.5D));
    private final NumberSetting speed = addSetting(new NumberSetting("Animation Speed", 1.0D, 0.1D, 4.0D, 0.1D));

    public TargetEspModule() {
        super("TargetESP", "Highlight the active AimAssist target", Category.VISUAL, Keyboard.KEY_NONE);
    }

    private ColorSetting color(String name, final String mode, int value) {
        return addSetting(new ColorSetting(name, value, () -> modes.isSelected(mode)));
    }

    public MultiSelectSetting getModes() { return modes; }
    public NumberSetting getLineWidth() { return lineWidth; }
    public NumberSetting getSpeed() { return speed; }

    public ColorSetting getPrimaryColor(String mode) {
        if (HELIX.equals(mode)) return helixPrimary;
        if (SIMS.equals(mode)) return simsPrimary;
        if (SIGMA.equals(mode)) return sigmaPrimary;
        if (TIRE.equals(mode)) return tirePrimary;
        if (TRACER.equals(mode)) return tracerPrimary;
        if (HIZZY.equals(mode)) return hizzyPrimary;
        return circlePrimary;
    }

    public ColorSetting getSecondaryColor(String mode) {
        if (HELIX.equals(mode)) return helixSecondary;
        if (SIMS.equals(mode)) return simsSecondary;
        if (SIGMA.equals(mode)) return sigmaSecondary;
        if (TIRE.equals(mode)) return tireSecondary;
        if (TRACER.equals(mode)) return tracerSecondary;
        if (HIZZY.equals(mode)) return hizzySecondary;
        return circleSecondary;
    }
}

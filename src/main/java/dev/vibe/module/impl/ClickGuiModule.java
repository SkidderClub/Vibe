package dev.vibe.module.impl;

import dev.vibe.Vibe;
import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.setting.BooleanSetting;
import dev.vibe.setting.ColorSetting;
import dev.vibe.setting.ModeSetting;
import dev.vibe.setting.NumberSetting;
import org.lwjgl.input.Keyboard;

public final class ClickGuiModule extends Module {

    // Each style renders through VibeClickGui's shared Module/Setting model,
    // so switching the look never changes bindings, values, or config data.
    private final ModeSetting theme = addSetting(new ModeSetting("Theme", "Skeet", "Skeet", "Futuristic", "NeverLose", "Augustus", "Xanax"));
    private final ColorSetting primaryColor = addSetting(new ColorSetting("Primary Color", 0xFF2DE2C2));
    private final ColorSetting secondaryColor = addSetting(new ColorSetting("Secondary Color", 0xFFA855F7));
    private final BooleanSetting augustusRoundedCorners = addSetting(new BooleanSetting("Rounded Corners", true, () -> theme.is("Augustus")));
    private final NumberSetting augustusBackgroundAlpha = addSetting(new NumberSetting("Background Alpha", 200, 0, 255, 1, () -> theme.is("Augustus")));

    public ClickGuiModule() {
        super("ClickGUI", "Open the Vibe module manager", Category.CLIENT, Keyboard.KEY_RSHIFT);
    }

    @Override
    public void toggle() {
        Vibe.getInstance().openClickGui();
    }

    public ModeSetting getTheme() { return theme; }
    public ColorSetting getPrimaryColor() { return primaryColor; }
    public ColorSetting getSecondaryColor() { return secondaryColor; }
    public BooleanSetting getAugustusRoundedCorners() { return augustusRoundedCorners; }
    public NumberSetting getAugustusBackgroundAlpha() { return augustusBackgroundAlpha; }
}

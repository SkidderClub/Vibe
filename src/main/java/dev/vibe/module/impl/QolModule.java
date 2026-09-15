package dev.vibe.module.impl;

import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.setting.MultiSelectSetting;
import dev.vibe.setting.NumberSetting;
import java.util.Arrays;
import org.lwjgl.input.Keyboard;

/** Small client-only quality-of-life presentation controls. */
public final class QolModule extends Module {

    private final MultiSelectSetting features = addSetting(new MultiSelectSetting("Features",
            Arrays.asList("Hide Black Background", "AntiInvisibility"), Arrays.asList("Hide Black Background")));
    private final MultiSelectSetting guiTargets = addSetting(new MultiSelectSetting("Hide In",
            Arrays.asList("Inventory", "ESC", "Other Vanilla GUIs"),
            Arrays.asList("Inventory", "ESC", "Other Vanilla GUIs"),
            () -> features.isSelected("Hide Black Background")));
    private final NumberSetting invisibleAlpha = addSetting(new NumberSetting("Invisible Alpha", 125.0D, 0.0D, 255.0D, 1.0D,
            () -> features.isSelected("AntiInvisibility")));

    public QolModule() {
        super("QOL", "Client-side quality-of-life presentation", Category.CLIENT, Keyboard.KEY_NONE);
    }

    public MultiSelectSetting getFeatures() {
        return features;
    }

    public MultiSelectSetting getGuiTargets() {
        return guiTargets;
    }

    public NumberSetting getInvisibleAlpha() {
        return invisibleAlpha;
    }
}

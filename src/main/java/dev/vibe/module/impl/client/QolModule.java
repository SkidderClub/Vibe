package dev.vibe.module.impl.client;

import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.setting.MultiSelectSetting;
import dev.vibe.setting.NumberSetting;
import java.util.Arrays;
import org.lwjgl.input.Keyboard;

/** Small client-only quality-of-life presentation controls. */
public final class QolModule extends Module {
    public static final String REAL_REWOUND_PLAYERS="RealRewoundPlayers";

    private final MultiSelectSetting features = addSetting(new MultiSelectSetting("Features",
            Arrays.asList("Hide Black Background", "AntiInvisibility", REAL_REWOUND_PLAYERS), Arrays.asList("Hide Black Background")));
    private final MultiSelectSetting realPlayerViews=addSetting(new MultiSelectSetting("RealRewoundPlayers Views",
            Arrays.asList("ESP","Tab"),Arrays.asList("ESP","Tab"),()->features.isSelected(REAL_REWOUND_PLAYERS)));
    public MultiSelectSetting getRealPlayerViews(){return realPlayerViews;}
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

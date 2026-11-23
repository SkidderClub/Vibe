package dev.vibe.module.impl.client;

import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.setting.MultiSelectSetting;
import dev.vibe.setting.NumberSetting;
import java.util.Arrays;
import org.lwjgl.input.Keyboard;

/** Small client-only quality-of-life presentation controls. */
public final class QolModule extends Module {
    public final dev.vibe.setting.BooleanSetting dropConfirm=addSetting(new dev.vibe.setting.BooleanSetting("Drop confirm",false));
    public static final String REAL_REWOUND_PLAYERS="RealRewoundPlayers";
    public static final String ANTI_CRASH = "AntiCrash";
    public static final String VISIBLE_BARRIERS = "Visible Barriers";

    private final MultiSelectSetting features = addSetting(new MultiSelectSetting("Features",
            Arrays.asList("Hide Black Background", "AntiInvisibility", REAL_REWOUND_PLAYERS, VISIBLE_BARRIERS, ANTI_CRASH),
            Arrays.asList("Hide Black Background", ANTI_CRASH)));
    private final MultiSelectSetting realPlayerViews=addSetting(new MultiSelectSetting("RealRewoundPlayers Views",
            Arrays.asList("ESP","Tab"),Arrays.asList("ESP","Tab"),()->features.isSelected(REAL_REWOUND_PLAYERS)));
    public MultiSelectSetting getRealPlayerViews(){return realPlayerViews;}
    private final MultiSelectSetting realPlayerModes = addSetting(new MultiSelectSetting("RealRewoundPlayers Hide In",
            Arrays.asList("Skywars", "Bedwars", "Survivalgames", "Pit"), Arrays.asList("Pit"),
            () -> features.isSelected(REAL_REWOUND_PLAYERS)));
    public MultiSelectSetting getRealPlayerModes() { return realPlayerModes; }
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

    public static boolean active(String feature) {
        QolModule module = dev.vibe.features.FeatureModule.module(QolModule.class);
        return module != null && module.isEnabled() && module.features.isSelected(feature);
    }

    public static boolean barrierParticles(boolean vanilla) {
        return vanilla || active(VISIBLE_BARRIERS);
    }
}

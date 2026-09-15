package dev.vibe.module.impl;

import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.setting.ModeSetting;
import dev.vibe.setting.NumberSetting;
import dev.vibe.setting.MultiSelectSetting;
import java.util.Arrays;
import org.lwjgl.input.Keyboard;

/** Client-only first-person sword-block animation styles. */
public final class AnimationsModule extends Module {

    private final ModeSetting blockingStyle = addSetting(new ModeSetting("Blocking Style", "Vanilla",
            "Vanilla", "Slide", "Reverse", "Reverse Spin", "Spin", "Orbit"));
    private final MultiSelectSetting itemTypes = addSetting(new MultiSelectSetting("Items",
            Arrays.asList("Swords", "Bow", "Consumables", "Food", "Potions", "Other"), Arrays.asList("Swords")));
    private final NumberSetting animationSpeed = addSetting(new NumberSetting("Animation Speed", 1.0D, 0.1D, 4.0D, 0.1D,
            () -> !blockingStyle.is("Vanilla")));
    private final NumberSetting rotation = addSetting(new NumberSetting("Rotation", 35.0D, 0.0D, 180.0D, 1.0D,
            () -> !blockingStyle.is("Vanilla")));
    private final NumberSetting offsetX = addSetting(new NumberSetting("X Offset", 0.0D, -1.0D, 1.0D, 0.01D,
            () -> !blockingStyle.is("Vanilla")));
    private final NumberSetting offsetY = addSetting(new NumberSetting("Y Offset", 0.0D, -1.0D, 1.0D, 0.01D,
            () -> !blockingStyle.is("Vanilla")));
    private final NumberSetting offsetZ = addSetting(new NumberSetting("Z Offset", 0.0D, -1.0D, 1.0D, 0.01D,
            () -> !blockingStyle.is("Vanilla")));

    public AnimationsModule() {
        super("Animations", "Customize first-person sword blocking", Category.VISUAL, Keyboard.KEY_NONE);
    }

    public ModeSetting getBlockingStyle() { return blockingStyle; }
    public MultiSelectSetting getItemTypes() { return itemTypes; }
    public NumberSetting getAnimationSpeed() { return animationSpeed; }
    public NumberSetting getRotation() { return rotation; }
    public NumberSetting getOffsetX() { return offsetX; }
    public NumberSetting getOffsetY() { return offsetY; }
    public NumberSetting getOffsetZ() { return offsetZ; }
}

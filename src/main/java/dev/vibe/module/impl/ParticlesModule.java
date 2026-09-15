package dev.vibe.module.impl;

import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.setting.ColorSetting;
import dev.vibe.setting.MultiSelectSetting;
import dev.vibe.setting.NumberSetting;
import java.util.Arrays;
import org.lwjgl.input.Keyboard;

/** Ambient interactive particles shown over selected client GUIs. */
public final class ParticlesModule extends Module {

    private final MultiSelectSetting guiTargets = addSetting(new MultiSelectSetting("Show In",
            Arrays.asList("ClickGUI", "Inventory", "Inventory Editor", "Friend Editor", "Config Editor", "Keybind Editor", "ESP Editor", "NES Emulator", "Cosmetics Editor", "GTA7", "Meme Games", "Chest", "Escape", "Anvil", "Dropper", "Thrower", "Other"),
            Arrays.asList("ClickGUI")));
    private final MultiSelectSetting modes = addSetting(new MultiSelectSetting("Modes",
            Arrays.asList("Dots", "Stars", "Balls", "Hearts", "Cross", "Snowflake"),
            Arrays.asList("Dots")));
    private final NumberSetting count = addSetting(new NumberSetting("Count", 52.0D, 8.0D, 500.0D, 1.0D));
    // Particles deliberately have one crisp pixel core. A configurable size
    // made the UI muddy at small GUI scales, so velocity is the meaningful
    // visual control instead.
    private final NumberSetting speed = addSetting(new NumberSetting("Speed", 1.35D, 0.1D, 8.0D, 0.05D));
    private final NumberSetting interaction = addSetting(new NumberSetting("Mouse Force", 3.0D, 0.1D, 16.0D, 0.05D));
    private final ColorSetting primary = addSetting(new ColorSetting("Primary Color", 0xD82DE2C2));
    private final ColorSetting secondary = addSetting(new ColorSetting("Secondary Color", 0xD8A855F7));

    public ParticlesModule() {
        super("Particles", "Interactive ambient particles in selected GUIs", Category.CLIENT, Keyboard.KEY_NONE);
    }

    public MultiSelectSetting getGuiTargets() { return guiTargets; }
    public MultiSelectSetting getModes() { return modes; }
    public NumberSetting getCount() { return count; }
    public NumberSetting getSpeed() { return speed; }
    public NumberSetting getInteraction() { return interaction; }
    public ColorSetting getPrimary() { return primary; }
    public ColorSetting getSecondary() { return secondary; }
}

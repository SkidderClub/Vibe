package dev.vibe.module.impl;

import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.setting.BooleanSetting;
import dev.vibe.setting.ColorSetting;
import dev.vibe.setting.NumberSetting;
import org.lwjgl.input.Keyboard;

/** Highlights dropped item entities, with an inventory-aware smart filter. */
public final class ItemEspModule extends Module {
    private final ColorSetting color = addSetting(new ColorSetting("Color", 0xFF2DE2C2));
    private final BooleanSetting outline = addSetting(new BooleanSetting("Outline", true));
    private final ColorSetting outlineColor = addSetting(new ColorSetting("Outline Color", 0xFFFFFFFF,
            () -> outline.isEnabled()));
    private final NumberSetting lineWidth = addSetting(new NumberSetting("Line Width", 1.5D, 1.0D, 5.0D, 0.5D));
    private final BooleanSetting smart = addSetting(new BooleanSetting("Smart", false));

    public ItemEspModule() {
        super("ItemESP", "Highlight dropped items", Category.VISUAL, Keyboard.KEY_NONE);
    }
    public ColorSetting getColor() { return color; }
    public BooleanSetting getOutline() { return outline; }
    public ColorSetting getOutlineColor() { return outlineColor; }
    public NumberSetting getLineWidth() { return lineWidth; }
    public BooleanSetting getSmart() { return smart; }
}

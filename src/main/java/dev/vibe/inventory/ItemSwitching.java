package dev.vibe.inventory;

import dev.vibe.setting.*;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** The same controls and semantics for every automatic hotbar user. */
public final class ItemSwitching {
    public final ModeSetting mode;
    public final BooleanSetting highlight;
    public final ColorSetting color;
    public ItemSwitching(Consumer<Setting<?>> register, String defaultMode) { this(register, defaultMode, () -> true); }
    public ItemSwitching(Consumer<Setting<?>> register, String defaultMode, BooleanSupplier visible) {
        mode = new ModeSetting("Item Switching", defaultMode, visible, "Basic", "Silent");
        highlight = new BooleanSetting("Slot Highlight", true, visible);
        color = new ColorSetting("Slot Highlight Color", 0xFF2DE2C2, () -> visible.getAsBoolean() && highlight.isEnabled());
        register.accept(mode); register.accept(highlight); register.accept(color);
    }
    public boolean silent() { return mode.is("Silent"); }
}

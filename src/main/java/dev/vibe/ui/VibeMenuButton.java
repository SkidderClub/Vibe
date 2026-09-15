package dev.vibe.ui;

import net.minecraft.client.gui.GuiButton;

/** An account-style menu button that preserves vanilla IDs and click handling. */
public final class VibeMenuButton extends AccountScreenStyle.Button {

    public VibeMenuButton(GuiButton original) {
        this(original, "", false);
    }

    public VibeMenuButton(GuiButton original, String subtitle, boolean primary) {
        super(original.id, original.xPosition, original.yPosition, original.width, original.height,
                original.displayString, subtitle, primary, original.id == 4);
        this.enabled = original.enabled;
        this.visible = original.visible;
        this.packedFGColour = original.packedFGColour;
    }
}

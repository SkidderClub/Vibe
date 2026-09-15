package dev.vibe.module.impl;

import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.ui.EspEditorGui;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;

/** Opens the Counter-Strike-inspired interactive ESP layout editor. */
public final class EspEditorModule extends Module {
    public EspEditorModule() {
        super("ESP Editor", "Preview and arrange tactical ESP elements", Category.VISUAL, Keyboard.KEY_NONE);
    }
    @Override protected void onEnable() {
        if (Minecraft.getMinecraft().thePlayer == null) { setEnabled(false); return; }
        Minecraft.getMinecraft().displayGuiScreen(new EspEditorGui(this));
    }
}

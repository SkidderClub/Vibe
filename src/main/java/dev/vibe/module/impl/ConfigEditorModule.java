package dev.vibe.module.impl;

import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.ui.ConfigEditorGui;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;

/** Opens the in-game JSON profile manager. */
public final class ConfigEditorModule extends Module {
    public ConfigEditorModule() {
        super("Config Editor", "Create, load and manage Vibe JSON profiles", Category.CLIENT, Keyboard.KEY_NONE);
    }

    @Override protected void onEnable() {
        if (Minecraft.getMinecraft().thePlayer == null) { setEnabled(false); return; }
        Minecraft.getMinecraft().displayGuiScreen(new ConfigEditorGui(this));
    }
}

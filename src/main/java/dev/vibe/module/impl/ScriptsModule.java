package dev.vibe.module.impl;

import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.ui.ScriptsEditorGui;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;

/** Opens the Raven-compatible local Java script manager. */
public final class ScriptsModule extends Module {
    public ScriptsModule() {
        super("Scripts", "Create, load and manage Raven BS compatible scripts", Category.SCRIPTS, Keyboard.KEY_NONE);
    }

    @Override protected void onEnable() {
        if (isConfigLoading()) { setEnabled(false); return; }
        Minecraft.getMinecraft().displayGuiScreen(new ScriptsEditorGui(this));
    }
}

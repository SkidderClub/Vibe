package dev.vibe.module.impl;

import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.ui.CosmeticsEditorGui;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;

/** Opens the Skeet-themed preset editor for all client-only cosmetics. */
public final class CosmeticsEditorModule extends Module {
    public CosmeticsEditorModule() { super("Cosmetics Editor", "Create, preview and assign cosmetic presets", Category.CLIENT, Keyboard.KEY_NONE); }
    @Override protected void onEnable() {
        if (isConfigLoading()) { setEnabled(false); return; }
        Minecraft.getMinecraft().displayGuiScreen(new CosmeticsEditorGui(this));
    }
}

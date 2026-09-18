package dev.vibe.module.impl;

import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.ui.Gta7Gui;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;

/** Opens Vibe's local GTA7 city mini-game. */
public final class Gta7Module extends Module {
    public Gta7Module() { super("GTA7", "A 3D first-person city with police, traffic and permanent upgrades", Category.MEME, Keyboard.KEY_NONE); }
    @Override protected void onEnable() {
        if (isConfigLoading()) { setEnabled(false); return; }
        Minecraft.getMinecraft().displayGuiScreen(new Gta7Gui(this));
    }
}

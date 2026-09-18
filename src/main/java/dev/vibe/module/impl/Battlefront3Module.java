package dev.vibe.module.impl;

import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.ui.Battlefront3Gui;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;

/** Opens the independent local Star Wars campaign. */
public final class Battlefront3Module extends Module {
    public Battlefront3Module(){super("Battlefront 3","Command a Star Wars army across Geonosis and Endor; earn credits and upgrade your forces",Category.MEME,Keyboard.KEY_NONE);}
    @Override protected void onEnable(){
        if (isConfigLoading()) { setEnabled(false); return; }
        Minecraft mc=Minecraft.getMinecraft();
        mc.displayGuiScreen(new Battlefront3Gui(this));
    }
}

package dev.vibe.module.impl;

import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.ui.EspEditorGui;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;

/** Opens the Counter-Strike-inspired interactive ESP layout editor. */
public final class EspEditorModule extends Module {
    public EspEditorModule() {
        super("ESP Editor", "Edit 2D, 3D, Skeletal and Chams appearance", Category.VISUAL, Keyboard.KEY_NONE);
    }
    @Override protected void onEnable() {
        Minecraft mc=Minecraft.getMinecraft();
        if(mc.currentScreen instanceof dev.vibe.ui.Gta7Gui) { ((dev.vibe.ui.Gta7Gui)mc.currentScreen).openEspEditor();return; }
        mc.displayGuiScreen(new EspEditorGui(this,mc.currentScreen));
    }
}

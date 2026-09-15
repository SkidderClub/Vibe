package dev.vibe.module.impl;

import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.ui.FriendEditorGui;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;

/** Opens the visual management surface for the persistent Friends roster. */
public final class FriendEditorModule extends Module {
    public FriendEditorModule() { super("Friend Editor", "Edit friends and view live equipment", Category.CLIENT, Keyboard.KEY_NONE); }
    @Override protected void onEnable() {
        if (Minecraft.getMinecraft().thePlayer == null) { setEnabled(false); return; }
        Minecraft.getMinecraft().displayGuiScreen(new FriendEditorGui(this));
    }
}

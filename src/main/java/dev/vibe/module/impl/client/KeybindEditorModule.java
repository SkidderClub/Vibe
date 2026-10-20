package dev.vibe.module.impl.client;

import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.ui.screen.KeybindEditorGui;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;

/** Opens the keyboard-layout-aware in-game bind editor. */
public final class KeybindEditorModule extends Module {
    public KeybindEditorModule() {
        super("Keybind Editor", "Assign module keys using the current keyboard layout", Category.CLIENT, Keyboard.KEY_NONE);
    }

    @Override protected void onEnable() {
        if (isConfigLoading()) { setEnabled(false); return; }
        Minecraft.getMinecraft().displayGuiScreen(new KeybindEditorGui(this));
    }
}

package dev.vibe.module.impl;

import dev.vibe.Vibe;
import dev.vibe.module.Category;
import dev.vibe.module.Module;
import org.lwjgl.input.Keyboard;

public final class HudEditorModule extends Module {

    public HudEditorModule() {
        super("HUD Editor", "Move watermark and module list", Category.CLIENT, Keyboard.KEY_H);
    }

    @Override
    public void toggle() {
        Vibe.getInstance().getHudManager().openEditor();
    }
}

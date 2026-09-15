package dev.vibe.module.impl;

import dev.vibe.module.Category;
import dev.vibe.module.Module;
import org.lwjgl.input.Keyboard;

public final class SprintModule extends Module {
    public SprintModule() {
        super("Sprint", "Sprint while moving forward", Category.MOVEMENT, Keyboard.KEY_NONE);
    }
}

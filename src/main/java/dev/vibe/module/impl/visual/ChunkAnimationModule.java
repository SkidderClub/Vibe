package dev.vibe.module.impl.visual;

import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.setting.ModeSetting;
import dev.vibe.setting.NumberSetting;
import dev.vibe.ui.render.ChunkAnimationHooks;
import org.lwjgl.input.Keyboard;

/** Animates the existing chunk meshes once when their first compiled geometry becomes ready. */
public final class ChunkAnimationModule extends Module {
    public final ModeSetting animation = addSetting(new ModeSetting("Animation", "Rise", "Rise", "Drop", "Slide", "Grow"));
    public final NumberSetting duration = addSetting(new NumberSetting("Duration (ms)", 600, 150, 2000, 25));
    public final NumberSetting distance = addSetting(new NumberSetting("Distance", 16, 2, 48, 1, () -> !animation.is("Grow")));

    public ChunkAnimationModule() {
        super("ChunkAnimation", "Smooth animations for newly loaded chunks", Category.VISUAL, Keyboard.KEY_NONE);
        ChunkAnimationHooks.bind(this);
    }
    @Override protected void onDisable() { ChunkAnimationHooks.disable(this); }
}

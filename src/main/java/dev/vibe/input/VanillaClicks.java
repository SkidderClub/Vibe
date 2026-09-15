package dev.vibe.input;

import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/** Shared native input edges; the Minecraft input loop owns combat packets. */
public final class VanillaClicks {
    private VanillaClicks() { }

    public static void pulseAttack(KeyBinding binding) {
        int code = binding.getKeyCode();
        KeyBinding.setKeyBindState(code, false);
        KeyBinding.setKeyBindState(code, true);
        KeyBinding.onTick(code);
        ClickStats.recordLeft();
    }

    public static void discardPresses(KeyBinding binding) {
        while (binding.isPressed()) { /* Consume stale physical/synthetic edges. */ }
    }

    public static boolean physicallyDown(KeyBinding binding) {
        int code = binding.getKeyCode();
        if (code < 0) return Mouse.isCreated() && code + 100 >= 0 && Mouse.isButtonDown(code + 100);
        return code > 0 && code < Keyboard.KEYBOARD_SIZE && Keyboard.isCreated() && Keyboard.isKeyDown(code);
    }

    public static void restore(KeyBinding binding) {
        KeyBinding.setKeyBindState(binding.getKeyCode(), physicallyDown(binding));
    }
}

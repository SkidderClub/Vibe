package dev.vibe.module.impl;

import dev.vibe.Vibe;
import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;

/** A fixed fake rotation producer for checking MoveFix in-game. */
public final class TestModule extends Module {

    private final NumberSetting yaw = addSetting(new NumberSetting("Yaw", 0.0D, -180.0D, 180.0D, 1.0D));
    private final NumberSetting pitch = addSetting(new NumberSetting("Pitch", 0.0D, -90.0D, 90.0D, 1.0D));

    public TestModule() {
        super("Test", "Tests MoveFix with a fake server rotation", Category.MOVEMENT, Keyboard.KEY_NONE);
    }

    /** Runs before vanilla updates movement and sends its walking packet. */
    public void tick() {
        if (!isEnabled() || Minecraft.getMinecraft().thePlayer == null) {
            return;
        }
        Vibe vibe = Vibe.getInstance();
        MoveFixModule moveFix = vibe == null || vibe.getModuleManager() == null ? null
                : vibe.getModuleManager().getModule(MoveFixModule.class);
        if (moveFix != null) {
            moveFix.setFakeRotation(getId(), yaw.getFloat(), pitch.getFloat());
        }
    }

    @Override
    protected void onDisable() {
        Vibe vibe = Vibe.getInstance();
        MoveFixModule moveFix = vibe == null || vibe.getModuleManager() == null ? null
                : vibe.getModuleManager().getModule(MoveFixModule.class);
        if (moveFix != null) {
            moveFix.clearFakeRotation(getId());
        }
    }

    public NumberSetting getYaw() {
        return yaw;
    }

    public NumberSetting getPitch() {
        return pitch;
    }
}

package dev.vibe.module.impl;

import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.module.impl.SprintModule;
import dev.vibe.setting.ModeSetting;
import dev.vibe.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;

/** Basic client flight and a ground-spoof movement mode. */
public final class FlyModule extends Module {

    private final ModeSetting mode = addSetting(new ModeSetting("Mode", "Basic", "Basic", "GroundSpoof"));
    private final NumberSetting horizontalSpeed = addSetting(new NumberSetting("Horizontal Speed", 0.8D, 0.1D, 3.0D, 0.1D));
    private final NumberSetting verticalSpeed = addSetting(new NumberSetting("Vertical Speed", 0.35D, 0.1D, 2.0D, 0.05D));
    private final Minecraft minecraft = Minecraft.getMinecraft();
    private boolean wasFlying;

    public FlyModule() {
        super("Fly", "Simple client flight", Category.MOVEMENT, Keyboard.KEY_NONE);
    }

    @Override
    protected void onEnable() {
        wasFlying = minecraft.thePlayer != null && minecraft.thePlayer.capabilities.isFlying;
    }

    public void tick() {
        if (!isEnabled() || minecraft.thePlayer == null || minecraft.currentScreen != null) {
            return;
        }
        minecraft.thePlayer.capabilities.isFlying = true;
        minecraft.thePlayer.capabilities.setFlySpeed(0.05F);
        minecraft.thePlayer.motionY = 0.0D;
        if (minecraft.gameSettings.keyBindJump.isKeyDown()) {
            minecraft.thePlayer.motionY = verticalSpeed.getDouble();
        } else if (minecraft.gameSettings.keyBindSneak.isKeyDown()) {
            minecraft.thePlayer.motionY = -verticalSpeed.getDouble();
        }
        setHorizontalSpeed(horizontalSpeed.getDouble());
        if (mode.is("GroundSpoof")) {
            minecraft.thePlayer.onGround = true;
        }
    }

    @Override
    protected void onDisable() {
        if (minecraft.thePlayer == null) {
            return;
        }
        minecraft.thePlayer.capabilities.isFlying = wasFlying;
        minecraft.thePlayer.motionX = 0.0D;
        minecraft.thePlayer.motionY = 0.0D;
        minecraft.thePlayer.motionZ = 0.0D;
    }

    private void setHorizontalSpeed(double speed) {
        double forward = minecraft.thePlayer.movementInput.moveForward;
        double strafe = minecraft.thePlayer.movementInput.moveStrafe;
        double length = Math.sqrt(forward * forward + strafe * strafe);
        if (length < 0.001D) {
            minecraft.thePlayer.motionX = 0.0D;
            minecraft.thePlayer.motionZ = 0.0D;
            return;
        }
        forward /= length;
        strafe /= length;
        double yaw = Math.toRadians(minecraft.thePlayer.rotationYaw);
        minecraft.thePlayer.motionX = (-Math.sin(yaw) * forward + Math.cos(yaw) * strafe) * speed;
        minecraft.thePlayer.motionZ = (Math.cos(yaw) * forward + Math.sin(yaw) * strafe) * speed;
    }
}

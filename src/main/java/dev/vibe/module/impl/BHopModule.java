package dev.vibe.module.impl;

import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.setting.BooleanSetting;
import dev.vibe.setting.ModeSetting;
import dev.vibe.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;

/** Small bunny-hop helper with a restrained custom mode. */
public final class BHopModule extends Module {

    private final ModeSetting mode = addSetting(new ModeSetting("Mode", "Legit", "Custom", "Legit", "GroundStrafe"));
    private final NumberSetting speed = addSetting(new NumberSetting("Speed", 0.32D, 0.1D, 0.8D, 0.01D,
            () -> mode.is("Custom")));
    private final NumberSetting jumpHeight = addSetting(new NumberSetting("Jump Height", 0.42D, 0.1D, 0.8D, 0.01D,
            () -> mode.is("Custom")));
    private final BooleanSetting onlyMoving = addSetting(new BooleanSetting("Only Moving", true,
            () -> mode.is("Custom")));
    private final Minecraft minecraft = Minecraft.getMinecraft();

    public BHopModule() {
        super("BHop", "Automatically jump while moving", Category.MOVEMENT, Keyboard.KEY_NONE);
    }

    public void tick() {
        if (!isEnabled() || minecraft.thePlayer == null || minecraft.currentScreen != null
                || minecraft.thePlayer.isInWater() || minecraft.thePlayer.isOnLadder()
                || !minecraft.thePlayer.onGround) {
            return;
        }
        boolean moving = minecraft.thePlayer.movementInput.moveForward != 0.0F
                || minecraft.thePlayer.movementInput.moveStrafe != 0.0F;
        if (mode.is("Custom") && onlyMoving.isEnabled() && !moving) {
            return;
        }
        if (mode.is("Legit") || mode.is("GroundStrafe")) {
            minecraft.thePlayer.jump();
            if (mode.is("GroundStrafe")) {
                minecraft.thePlayer.setSprinting(true);
                strafeOnGround(0.28D);
            }
            return;
        }
        minecraft.thePlayer.motionY = jumpHeight.getDouble();
        double horizontal = Math.sqrt(minecraft.thePlayer.motionX * minecraft.thePlayer.motionX
                + minecraft.thePlayer.motionZ * minecraft.thePlayer.motionZ);
        if (moving && horizontal < speed.getDouble()) {
            double yaw = Math.toRadians(minecraft.thePlayer.rotationYaw);
            double forward = minecraft.thePlayer.movementInput.moveForward;
            double strafe = minecraft.thePlayer.movementInput.moveStrafe;
            double length = Math.sqrt(forward * forward + strafe * strafe);
            if (length > 0.0D) {
                forward /= length;
                strafe /= length;
                minecraft.thePlayer.motionX = (-Math.sin(yaw) * forward + Math.cos(yaw) * strafe) * speed.getDouble();
                minecraft.thePlayer.motionZ = (Math.cos(yaw) * forward + Math.sin(yaw) * strafe) * speed.getDouble();
            }
        }
    }

    private void strafeOnGround(double speed) {
        double forward = minecraft.thePlayer.movementInput.moveForward;
        double strafe = minecraft.thePlayer.movementInput.moveStrafe;
        double length = Math.sqrt(forward * forward + strafe * strafe);
        if (length < 0.001D) {
            return;
        }
        double yaw = Math.toRadians(minecraft.thePlayer.rotationYaw);
        minecraft.thePlayer.motionX = (-Math.sin(yaw) * forward + Math.cos(yaw) * strafe) / length * speed;
        minecraft.thePlayer.motionZ = (Math.cos(yaw) * forward + Math.sin(yaw) * strafe) / length * speed;
    }
}

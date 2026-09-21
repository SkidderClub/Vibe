package dev.vibe.module.impl;

import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.setting.BooleanSetting;
import dev.vibe.setting.ModeSetting;
import dev.vibe.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.EnumFacing;
import org.lwjgl.input.Keyboard;

/** LiquidBounce legacy LongJump AAC modes plus a tunable Vibe custom mode. */
public final class LongJumpModule extends Module {
    private final ModeSetting mode = addSetting(new ModeSetting("Mode", "Custom", "Custom", "AACv1", "AACv2", "AACv3"));
    private final NumberSetting customMotionY = addSetting(new NumberSetting("Custom Motion Y", 0.05999D, 0.0D, 1.0D, 0.00001D,
            () -> mode.is("Custom")));
    private final NumberSetting customSpeed = addSetting(new NumberSetting("Custom Speed", 1.08D, 0.1D, 3.0D, 0.01D,
            () -> mode.is("Custom")));
    private final NumberSetting customAirControl = addSetting(new NumberSetting("Custom Air Control", 0.08D, 0.0D, 1.0D, 0.01D,
            () -> mode.is("Custom")));
    private final BooleanSetting autoJump = addSetting(new BooleanSetting("Auto Jump", true));
    private final Minecraft minecraft = Minecraft.getMinecraft();
    private boolean jumped;
    private boolean teleported;

    public LongJumpModule() { super("LongJump", "Legacy AAC long-jump modes", Category.MOVEMENT, Keyboard.KEY_NONE); }

    public void tick() {
        if (!isEnabled() || minecraft.thePlayer == null || minecraft.currentScreen != null) return;
        EntityPlayerSP p = minecraft.thePlayer;
        if (jumped) {
            if (p.onGround || p.capabilities.isFlying) { jumped = false; return; }
            if (mode.is("AACv1")) {
                p.motionY += 0.05999D;
                AacMovementSupport.strafe(p, AacMovementSupport.horizontalSpeed(p) * 1.08D);
            } else if (mode.is("AACv2")) {
                p.jumpMovementFactor = 0.09F;
                p.motionY += 0.01320999999999999D;
                p.jumpMovementFactor = 0.08F;
                AacMovementSupport.strafe(p, AacMovementSupport.horizontalSpeed(p));
            } else if (mode.is("AACv3")) {
                if (p.fallDistance > 0.5F && !teleported) {
                    double value = 3.0D, x = 0.0D, z = 0.0D;
                    EnumFacing facing = p.getHorizontalFacing();
                    if (facing == EnumFacing.NORTH) z = -value;
                    else if (facing == EnumFacing.EAST) x = value;
                    else if (facing == EnumFacing.SOUTH) z = value;
                    else if (facing == EnumFacing.WEST) x = -value;
                    p.setPosition(p.posX + x, p.posY, p.posZ + z);
                    teleported = true;
                }
            } else {
                p.motionY += customMotionY.getDouble();
                if (customAirControl.getDouble() > 0.0D) {
                    p.jumpMovementFactor = customAirControl.getFloat();
                    AacMovementSupport.strafe(p, Math.max(AacMovementSupport.horizontalSpeed(p), customSpeed.getDouble()));
                }
            }
            return;
        }
        if (autoJump.isEnabled() && p.onGround && AacMovementSupport.moving(p)) {
            jumped = true;
            teleported = false;
            p.jump();
        }
    }

    @Override protected void onDisable() { jumped = false; teleported = false; }
}

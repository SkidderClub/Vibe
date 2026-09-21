package dev.vibe.module.impl;

import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.setting.BooleanSetting;
import dev.vibe.setting.ModeSetting;
import dev.vibe.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.item.ItemBucketMilk;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Keyboard;

/**
 * Speed (formerly exposed as BHop) with the legacy LiquidBounce AAC modes.
 * The four AAC hop branches retain their original constants and guards; the
 * old Vibe hop modes remain available under their existing names.
 */
public final class SpeedModule extends Module {
    private final ModeSetting mode = addSetting(new ModeSetting("Mode", "Legit", "Legit", "GroundStrafe", "Custom",
            "AACYSpoof", "AACHop3.3.13", "AACHop3.5.0", "AACHop4", "AACHop5"));
    private final NumberSetting customY = addSetting(new NumberSetting("Custom Y", 0.42D, 0.0D, 4.0D, 0.01D,
            () -> mode.is("Custom")));
    private final NumberSetting customGroundStrafe = addSetting(new NumberSetting("Ground Strafe", 1.6D, 0.0D, 2.0D, 0.01D,
            () -> mode.is("Custom")));
    private final NumberSetting customAirStrafe = addSetting(new NumberSetting("Air Strafe", 0.0D, 0.0D, 2.0D, 0.01D,
            () -> mode.is("Custom")));
    private final NumberSetting customGroundTimer = addSetting(new NumberSetting("Ground Timer", 1.0D, 0.1D, 2.0D, 0.01D,
            () -> mode.is("Custom")));
    private final NumberSetting customAirTimerTick = addSetting(new NumberSetting("Air Timer Tick", 5.0D, 1.0D, 20.0D, 1.0D,
            () -> mode.is("Custom")));
    private final NumberSetting customAirTimer = addSetting(new NumberSetting("Air Timer", 1.0D, 0.1D, 2.0D, 0.01D,
            () -> mode.is("Custom")));
    private final BooleanSetting resetXZ = addSetting(new BooleanSetting("Reset XZ", false, () -> mode.is("Custom")));
    private final BooleanSetting resetY = addSetting(new BooleanSetting("Reset Y", false, () -> mode.is("Custom")));
    private final BooleanSetting notOnConsuming = addSetting(new BooleanSetting("Not On Consuming", false, () -> mode.is("Custom")));
    private final BooleanSetting notOnFalling = addSetting(new BooleanSetting("Not On Falling", false, () -> mode.is("Custom")));
    private final BooleanSetting notOnVoid = addSetting(new BooleanSetting("Not On Void", true, () -> mode.is("Custom")));
    private final Minecraft minecraft = Minecraft.getMinecraft();
    private int aacYSpoofStage;
    private boolean aacYSpoofReset;

    public SpeedModule() { super("Speed", "Controls hop and AAC movement speed", Category.MOVEMENT, Keyboard.KEY_NONE); }

    public void tick() {
        if (!isEnabled() || minecraft.thePlayer == null || minecraft.currentScreen != null) return;
        EntityPlayerSP p = minecraft.thePlayer;
        if (mode.is("Custom")) { tickCustom(p); return; }
        if (mode.is("AACYSpoof")) { tickAacYSpoof(p); return; }
        if (mode.is("Legit") || mode.is("GroundStrafe")) {
            if (!p.onGround || AacMovementSupport.inLiquid(p) || p.isOnLadder()) return;
            if (!AacMovementSupport.moving(p)) return;
            p.jump();
            if (mode.is("GroundStrafe")) { p.setSprinting(true); AacMovementSupport.strafe(p, 0.28D); }
            return;
        }
        if (mode.is("AACHop3.3.13")) tickAac3313(p);
        else if (mode.is("AACHop3.5.0")) tickAac350(p);
        else if (mode.is("AACHop4")) tickAac4(p);
        else tickAac5(p);
    }

    private void tickAac3313(EntityPlayerSP p) {
        if (!AacMovementSupport.moving(p) || AacMovementSupport.inLiquid(p) || p.isOnLadder() || p.ridingEntity != null || p.hurtTime > 0) return;
        if (p.onGround && p.isCollidedVertically) {
            double yaw = Math.toRadians(p.rotationYaw);
            p.motionX -= Math.sin(yaw) * 0.202D;
            p.motionZ += Math.cos(yaw) * 0.202D;
            p.motionY = 0.405D;
            AacMovementSupport.strafe(p, AacMovementSupport.horizontalSpeed(p));
        } else if (p.fallDistance < 0.31F) {
            if (AacMovementSupport.onCarpet(p)) return;
            p.jumpMovementFactor = p.movementInput.moveStrafe == 0.0F ? 0.027F : 0.021F;
            p.motionX *= 1.001D; p.motionZ *= 1.001D;
            if (!p.isCollidedHorizontally) p.motionY -= 0.014999993D;
        } else p.jumpMovementFactor = 0.02F;
    }

    private void tickAac350(EntityPlayerSP p) {
        if (!AacMovementSupport.moving(p) || AacMovementSupport.inLiquid(p) || p.isSneaking()) return;
        p.jumpMovementFactor += 0.00208F;
        if (p.fallDistance <= 1.0F) {
            if (p.onGround) {
                p.jump();
                p.motionX *= 1.0118D; p.motionZ *= 1.0118D;
            } else {
                p.motionY -= 0.0147D;
                p.motionX *= 1.00138D; p.motionZ *= 1.00138D;
            }
        }
    }

    private void tickAac4(EntityPlayerSP p) {
        CombatTimerAccess.setSpeed(1.0F);
        if (!AacMovementSupport.moving(p) || AacMovementSupport.inLiquid(p) || p.isOnLadder() || p.ridingEntity != null) return;
        if (p.onGround) p.jump();
        else if (p.fallDistance <= 0.1F) CombatTimerAccess.setSpeed(1.5F);
        else if (p.fallDistance < 1.3F) CombatTimerAccess.setSpeed(0.7F);
    }

    private void tickAac5(EntityPlayerSP p) {
        if (!AacMovementSupport.moving(p) || AacMovementSupport.inLiquid(p) || p.isOnLadder() || p.ridingEntity != null) return;
        if (p.onGround) {
            p.jump();
            CombatTimerAccess.setSpeed(0.9385F);
            AacMovementSupport.speedInAir(p, 0.0201F);
        }
        if (p.fallDistance < 2.5F) {
            if (p.fallDistance > 0.7F) {
                if (p.ticksExisted % 3 == 0) CombatTimerAccess.setSpeed(1.925F);
                else if (p.fallDistance < 1.25F) CombatTimerAccess.setSpeed(1.7975F);
            }
            AacMovementSupport.speedInAir(p, 0.02F);
        }
    }

    /**
     * AAC 3.3.x YPort-style spoof.  The constants and stage timing follow
     * the public AACYPort implementation: a small ground impulse, then a
     * delayed downward impulse while the player is airborne.
     */
    private void tickAacYSpoof(EntityPlayerSP p) {
        CombatTimerAccess.setSpeed(1.0F);
        if (!AacMovementSupport.moving(p) || AacMovementSupport.inLiquid(p)
                || p.isOnLadder() || CombatRangeSupport.isInWeb(p) || p.isCollidedHorizontally) {
            aacYSpoofStage = 0;
            return;
        }
        if (p.onGround) {
            aacYSpoofReset = true;
            double yaw = Math.toRadians(p.rotationYaw);
            p.motionX -= Math.sin(yaw) * 0.2D;
            p.motionZ += Math.cos(yaw) * 0.2D;
            p.motionY = 0.42D;
            aacYSpoofStage++;
            if (aacYSpoofStage > 3) {
                p.motionX *= 1.01D;
                p.motionZ *= 1.01D;
            }
            return;
        }
        if (aacYSpoofStage > 3) {
            p.motionX *= 1.025D;
            p.motionZ *= 1.025D;
        }
        if (aacYSpoofReset) {
            p.motionY = -0.21D;
            aacYSpoofReset = false;
        } else {
            p.motionY -= 0.013D;
        }
    }

    private void tickCustom(EntityPlayerSP p) {
        ItemStack held = p.getHeldItem();
        boolean consuming = p.isUsingItem() && held != null && (held.getItem() instanceof ItemFood
                || held.getItem() instanceof ItemPotion || held.getItem() instanceof ItemBucketMilk);
        boolean blocked = notOnVoid.isEnabled() && !AacMovementSupport.hasCollisionWithin(p, 500)
                || notOnFalling.isEnabled() && p.fallDistance > 2.5F
                || notOnConsuming.isEnabled() && consuming;
        if (blocked) {
            if (p.onGround) AacMovementSupport.jump(p);
            CombatTimerAccess.setSpeed(1.0F);
            return;
        }
        if (!AacMovementSupport.moving(p)) return;
        if (p.onGround) {
            if (customGroundStrafe.getDouble() > 0.0D) AacMovementSupport.strafe(p, customGroundStrafe.getDouble());
            CombatTimerAccess.setSpeed(customGroundTimer.getFloat());
            p.motionY = customY.getDouble();
        } else {
            if (customAirStrafe.getDouble() > 0.0D) AacMovementSupport.strafe(p, customAirStrafe.getDouble());
            CombatTimerAccess.setSpeed(p.ticksExisted % customAirTimerTick.getInt() == 0 ? customAirTimer.getFloat() : 1.0F);
        }
    }

    @Override protected void onEnable() {
        aacYSpoofStage = 0;
        aacYSpoofReset = false;
        if (minecraft.thePlayer != null && mode.is("Custom")) {
            if (resetXZ.isEnabled()) AacMovementSupport.stopXZ(minecraft.thePlayer);
            if (resetY.isEnabled()) AacMovementSupport.stopY(minecraft.thePlayer);
        }
        if (minecraft.thePlayer != null && mode.is("AACHop3.5.0") && minecraft.thePlayer.onGround) AacMovementSupport.stopXZ(minecraft.thePlayer);
    }
    @Override protected void onDisable() {
        CombatTimerAccess.setSpeed(1.0F);
        aacYSpoofStage = 0;
        aacYSpoofReset = false;
        if (minecraft.thePlayer != null) AacMovementSupport.speedInAir(minecraft.thePlayer, 0.02F);
    }
}

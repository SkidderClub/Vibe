package dev.vibe.module.impl.movement;

import dev.vibe.combat.CombatRangeSupport;
import dev.vibe.combat.CombatTimerAccess;
import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.movement.AacMovementSupport;
import dev.vibe.movement.AdjustJesus;
import dev.vibe.movement.RewoundSpeed;
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
            "AACYSpoof", "AACHop3.3.13", "AACHop3.5.0", "AACHop4", "AACHop5", "Rewound"));
    private final BooleanSetting rewoundTimer = addSetting(new BooleanSetting("Use timer", true, () -> mode.is("Rewound")));
    private final ModeSetting rewoundFastFall = addSetting(new ModeSetting("Fast fall", "Off", () -> mode.is("Rewound"), "Off", "Snap", "Push"));
    private final RewoundSpeed rewound = new RewoundSpeed();
    private EntityPlayerSP rewoundPlayer;
    private boolean rewoundActive;
    private boolean rewoundStepPaused;
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
    private boolean legacyTimerOwned;
    private float previousTimer,lastTimer;
    private EntityPlayerSP airOwner;
    private float previousJumpFactor,previousAir,lastJumpFactor=Float.NaN,lastAir=Float.NaN;
    private Object waterPlayer,waterWorld;
    private boolean wasLiquid;
    private int resumeAfterWater=Integer.MIN_VALUE;

    public SpeedModule() { super("Speed", "Controls hop, AAC and Rewound movement speed", Category.MOVEMENT, Keyboard.KEY_NONE); }

    public void tick() {
        synchronizeRewound();
        if (!isEnabled() || waterPause() || flightActive() || rewoundStepPaused || AutoWalkModule.reservesJump() || dev.vibe.module.impl.world.ScaffoldModule.ownsStationaryNcpJump() || minecraft.thePlayer == null || minecraft.currentScreen != null) return;
        EntityPlayerSP p = minecraft.thePlayer;
        if (mode.is("Rewound")) return; // Runs at moveEntity, never at tick end.
        if (mode.is("Custom")) { tickCustom(p); return; }
        if (mode.is("AACYSpoof")) { tickAacYSpoof(p); return; }
        if (mode.is("Legit") || mode.is("GroundStrafe")) {
            if (!p.onGround || AacMovementSupport.inLiquid(p) || p.isOnLadder()) return;
            if (!AacMovementSupport.moving(p)) return;
            AacMovementSupport.jump(p);
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
            double yaw = Math.toRadians(dev.vibe.module.impl.world.ScaffoldModule.isActive() ? AacMovementSupport.directionYaw(p) : p.rotationYaw);
            p.motionX -= Math.sin(yaw) * 0.202D;
            p.motionZ += Math.cos(yaw) * 0.202D;
            p.motionY = 0.405D;
            AacMovementSupport.strafe(p, AacMovementSupport.horizontalSpeed(p));
        } else if (p.fallDistance < 0.31F) {
            if (AacMovementSupport.onCarpet(p)) return;
            setJumpFactor(p,p.movementInput.moveStrafe == 0.0F ? 0.027F : 0.021F);
            p.motionX *= 1.001D; p.motionZ *= 1.001D;
            if (!p.isCollidedHorizontally) p.motionY -= 0.014999993D;
        } else setJumpFactor(p,0.02F);
    }

    private void tickAac350(EntityPlayerSP p) {
        if (!AacMovementSupport.moving(p) || AacMovementSupport.inLiquid(p) || p.isSneaking()) return;
        setJumpFactor(p,p.jumpMovementFactor + 0.00208F);
        if (p.fallDistance <= 1.0F) {
            if (p.onGround) {
                AacMovementSupport.jump(p);
                p.motionX *= 1.0118D; p.motionZ *= 1.0118D;
            } else {
                p.motionY -= 0.0147D;
                p.motionX *= 1.00138D; p.motionZ *= 1.00138D;
            }
        }
    }

    private void tickAac4(EntityPlayerSP p) {
        setLegacyTimer(1.0F);
        if (!AacMovementSupport.moving(p) || AacMovementSupport.inLiquid(p) || p.isOnLadder() || p.ridingEntity != null) return;
        if (p.onGround) AacMovementSupport.jump(p);
        else if (p.fallDistance <= 0.1F) setLegacyTimer(1.5F);
        else if (p.fallDistance < 1.3F) setLegacyTimer(0.7F);
    }

    private void tickAac5(EntityPlayerSP p) {
        if (!AacMovementSupport.moving(p) || AacMovementSupport.inLiquid(p) || p.isOnLadder() || p.ridingEntity != null) return;
        if (p.onGround) {
            AacMovementSupport.jump(p);
            setLegacyTimer(0.9385F);
            setAirSpeed(p,0.0201F);
        }
        if (p.fallDistance < 2.5F) {
            if (p.fallDistance > 0.7F) {
                if (p.ticksExisted % 3 == 0) setLegacyTimer(1.925F);
                else if (p.fallDistance < 1.25F) setLegacyTimer(1.7975F);
            }
            setAirSpeed(p,0.02F);
        }
    }

    /**
     * AAC 3.3.x YPort-style spoof.  The constants and stage timing follow
     * the public AACYPort implementation: a small ground impulse, then a
     * delayed downward impulse while the player is airborne.
     */
    private void tickAacYSpoof(EntityPlayerSP p) {
        setLegacyTimer(1.0F);
        if (!AacMovementSupport.moving(p) || AacMovementSupport.inLiquid(p)
                || p.isOnLadder() || CombatRangeSupport.isInWeb(p) || p.isCollidedHorizontally) {
            aacYSpoofStage = 0;
            return;
        }
        if (p.onGround) {
            aacYSpoofReset = true;
            double yaw = Math.toRadians(dev.vibe.module.impl.world.ScaffoldModule.isActive() ? AacMovementSupport.directionYaw(p) : p.rotationYaw);
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
            setLegacyTimer(1.0F);
            return;
        }
        if (!AacMovementSupport.moving(p)) return;
        if (p.onGround) {
            if (customGroundStrafe.getDouble() > 0.0D) AacMovementSupport.strafe(p, customGroundStrafe.getDouble());
            setLegacyTimer(customGroundTimer.getFloat());
            p.motionY = customY.getDouble();
        } else {
            if (customAirStrafe.getDouble() > 0.0D) AacMovementSupport.strafe(p, customAirStrafe.getDouble());
            setLegacyTimer(p.ticksExisted % customAirTimerTick.getInt() == 0 ? customAirTimer.getFloat() : 1.0F);
        }
    }

    @Override protected void onEnable() {
        rewound.reset(minecraft.thePlayer);
        aacYSpoofStage = 0;
        aacYSpoofReset = false;
        if (waterPause()||flightActive()||dev.vibe.module.impl.world.ScaffoldModule.ownsStationaryNcpJump()) return;
        if (minecraft.thePlayer != null && mode.is("Custom")) {
            if (resetXZ.isEnabled()) AacMovementSupport.stopXZ(minecraft.thePlayer);
            if (resetY.isEnabled()) AacMovementSupport.stopY(minecraft.thePlayer);
        }
        if (minecraft.thePlayer != null && mode.is("AACHop3.5.0") && minecraft.thePlayer.onGround) AacMovementSupport.stopXZ(minecraft.thePlayer);
    }
    @Override protected void onDisable() {
        rewoundStepPaused = false;
        rewound.reset(null);
        rewoundActive = false;
        rewoundPlayer = null;
        releaseLegacyTweaks();
        aacYSpoofStage = 0;
        aacYSpoofReset = false;
    }

    private boolean synchronizeRewound() {
        EntityPlayerSP p = minecraft.thePlayer;
        boolean liquid=waterPause();
        boolean flying = flightActive();
        if (liquid || flying || isEnabled()&&(AutoWalkModule.reservesJump()||dev.vibe.module.impl.world.ScaffoldModule.ownsStationaryNcpJump())) { releaseLegacyTweaks(); aacYSpoofStage=0; aacYSpoofReset=false; }
        boolean active = isEnabled() && !liquid && !flying && !rewoundStepPaused && !AutoWalkModule.reservesJump() && !dev.vibe.module.impl.world.ScaffoldModule.ownsStationaryNcpJump() && mode.is("Rewound") && p != null && p.worldObj != null && p.worldObj == minecraft.theWorld
                && !p.isSpectator() && p.ridingEntity == null;
        // Switching modes must not let Rewound inherit a legacy timer as its baseline.
        if (active) releaseLegacyTweaks();
        if (rewoundActive != active || rewoundPlayer != p) rewound.reset(p);
        rewoundActive = active;
        rewoundPlayer = p;
        return active;
    }
    private static boolean flightActive() {
        FlyModule fly = dev.vibe.features.FeatureModule.module(FlyModule.class);
        return fly != null && fly.isEnabled();
    }
    /** Also runs in menus, where ClientEvents skips the ordinary movement ticks. */
    public void maintainRewound() { synchronizeRewound(); }
    public boolean rewoundControlsJump() { return synchronizeRewound(); }
    public void pauseForRewoundStep(boolean paused) {
        if (rewoundStepPaused == paused) return;
        rewoundStepPaused = paused;
        if (!isEnabled() || waterPause()) return;
        if (paused) {
            rewound.reset(null);
            CombatTimerAccess.setSpeed(1);
        } else {
            rewound.reset(minecraft.thePlayer);
            aacYSpoofStage = 0;
            aacYSpoofReset = false;
        }
    }
    public double[] rewoundMove(Object entity, double x, double y, double z) {
        return entity == minecraft.thePlayer && synchronizeRewound()
                ? rewound.move(minecraft.thePlayer, x, y, z, rewoundTimer.isEnabled(), rewoundFastFall.getValue()) : null;
    }
    public void rewoundAfterMove(Object entity, double[] move) {
        if (move != null && move.length >= 4 && entity == minecraft.thePlayer && synchronizeRewound()) rewound.afterMove(minecraft.thePlayer, move);
    }
    public void rewoundCorrection(net.minecraft.network.play.server.S08PacketPlayerPosLook packet) {
        if (synchronizeRewound()) rewound.correction(minecraft.thePlayer, packet);
    }
    private boolean inLiquid() { return minecraft.thePlayer != null && minecraft.thePlayer.worldObj != null && AacMovementSupport.inLiquid(minecraft.thePlayer); }
    private boolean waterPause(){
        EntityPlayerSP p=minecraft.thePlayer;
        if(p!=waterPlayer||minecraft.theWorld!=waterWorld){waterPlayer=p;waterWorld=minecraft.theWorld;wasLiquid=false;resumeAfterWater=Integer.MIN_VALUE;}
        if(p==null)return false;
        if(inLiquid() || AdjustJesus.supportsFeet(p)){wasLiquid=true;return true;}
        if(wasLiquid){wasLiquid=false;resumeAfterWater=p.ticksExisted+4;}
        return p.ticksExisted<resumeAfterWater;
    }
    private void setLegacyTimer(float value) {
        if (!legacyTimerOwned) previousTimer=CombatTimerAccess.getBaseSpeed();
        legacyTimerOwned=true;lastTimer=value;CombatTimerAccess.setSpeed(value);
    }
    private void claimAir(EntityPlayerSP p) {
        if (airOwner == p) return;
        airOwner=p;previousJumpFactor=p.jumpMovementFactor;previousAir=AacMovementSupport.speedInAir(p);
        lastJumpFactor=lastAir=Float.NaN;
    }
    private void setJumpFactor(EntityPlayerSP p,float value) { claimAir(p);p.jumpMovementFactor=value;lastJumpFactor=value; }
    private void setAirSpeed(EntityPlayerSP p,float value) { claimAir(p);AacMovementSupport.speedInAir(p,value);lastAir=value; }
    /** Restore Speed-owned settings once; leave swimming input/velocity and other owners alone. */
    private void releaseLegacyTweaks() {
        if (legacyTimerOwned && Float.compare(CombatTimerAccess.getBaseSpeed(),lastTimer)==0) CombatTimerAccess.setSpeed(previousTimer);
        legacyTimerOwned=false;
        if (airOwner != null) {
            if (Float.compare(airOwner.jumpMovementFactor,lastJumpFactor)==0) airOwner.jumpMovementFactor=previousJumpFactor;
            if (Float.compare(AacMovementSupport.speedInAir(airOwner),lastAir)==0) AacMovementSupport.speedInAir(airOwner,previousAir);
            airOwner=null;
        }
    }
}

package dev.vibe.movement;

import dev.vibe.combat.CombatTimerAccess;
import dev.vibe.features.FeatureModule;
import dev.vibe.features.MovementForecast;
import dev.vibe.module.impl.combat.AutoBuffModule;
import dev.vibe.module.impl.movement.LongJumpModule;
import dev.vibe.module.impl.movement.SpeedModule;
import java.util.function.LongSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.network.play.client.C03PacketPlayer.C04PacketPlayerPosition;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;

/** Exire NcpStep plus StepModule's look-ahead Speed pause. */
public final class RewoundStep {
    private final LongSupplier clock;
    private EntityPlayerSP owner;
    private long stepDelay;
    private int timerTicks = -1, speedPauseTicks;
    private float preparedHeight;
    private double candidateBaseY, speedBeforePause;
    private boolean active;

    public RewoundStep() { this(() -> System.nanoTime() / 1000000L); }
    public RewoundStep(LongSupplier clock) { this.clock = clock; stepDelay = clock.getAsLong(); }

    public void reset(EntityPlayerSP player) {
        if (timerTicks > 0) CombatTimerAccess.setSpeed(1);
        SpeedModule speed = FeatureModule.module(SpeedModule.class);
        if (speed != null) speed.pauseForRewoundStep(false);
        if (owner != null) owner.stepHeight = .6F;
        owner = player;
        timerTicks = -1;
        speedPauseTicks = 0;
        preparedHeight = .6F;
        active = false;
        speedBeforePause = 0;
        stepDelay = clock.getAsLong();
    }

    public void preUpdate(int delay, boolean speedCheck) {
        if (owner == null) return;
        boolean paused = speedPauseTicks > 0;
        if (paused) speedPauseTicks--;
        double height = stepAhead(paused);
        if (height > 0 && height <= 1 && canStep(delay, speedCheck) && owner.onGround) speedPauseTicks = Math.max(speedPauseTicks, 1);
        SpeedModule speed = FeatureModule.module(SpeedModule.class);
        if (speed != null) speed.pauseForRewoundStep(speedPauseTicks > 0);
        if (timerTicks >= 0 && --timerTicks <= 0) {
            CombatTimerAccess.setSpeed(1);
            stepDelay = clock.getAsLong();
        }
    }

    public float preStep(int delay, boolean speedCheck) {
        owner.stepHeight = .6F;
        candidateBaseY = owner.getEntityBoundingBox().minY;
        active = false;
        preparedHeight = .6F;
        if (speedCheck && speedEnabled() || Minecraft.getMinecraft().gameSettings.keyBindJump.isKeyDown()) return preparedHeight;
        LongJumpModule jump = FeatureModule.module(LongJumpModule.class);
        if (jump != null && jump.isEnabled() || AutoBuffModule.ownsUseInput()) return preparedHeight = 0;
        if (owner.onGround && owner.isCollidedVertically && clock.getAsLong() - stepDelay >= delay) {
            active = true;
            preparedHeight = 1;
        }
        return preparedHeight;
    }
    public float height() { return preparedHeight; }

    /** Called at Exire's post-step site, before resetPositionToBB changes the base position. */
    public void postStep(boolean timer) {
        if (owner == null) return;
        float height = (float) Math.abs(owner.getEntityBoundingBox().minY - candidateBaseY);
        if (active && height > .6F) {
            if (timer) { timerTicks = 1; CombatTimerAccess.setSpeed(.5F); }
            owner.sendQueue.addToSendQueue(new C04PacketPlayerPosition(owner.posX, owner.posY + (double) .42F, owner.posZ, false));
            owner.sendQueue.addToSendQueue(new C04PacketPlayerPosition(owner.posX, owner.posY + .7531999805212024, owner.posZ, false));
            active = false;
        }
        if (height > .6F) speedPauseTicks = Math.max(speedPauseTicks, 2);
    }

    private boolean canStep(int delay, boolean speedCheck) {
        LongJumpModule jump = FeatureModule.module(LongJumpModule.class);
        return !(speedCheck && speedEnabled()) && !Minecraft.getMinecraft().gameSettings.keyBindJump.isKeyDown()
                && (jump == null || !jump.isEnabled()) && !AutoBuffModule.ownsUseInput() && clock.getAsLong() - stepDelay >= delay;
    }
    private boolean speedEnabled() { SpeedModule speed = FeatureModule.module(SpeedModule.class); return speed != null && speed.isEnabled(); }

    private double stepAhead(boolean paused) {
        Minecraft mc = Minecraft.getMinecraft();
        AxisAlignedBB box = owner.getEntityBoundingBox();
        if (!owner.onGround) {
            double fall = owner.motionY;
            if (fall >= 0 || !MovementForecast.loaded(box.addCoord(0, fall, 0))
                    || mc.theWorld.getCollidingBoundingBoxes(owner, box.offset(0, fall, 0)).isEmpty()) return 0;
            double land = fall;
            for (AxisAlignedBB block : mc.theWorld.getCollidingBoundingBoxes(owner, box.addCoord(0, fall, 0))) land = block.calculateYOffset(box, land);
            box = box.offset(0, land, 0);
        }
        GameSettings settings = mc.gameSettings;
        boolean keys = settings.keyBindForward.isKeyDown() != settings.keyBindBack.isKeyDown()
                || settings.keyBindLeft.isKeyDown() != settings.keyBindRight.isKeyDown();
        if (!keys && !AacMovementSupport.moving(owner)) return 0;
        float forward = keys ? (settings.keyBindForward.isKeyDown() ? 1 : 0) - (settings.keyBindBack.isKeyDown() ? 1 : 0) : owner.movementInput.moveForward;
        float side = keys ? (settings.keyBindLeft.isKeyDown() ? 1 : 0) - (settings.keyBindRight.isKeyDown() ? 1 : 0) : owner.movementInput.moveStrafe;
        float yaw = owner.rotationYaw;
        if (forward < 0) yaw += 180;
        float factor = forward < 0 ? -.5F : forward > 0 ? .5F : 1;
        if (side > 0) yaw -= 90 * factor;
        else if (side < 0) yaw += 90 * factor;
        float direction = (float) Math.toRadians(yaw);
        double speed = Math.max(Math.hypot(owner.motionX, owner.motionZ), Math.hypot(owner.posX - owner.prevPosX, owner.posZ - owner.prevPosZ));
        if (paused) speed = Math.max(speed, speedBeforePause); else speedBeforePause = speed;
        double distance = Math.max(.5, speed * 2 + .2);
        double x = -MathHelper.sin(direction) * distance, z = MathHelper.cos(direction) * distance;
        if (!MovementForecast.loaded(box.offset(x, 0, z).addCoord(0, 1, 0))) return 0;
        double top = Double.NEGATIVE_INFINITY;
        for (AxisAlignedBB block : mc.theWorld.getCollidingBoundingBoxes(owner, box.offset(x, 0, z))) top = Math.max(top, block.maxY);
        double height = top - box.minY;
        if (height <= .600001) return 0;
        return mc.theWorld.getCollidingBoundingBoxes(owner, box.addCoord(0, height, 0)).isEmpty()
                && mc.theWorld.getCollidingBoundingBoxes(owner, box.offset(x, height, z)).isEmpty() ? height : 0;
    }
}

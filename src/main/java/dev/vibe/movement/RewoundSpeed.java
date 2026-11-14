package dev.vibe.movement;

import dev.vibe.combat.CombatTimerAccess;
import java.lang.reflect.Field;
import java.util.function.LongSupplier;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.potion.Potion;

/** Exire NcpModesSpeed's REWOUND branch, evaluated before native collision resolution. */
public final class RewoundSpeed {
    private final LongSupplier clock;
    private long lastSetback;
    private int stage = 2;
    private double less, stair;
    private boolean collided, lessSlow, shouldSlow, timerOwned;

    public RewoundSpeed() { this(() -> System.nanoTime() / 1000000L); }
    public RewoundSpeed(LongSupplier clock) { this.clock = clock; lastSetback = clock.getAsLong(); }

    public void reset(EntityPlayerSP player) {
        releaseTimer();
        stage = 2;
        less = stair = 0;
        collided = player != null && player.isCollidedHorizontally;
        lessSlow = shouldSlow = false;
    }

    public void releaseTimer() {
        if (timerOwned) CombatTimerAccess.setSpeed(1.0F);
        timerOwned = false;
    }

    public double[] move(EntityPlayerSP player, double x, double y, double z, boolean timer, String fastFall) {
        if (timer) setTimer(1.2F); else releaseTimer();
        if (player.isCollidedHorizontally) collided = true;
        if (collided) { setTimer(1.0F); stage = -1; }
        if (stair > 0) stair -= .25;
        less = Math.max(0, less - (less > 1 ? .12 : .11));
        if (!player.isInWater() && !player.isInLava()
                && !player.worldObj.getCollidingBoundingBoxes(player, player.getEntityBoundingBox().offset(0, -.01, 0)).isEmpty()
                && AacMovementSupport.moving(player)) {
            collided = player.isCollidedHorizontally;
            if (stage >= 0 || collided) {
                stage = 0;
                if (stair == 0) {
                    player.jump();
                    y = player.motionY = .407 + boost(player, Potion.jump) * .1;
                }
                less += 1;
                lessSlow = less > 1 && !lessSlow;
                less = Math.min(less, 1.12);
            }
        }
        if (stage == 5 && !player.onGround) {
            if ("Snap".equals(fastFall)) y = player.motionY = -.18517061160392762;
            else if ("Push".equals(fastFall)) y = player.motionY += -((double) .18999F + (double) 4E-8F * Math.random());
        }
        int speedBoost = boost(player, Potion.moveSpeed);
        double speed = (stageSpeed(stage, speedBoost) + .0331) * .91;
        if (stair > 0) speed *= .7 - speedBoost * .1;
        if (stage < 0) speed = defaultSpeed(speedBoost);
        if (lessSlow) speed *= .95;
        if (player.isInWater() || player.isInLava()) speed = .12;
        if (player.moveForward != 0 || player.moveStrafing != 0) {
            // MovementUtil.strafe(event, speed) changes the requested move, not motionX/Z.
            double forward = player.movementInput.moveForward, strafe = player.moveStrafing;
            float yaw = player.rotationYaw;
            if (forward != 0) {
                if (strafe > 0) yaw += forward > 0 ? -45 : 45;
                else if (strafe < 0) yaw += forward > 0 ? 45 : -45;
                strafe = 0;
                forward = forward > 0 ? 1 : -1;
            }
            strafe = Math.signum(strafe);
            double mx = Math.cos(Math.toRadians(yaw + 90F)), mz = Math.sin(Math.toRadians(yaw + 90F));
            x = forward * speed * mx + strafe * speed * mz;
            z = forward * speed * mz - strafe * speed * mx;
            stage++;
        }
        return new double[] {x, y, z, player.getEntityBoundingBox().minY};
    }

    private double stageSpeed(int current, int speedBoost) {
        double normal = defaultSpeed(speedBoost);
        double value = normal + .028 * speedBoost + speedBoost / 15.0;
        double first = .4145 + speedBoost / 12.5;
        if (current == 0) {
            shouldSlow = clock.getAsLong() - lastSetback < 500;
            value = .64 + (speedBoost + .028 * speedBoost) * .134;
        } else if (current == 1) value = first;
        else if (current >= 2) value = first - current / 500.0 * 2;
        if (shouldSlow || clock.getAsLong() - lastSetback < 500 || collided) value = current == 0 ? 0 : .2;
        return Math.max(value, shouldSlow ? value : normal + .028 * speedBoost);
    }

    /** The source emits post-step only after collision resolution chooses a raised path. */
    public void afterMove(EntityPlayerSP player, double[] move) {
        double rise = player.getEntityBoundingBox().minY - move[3];
        if (!player.noClip && player.isCollidedVertically && rise > Math.max(0, move[1]) + 1E-7) {
            float height = (float) rise;
            if (height > .7) less = 0;
            if (height == .5F) stair = .75;
        }
    }

    public void correction(EntityPlayerSP player, S08PacketPlayerPosLook packet) {
        if (clock.getAsLong() - lastSetback >= 300) {
            // Relative look flags must receive zero offsets, not an absolute camera angle.
            writeLook(packet, packet.func_179834_f().contains(S08PacketPlayerPosLook.EnumFlags.Y_ROT) ? 0 : player.rotationYaw,
                    packet.func_179834_f().contains(S08PacketPlayerPosLook.EnumFlags.X_ROT) ? 0 : player.rotationPitch);
        }
        stage = -4;
        lastSetback = clock.getAsLong();
    }

    private void setTimer(float speed) { CombatTimerAccess.setSpeed(speed); timerOwned = true; }
    private static int boost(EntityPlayerSP p, Potion potion) {
        return p.isPotionActive(potion) ? p.getActivePotionEffect(potion).getAmplifier() + 1 : 0;
    }
    private static double defaultSpeed(int boost) { return .2873 * (1 + .2 * boost); }
    private static Field yaw, pitch;
    private static void writeLook(S08PacketPlayerPosLook p, float y, float x) {
        try {
            if (yaw == null) yaw = lookField("yaw", "field_148936_d");
            if (pitch == null) pitch = lookField("pitch", "field_148937_e");
            yaw.setFloat(p, y); pitch.setFloat(p, x);
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("S08 look fields", failure); }
    }
    private static Field lookField(String mcp, String srg) throws NoSuchFieldException {
        Field field;
        try { field = S08PacketPlayerPosLook.class.getDeclaredField(mcp); }
        catch (NoSuchFieldException ignored) { field = S08PacketPlayerPosLook.class.getDeclaredField(srg); }
        field.setAccessible(true);
        return field;
    }
}

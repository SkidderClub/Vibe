package dev.vibe.module.impl;

import dev.vibe.Vibe;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.potion.Potion;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovementInput;
import net.minecraft.util.Vec3;
import org.lwjgl.input.Keyboard;

/** Small API bridge for the EntityUtil, MovementUtil and SimulatedPlayer calls
 * used by the Gothaj combat modules. */
final class CombatRangeSupport {
    private static final Minecraft MC = Minecraft.getMinecraft();

    private CombatRangeSupport() { }

    static EntityLivingBase getTarget(double range) {
        if (MC.thePlayer == null || MC.theWorld == null) return null;
        EntityLivingBase best = null;
        for (Object value : MC.theWorld.loadedEntityList) {
            if (!(value instanceof EntityLivingBase)) continue;
            EntityLivingBase candidate = (EntityLivingBase) value;
            if (candidate == MC.thePlayer || !candidate.isEntityAlive() || candidate.isDead || distanceToBox(candidate) > range) continue;
            TargetsModule targets = Vibe.getInstance() == null ? null
                    : Vibe.getInstance().getModuleManager().getModule(TargetsModule.class);
            if (targets != null && !targets.canTarget(candidate)) continue;
            if (best == null || distanceToBox(candidate) < distanceToBox(best)) best = candidate;
        }
        return best;
    }

    static double distanceToBox(Entity entity) {
        if (MC.thePlayer == null || entity == null) return Double.MAX_VALUE;
        Vec3 eye = MC.thePlayer.getPositionEyes(1.0F);
        Vec3 hit = closestPoint(eye, entity.getEntityBoundingBox());
        return eye.distanceTo(hit);
    }

    static double distanceToBoxFromPosition(double x, double y, double z, Entity entity) {
        if (MC.thePlayer == null || entity == null) return Double.MAX_VALUE;
        Vec3 eye = new Vec3(x, y + MC.thePlayer.getEyeHeight(), z);
        return eye.distanceTo(closestPoint(eye, entity.getEntityBoundingBox()));
    }

    static boolean isMoving() {
        return down(MC.gameSettings.keyBindForward.getKeyCode()) || down(MC.gameSettings.keyBindBack.getKeyCode())
                || down(MC.gameSettings.keyBindRight.getKeyCode()) || down(MC.gameSettings.keyBindLeft.getKeyCode());
    }

    static boolean isInWeb(Entity entity) {
        if (entity == null) return false;
        try {
            Field field;
            try { field = Entity.class.getDeclaredField("isInWeb"); }
            catch (NoSuchFieldException ignored) { field = Entity.class.getDeclaredField("field_70134_J"); }
            field.setAccessible(true);
            return field.getBoolean(entity);
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    static double baseMoveSpeed() {
        if (MC.thePlayer == null) return 0.2873D;
        double speed = 0.2873D;
        if (MC.thePlayer.isUsingItem()) speed *= 0.2D;
        if (MC.thePlayer.isPotionActive(Potion.moveSpeed)) {
            speed *= 1.0D + 0.2D * (MC.thePlayer.getActivePotionEffect(Potion.moveSpeed).getAmplifier() + 1);
        }
        return speed;
    }

    /** Port of the snapshots TickBase records from Gothaj's SimulatedPlayer. */
    static List<Snapshot> simulate(EntityPlayerSP player, int steps, float yaw) {
        List<Snapshot> result = new ArrayList<Snapshot>();
        if (player == null || player.worldObj == null || steps <= 0) return result;
        MovementInput input = player.movementInput;
        double x = player.posX, y = player.posY, z = player.posZ;
        double motionX = player.motionX, motionY = player.motionY, motionZ = player.motionZ;
        boolean ground = player.onGround;
        float fall = player.fallDistance;
        AxisAlignedBB box = player.getEntityBoundingBox();
        for (int index = 0; index < steps; index++) {
            if (ground && input.jump) motionY = 0.42D;
            float friction = ground ? player.worldObj.getBlockState(new net.minecraft.util.BlockPos(x, box.minY - 1.0D, z))
                    .getBlock().slipperiness * 0.91F : 0.91F;
            float acceleration = ground ? player.getAIMoveSpeed() * (0.16277136F / (friction * friction * friction))
                    : player.jumpMovementFactor;
            double forward = input.moveForward;
            double strafe = input.moveStrafe;
            double length = forward * forward + strafe * strafe;
            if (length >= 1.0E-4D) {
                length = Math.sqrt(length);
                double factor = acceleration / Math.max(1.0D, length);
                forward *= factor;
                strafe *= factor;
                double radians = Math.toRadians(yaw);
                motionX += strafe * Math.cos(radians) - forward * Math.sin(radians);
                motionZ += forward * Math.cos(radians) + strafe * Math.sin(radians);
            }
            double wantedX = motionX, wantedY = motionY, wantedZ = motionZ;
            List<AxisAlignedBB> collisions = player.worldObj.getCollidingBoundingBoxes(player, box.addCoord(wantedX, wantedY, wantedZ));
            for (AxisAlignedBB collision : collisions) wantedY = collision.calculateYOffset(box, wantedY);
            box = box.offset(0.0D, wantedY, 0.0D);
            for (AxisAlignedBB collision : collisions) wantedX = collision.calculateXOffset(box, wantedX);
            box = box.offset(wantedX, 0.0D, 0.0D);
            for (AxisAlignedBB collision : collisions) wantedZ = collision.calculateZOffset(box, wantedZ);
            box = box.offset(0.0D, 0.0D, wantedZ);
            x = (box.minX + box.maxX) * 0.5D;
            y = box.minY;
            z = (box.minZ + box.maxZ) * 0.5D;
            boolean collidedHorizontally = wantedX != motionX || wantedZ != motionZ;
            ground = wantedY != motionY && motionY < 0.0D;
            if (wantedX != motionX) motionX = 0.0D;
            if (wantedZ != motionZ) motionZ = 0.0D;
            if (wantedY != motionY) motionY = 0.0D;
            if (ground) fall = 0.0F; else fall += (float) Math.max(0.0D, -motionY);
            motionY = (motionY - 0.08D) * 0.98D;
            motionX *= friction;
            motionZ *= friction;
            result.add(new Snapshot(new Vec3(x, y, z), fall, ground, collidedHorizontally));
        }
        return result;
    }

    private static Vec3 closestPoint(Vec3 point, AxisAlignedBB box) {
        return new Vec3(clamp(point.xCoord, box.minX, box.maxX), clamp(point.yCoord, box.minY, box.maxY),
                clamp(point.zCoord, box.minZ, box.maxZ));
    }

    private static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }
    private static boolean down(int key) { return key != 0 && Keyboard.isKeyDown(key); }

    static final class Snapshot {
        final Vec3 position;
        final float fallDistance;
        final boolean onGround;
        final boolean collidedHorizontally;
        Snapshot(Vec3 position, float fallDistance, boolean onGround, boolean collidedHorizontally) {
            this.position = position;
            this.fallDistance = fallDistance;
            this.onGround = onGround;
            this.collidedHorizontally = collidedHorizontally;
        }
    }
}

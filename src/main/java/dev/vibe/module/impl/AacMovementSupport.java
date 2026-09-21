package dev.vibe.module.impl;

import net.minecraft.block.BlockCarpet;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import java.lang.reflect.Field;

/** Small 1.8.9 adapters for the movement helpers used by LiquidBounce's
 * legacy AAC modes.  The formulas intentionally follow MovementUtils rather
 * than the newer Vibe movement abstractions. */
final class AacMovementSupport {
    private AacMovementSupport() { }

    static boolean moving(EntityPlayerSP p) {
        return p != null && (p.movementInput.moveForward != 0.0F || p.movementInput.moveStrafe != 0.0F);
    }

    static void jump(EntityPlayerSP p) { if (p != null) p.jump(); }

    static void stopXZ(EntityPlayer p) { if (p != null) { p.motionX = 0.0D; p.motionZ = 0.0D; } }
    static void stopY(EntityPlayer p) { if (p != null) p.motionY = 0.0D; }

    static void speedInAir(EntityPlayer p, float value) {
        if (p == null) return;
        try {
            if (SPEED_IN_AIR == null) {
                SPEED_IN_AIR = EntityPlayer.class.getDeclaredField("speedInAir");
                SPEED_IN_AIR.setAccessible(true);
            }
            SPEED_IN_AIR.setFloat(p, value);
        } catch (Throwable ignored) { }
    }
    private static Field SPEED_IN_AIR;

    static double horizontalSpeed(EntityPlayer p) {
        return p == null ? 0.0D : Math.sqrt(p.motionX * p.motionX + p.motionZ * p.motionZ);
    }

    /** Equivalent to MovementUtils.strafe(speed); no input leaves the current
     * velocity alone, which is important for AAC1.9.10's second strafe call. */
    static void strafe(EntityPlayerSP p, double speed) {
        if (p == null || !moving(p)) return;
        float forward = p.movementInput.moveForward;
        float side = p.movementInput.moveStrafe;
        float yaw = p.rotationYaw;
        if (forward < 0.0F) { yaw += 180.0F; forward = -0.5F; }
        else if (forward > 0.0F) forward = 0.5F;
        else forward = 1.0F;
        if (side > 0.0F) yaw -= 90.0F * forward;
        else if (side < 0.0F) yaw += 90.0F * forward;
        double radians = Math.toRadians(yaw);
        p.motionX = -Math.sin(radians) * speed;
        p.motionZ = Math.cos(radians) * speed;
    }

    static boolean inLiquid(EntityPlayer p) { return p != null && (p.isInWater() || p.isInLava()); }

    static boolean onCarpet(EntityPlayer p) {
        if (p == null || p.worldObj == null) return false;
        BlockPos pos = new BlockPos(p.posX, p.posY, p.posZ);
        return p.worldObj.getBlockState(pos).getBlock() instanceof BlockCarpet;
    }

    /** The legacy FallingPlayer check is a nine-ray simulation.  Keeping the
     * same damping/acceleration and offsets avoids the false void result that
     * a single straight ray produces at slab and stair edges. */
    static boolean hasCollisionWithin(EntityPlayerSP player, int ticks) {
        if (player == null || player.worldObj == null) return true;
        double x = player.posX, y = player.posY, z = player.posZ;
        double mx = player.motionX, my = player.motionY, mz = player.motionZ;
        float strafe = player.movementInput.moveStrafe;
        float forward = player.movementInput.moveForward;
        float yaw = player.rotationYaw;
        for (int i = 0; i < ticks; i++) {
            Vec3 start = new Vec3(x, y, z);
            strafe *= 0.98F;
            forward *= 0.98F;
            float magnitude = strafe * strafe + forward * forward;
            if (magnitude >= 0.0001F) {
                magnitude = player.jumpMovementFactor / Math.max(1.0F, (float) Math.sqrt(magnitude));
                strafe *= magnitude;
                forward *= magnitude;
                double radians = Math.toRadians(yaw);
                mx += strafe * Math.cos(radians) - forward * Math.sin(radians);
                mz += forward * Math.cos(radians) + strafe * Math.sin(radians);
            }
            my -= 0.08D;
            mx *= 0.91D;
            my *= 0.9800000190734863D;
            my *= 0.91D;
            mz *= 0.91D;
            x += mx; y += my; z += mz;
            Vec3 end = new Vec3(x, y, z);
            if (ray(player, start, end)) return true;
        }
        return false;
    }

    private static boolean ray(EntityPlayer p, Vec3 start, Vec3 end) {
        double[][] offsets = {{0, 0}, {.3, .3}, {-.3, .3}, {.3, -.3}, {-.3, -.3}, {.3, .15}, {-.3, .15}, {.15, .3}, {.15, -.3}};
        for (double[] offset : offsets) {
            MovingObjectPosition hit = p.worldObj.rayTraceBlocks(start.addVector(offset[0], 0.0D, offset[1]),
                    end.addVector(offset[0], 0.0D, offset[1]), true, false, false);
            if (hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK && hit.sideHit == EnumFacing.UP) return true;
        }
        return false;
    }
}

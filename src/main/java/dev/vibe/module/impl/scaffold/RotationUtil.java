/*
 * Ported from Gothaj (CFR 0.152), revision 8aa9e1c056bc5b6393b29dc03bcb06b529b40a07.
 * Source mapping and Forge adaptations: LICENSES/GOTHAJ-SCAFFOLD.md.
 */
package dev.vibe.module.impl.scaffold;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.projectile.EntityEgg;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

public final class RotationUtil {
    public static final Minecraft mc = Minecraft.getMinecraft();
    public static float[] getDirectionToBlock(double x, double y, double z, EnumFacing enumfacing) {
        EntityEgg face = new EntityEgg(RotationUtil.mc.theWorld);
        face.posX = x + 0.5;
        face.posY = y + 0.5;
        face.posZ = z + 0.5;
        face.posX += (double)enumfacing.getDirectionVec().getX() * 0.5;
        face.posY += (double)enumfacing.getDirectionVec().getY() * 0.5;
        face.posZ += (double)enumfacing.getDirectionVec().getZ() * 0.5;
        return RotationUtil.getRotationFromPosition(face.posX, face.posY, face.posZ);
    }

    public static boolean lookingAtBlock(BlockPos blockPos, float yaw, float pitch, EnumFacing enumFacing, boolean strict) {
        MovingObjectPosition movingObjectPosition = RotationUtil.mc.objectMouseOver;
        if (movingObjectPosition == null || movingObjectPosition.getBlockPos() == null) {
            return false;
        }
        Vec3 hitVec = movingObjectPosition.hitVec;
        if (hitVec == null) {
            return false;
        }
        return movingObjectPosition.getBlockPos().equals(blockPos) && (!strict || movingObjectPosition.sideHit == enumFacing);
    }

    public static float getYawBasedPitch(BlockPos blockPos, EnumFacing facing, float currentYaw, float lastPitch, int maxPitch) {
        if (RotationUtil.lookingAtBlock(blockPos, currentYaw, lastPitch, facing, true)) {
            return lastPitch;
        }
        for (float i = 70.0f; i <= (float)maxPitch; i += 0.1f) {
            if (!RotationUtil.lookingAtBlock(blockPos, currentYaw, i, facing, true)) continue;
            return i;
        }
        return lastPitch;
    }

    public static float updateRotation(float current, float intended, float factor) {
        float var4 = MathHelper.wrapAngleTo180_float(intended - current);
        if (var4 > factor) {
            var4 = factor;
        }
        if (var4 < -factor) {
            var4 = -factor;
        }
        return current + var4;
    }

    public static float smoothRotation(float from, float to, float speed) {
        float f = MathHelper.wrapAngleTo180_float(to - from);
        if (f > speed) {
            f = speed;
        }
        if (f < -speed) {
            f = -speed;
        }
        return from + f;
    }

    public static float[] getRotationFromPosition(double x, double y, double z) {
        double xDiff = x - Minecraft.getMinecraft().thePlayer.posX;
        double zDiff = z - Minecraft.getMinecraft().thePlayer.posZ;
        double yDiff = y - Minecraft.getMinecraft().thePlayer.posY - 1.2;
        double dist = MathHelper.sqrt_double(xDiff * xDiff + zDiff * zDiff);
        float yaw = (float)(Math.atan2(zDiff, xDiff) * 180.0 / Math.PI) - 90.0f;
        float pitch = (float)(-(Math.atan2(yDiff, dist) * 180.0 / Math.PI));
        return new float[]{yaw, pitch};
    }

    public static float[] getFixedRotations(float[] rotations, float[] lastRotations) {
        float yaw = rotations[0];
        float pitch = rotations[1];
        float lastYaw = lastRotations[0];
        float lastPitch = lastRotations[1];
        float f = RotationUtil.mc.gameSettings.mouseSensitivity * 0.6f + 0.2f;
        float gcd = f * f * f * 1.2f;
        float deltaYaw = yaw - lastYaw;
        float deltaPitch = pitch - lastPitch;
        float fixedDeltaYaw = deltaYaw - deltaYaw % gcd;
        float fixedDeltaPitch = deltaPitch - deltaPitch % gcd;
        float fixedYaw = lastYaw + fixedDeltaYaw;
        float fixedPitch = lastPitch + fixedDeltaPitch;
        return new float[]{fixedYaw, fixedPitch};
    }
}

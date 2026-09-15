package dev.vibe.module.impl;

import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.setting.RangeSetting;
import java.util.List;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import org.lwjgl.input.Keyboard;

/**
 * Extends the local mouse-over ray without changing inventory, combat or
 * packet selection logic. The two ranges are sampled independently from
 * their configured min/max values and applied to the next ray calculation.
 */
public final class ReachModule extends Module {
    private final RangeSetting reachExtension = addSetting(new RangeSetting(
            "Reach Extension", 0.20D, 0.20D, 0.0D, 3.0D, 0.05D));
    private final RangeSetting blockReachExtension = addSetting(new RangeSetting(
            "Block Reach Extension", 0.0D, 0.0D, 0.0D, 3.0D, 0.05D));
    private final Minecraft minecraft = Minecraft.getMinecraft();
    private final Random random = new Random();
    private long nextSample;
    private double activeReach;
    private double activeBlockReach;

    public ReachModule() {
        super("Reach", "Adjust the client mouse-over reach for entities and blocks", Category.COMBAT, Keyboard.KEY_NONE);
    }

    public void tick() {
        if (!isEnabled() || minecraft.thePlayer == null || minecraft.theWorld == null || minecraft.currentScreen != null
                || minecraft.playerController == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now >= nextSample) {
            activeReach = randomBetween(reachExtension);
            activeBlockReach = randomBetween(blockReachExtension);
            nextSample = now + 150L;
        }
        if (activeReach <= 0.0D && activeBlockReach <= 0.0D) return;

        double entityRange = 3.0D + activeReach;
        double blockRange = minecraft.playerController.getBlockReachDistance() + activeBlockReach;
        double maxRange = Math.max(entityRange, blockRange);
        Vec3 eyes = minecraft.thePlayer.getPositionEyes(1.0F);
        Vec3 look = minecraft.thePlayer.getLook(1.0F);
        Vec3 end = eyes.addVector(look.xCoord * maxRange, look.yCoord * maxRange, look.zCoord * maxRange);
        MovingObjectPosition blockHit = minecraft.theWorld.rayTraceBlocks(eyes, end, false, false, true);
        double blockDistance = blockHit == null ? blockRange * blockRange : eyes.squareDistanceTo(blockHit.hitVec);
        EntityLivingBase closest = null;
        Vec3 closestHit = null;
        double closestDistance = entityRange * entityRange;
        AxisAlignedBB search = minecraft.thePlayer.getEntityBoundingBox()
                .addCoord(look.xCoord * entityRange, look.yCoord * entityRange, look.zCoord * entityRange)
                .expand(1.0D, 1.0D, 1.0D);
        List<?> entities = minecraft.theWorld.getEntitiesWithinAABBExcludingEntity(minecraft.thePlayer, search);
        for (Object value : entities) {
            if (!(value instanceof EntityLivingBase)) continue;
            EntityLivingBase entity = (EntityLivingBase) value;
            if (entity == minecraft.thePlayer || !entity.canBeCollidedWith() || entity.isDead) continue;
            float border = entity.getCollisionBorderSize();
            AxisAlignedBB box = entity.getEntityBoundingBox().expand(border, border, border);
            MovingObjectPosition intercept = box.calculateIntercept(eyes, end);
            if (intercept == null) continue;
            double distance = eyes.squareDistanceTo(intercept.hitVec);
            if (distance < closestDistance) {
                closest = entity;
                closestHit = intercept.hitVec;
                closestDistance = distance;
            }
        }

        // Preserve normal occlusion: an entity behind the first block remains
        // unselectable, while an extended block hit is still available.
        if (closest != null && closestDistance <= blockDistance && closestDistance <= entityRange * entityRange) {
            minecraft.objectMouseOver = new MovingObjectPosition(closest, closestHit);
        } else if (blockHit != null && eyes.squareDistanceTo(blockHit.hitVec) <= blockRange * blockRange) {
            minecraft.objectMouseOver = blockHit;
        }
    }

    private double randomBetween(RangeSetting setting) {
        double min = setting.getMin();
        double max = setting.getMax();
        return min + (max <= min ? 0.0D : random.nextDouble() * (max - min));
    }

    public RangeSetting getReachExtension() { return reachExtension; }
    public RangeSetting getBlockReachExtension() { return blockReachExtension; }
    public double getActiveReachExtension() { return isEnabled() ? activeReach : 0.0D; }
}

package dev.vibe.module.impl;

import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.setting.BooleanSetting;
import dev.vibe.setting.ColorSetting;
import dev.vibe.setting.NumberSetting;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.entity.projectile.EntityEgg;
import net.minecraft.entity.projectile.EntityPotion;
import net.minecraft.entity.item.EntityEnderPearl;
import net.minecraft.entity.item.EntityExpBottle;
import net.minecraft.entity.projectile.EntitySnowball;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemExpBottle;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemEgg;
import net.minecraft.item.ItemEnderPearl;
import net.minecraft.item.ItemSnowball;
import net.minecraft.util.Vec3;
import org.lwjgl.input.Keyboard;

/** Predicts the vanilla path of the projectile currently held by the player. */
public final class TrajectoriesModule extends Module {
    private final NumberSetting lineWidth = addSetting(new NumberSetting("Line Width", 2.0D, 1.0D, 5.0D, .1D));
    private final NumberSetting maxTicks = addSetting(new NumberSetting("Max Ticks", 100.0D, 30.0D, 200.0D, 10.0D));
    private final ColorSetting defaultColor = addSetting(new ColorSetting("Default Color", 0xFF8B76FF));
    private final ColorSetting enemyColor = addSetting(new ColorSetting("Enemy Hit", 0xFFFF3232));
    private final ColorSetting wallColor = addSetting(new ColorSetting("Wall Hit", 0xFF32FF32));
    private final ColorSetting groundColor = addSetting(new ColorSetting("Ground Hit", 0xFF55FFFF));
    private final BooleanSetting landingBox = addSetting(new BooleanSetting("Show Landing Box", true));
    private final BooleanSetting landingCross = addSetting(new BooleanSetting("Show Landing Cross", true));
    private final BooleanSetting landingBlock = addSetting(new BooleanSetting("Show Landing Block", true));
    private final BooleanSetting highlightEntities = addSetting(new BooleanSetting("Highlight Entities", true));
    private final BooleanSetting entityTrail = addSetting(new BooleanSetting("Entity Trail", true));
    private final BooleanSetting fadeOut = addSetting(new BooleanSetting("Fade Out", true));
    private final BooleanSetting addPlayerVelocity = addSetting(new BooleanSetting("Add Player Velocity", false));
    private final BooleanSetting rainbow = addSetting(new BooleanSetting("Rainbow", false));
    private final Minecraft minecraft = Minecraft.getMinecraft();
    private final Map<Integer, Deque<Vec3>> trails = new HashMap<Integer, Deque<Vec3>>();
    private Trajectory last;
    private long lastTrajectoryTime;

    public TrajectoriesModule() { super("Trajectories", "Predicts bow and throwable projectile paths", Category.VISUAL, Keyboard.KEY_NONE); }
    public NumberSetting getLineWidth() { return lineWidth; }
    public NumberSetting getMaxTicks() { return maxTicks; }
    public ColorSetting getDefaultColor() { return defaultColor; }
    public ColorSetting getEnemyColor() { return enemyColor; }
    public ColorSetting getWallColor() { return wallColor; }
    public ColorSetting getGroundColor() { return groundColor; }
    public BooleanSetting getLandingBox() { return landingBox; }
    public BooleanSetting getLandingCross() { return landingCross; }
    public BooleanSetting getLandingBlock() { return landingBlock; }
    public BooleanSetting getHighlightEntities() { return highlightEntities; }
    public BooleanSetting getEntityTrail() { return entityTrail; }
    public BooleanSetting getFadeOut() { return fadeOut; }
    public BooleanSetting getAddPlayerVelocity() { return addPlayerVelocity; }
    public BooleanSetting getRainbow() { return rainbow; }
    public Trajectory getLast() { return last; }
    public Map<Integer, Deque<Vec3>> getTrails() { return trails; }

    public void tick() {
        if (!isEnabled() || minecraft.theWorld == null || minecraft.thePlayer == null) { trails.clear(); last = null; return; }
        if (entityTrail.isEnabled()) {
            for (Object object : minecraft.theWorld.loadedEntityList) {
                if (!(object instanceof Entity)) continue;
                Entity entity = (Entity) object;
                if (!isProjectile(entity)) continue;
                Deque<Vec3> points = trails.get(entity.getEntityId());
                if (points == null) { points = new ArrayDeque<Vec3>(); trails.put(entity.getEntityId(), points); }
                points.addLast(new Vec3(entity.posX, entity.posY + entity.height * .5D, entity.posZ));
                while (points.size() > 80) points.removeFirst();
            }
            java.util.Iterator<Integer> ids = trails.keySet().iterator();
            while (ids.hasNext()) { int id = ids.next(); if (minecraft.theWorld.getEntityByID(id) == null) ids.remove(); }
        } else trails.clear();
    }

    public Trajectory simulate(float partialTicks) {
        if (!isEnabled() || minecraft.thePlayer == null || minecraft.theWorld == null) return null;
        ItemStack held = minecraft.thePlayer.getHeldItem(); if (held == null) return null;
        double[] properties = properties(held, partialTicks); if (properties == null) return null;
        double gravity = properties[0], drag = properties[1], velocity = properties[2];
        float yaw = (float) Math.toRadians(minecraft.thePlayer.rotationYaw);
        float pitch = (float) Math.toRadians(minecraft.thePlayer.rotationPitch);
        double x = minecraft.thePlayer.prevPosX + (minecraft.thePlayer.posX - minecraft.thePlayer.prevPosX) * partialTicks - Math.cos(yaw) * .16D;
        double y = minecraft.thePlayer.prevPosY + (minecraft.thePlayer.posY - minecraft.thePlayer.prevPosY) * partialTicks + minecraft.thePlayer.getEyeHeight() - .1D;
        double z = minecraft.thePlayer.prevPosZ + (minecraft.thePlayer.posZ - minecraft.thePlayer.prevPosZ) * partialTicks - Math.sin(yaw) * .16D;
        double vx = -Math.sin(yaw) * Math.cos(pitch) * velocity, vy = -Math.sin(pitch) * velocity, vz = Math.cos(yaw) * Math.cos(pitch) * velocity;
        if (addPlayerVelocity.isEnabled()) { vx += minecraft.thePlayer.motionX; vy += minecraft.thePlayer.motionY; vz += minecraft.thePlayer.motionZ; }
        ListBuilder path = new ListBuilder(); path.add(x, y, z);
        int hitType = 0; Vec3 hit = null;
        for (int tick = 0; tick < maxTicks.getInt(); tick++) {
            double nx = x + vx, ny = y + vy, nz = z + vz;
            net.minecraft.util.MovingObjectPosition block = minecraft.theWorld.rayTraceBlocks(new Vec3(x, y, z), new Vec3(nx, ny, nz), false, true, false);
            Vec3 blockHit = block == null ? null : block.hitVec;
            Entity entityHit = nearestEntity(new Vec3(x, y, z), new Vec3(nx, ny, nz), .25D);
            double blockDistance = blockHit == null ? Double.MAX_VALUE : blockHit.squareDistanceTo(new Vec3(x, y, z));
            double entityDistance = entityHit == null ? Double.MAX_VALUE : entityHit.getEntityBoundingBox().calculateIntercept(new Vec3(x, y, z), new Vec3(nx, ny, nz)) == null ? Double.MAX_VALUE : entityHit.getEntityBoundingBox().calculateIntercept(new Vec3(x, y, z), new Vec3(nx, ny, nz)).hitVec.squareDistanceTo(new Vec3(x, y, z));
            if (entityHit != null && entityDistance < blockDistance) { hit = entityHit.getEntityBoundingBox().calculateIntercept(new Vec3(x, y, z), new Vec3(nx, ny, nz)).hitVec; hitType = 1; path.add(hit.xCoord, hit.yCoord, hit.zCoord); break; }
            if (blockHit != null) { hit = blockHit; hitType = block.sideHit == net.minecraft.util.EnumFacing.UP || block.sideHit == net.minecraft.util.EnumFacing.DOWN ? 3 : 2; path.add(hit.xCoord, hit.yCoord, hit.zCoord); break; }
            path.add(nx, ny, nz); x = nx; y = ny; z = nz; vx *= drag; vy = vy * drag - gravity; vz *= drag;
            if (y < -64.0D) break;
        }
        lastTrajectoryTime = System.currentTimeMillis(); last = new Trajectory(path.values, hit, hitType, lastTrajectoryTime); return last;
    }

    private Entity nearestEntity(Vec3 from, Vec3 to, double radius) {
        Entity best = null; double bestDistance = Double.MAX_VALUE;
        for (Object object : minecraft.theWorld.loadedEntityList) if (object instanceof Entity) {
            Entity entity = (Entity) object; if (entity == minecraft.thePlayer || !entity.canBeCollidedWith()) continue;
            net.minecraft.util.MovingObjectPosition hit = entity.getEntityBoundingBox().expand(radius, radius, radius).calculateIntercept(from, to);
            if (hit != null && hit.hitVec.squareDistanceTo(from) < bestDistance) { best = entity; bestDistance = hit.hitVec.squareDistanceTo(from); }
        }
        return best;
    }

    private double[] properties(ItemStack stack, float partialTicks) {
        if (stack.getItem() instanceof ItemBow) {
            float draw = 72000.0F - minecraft.thePlayer.getItemInUseCount() + partialTicks; float f = draw / 20.0F; f = (f*f + f*2.0F)/3.0F;
            return new double[]{.05D, .99D, Math.min(1.0F, f) * 2.0D * 1.5D};
        }
        if (stack.getItem() instanceof ItemEgg || stack.getItem() instanceof ItemSnowball || stack.getItem() instanceof ItemEnderPearl) return new double[]{.03D, .99D, 1.5D};
        if (stack.getItem() instanceof ItemExpBottle) return new double[]{.07D, .99D, .7D};
        if (stack.getItem() instanceof ItemPotion) return new double[]{.05D, .99D, .5D};
        return null;
    }

    private boolean isProjectile(Entity entity) { return entity instanceof EntityArrow || entity instanceof EntityEgg || entity instanceof EntityEnderPearl || entity instanceof EntityExpBottle || entity instanceof EntityPotion || entity instanceof EntitySnowball; }
    @Override protected void onDisable() { trails.clear(); last = null; }

    public static final class Trajectory {
        public final java.util.List<Vec3> points; public final Vec3 hit; public final int hitType; public final long created;
        private Trajectory(java.util.List<Vec3> points, Vec3 hit, int hitType, long created) { this.points=points; this.hit=hit; this.hitType=hitType; this.created=created; }
    }
    private static final class ListBuilder {
        private final java.util.List<Vec3> values = new java.util.ArrayList<Vec3>();
        private void add(double x,double y,double z){values.add(new Vec3(x,y,z));}
    }
}

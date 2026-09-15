package dev.vibe.module.impl;

import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.network.PacketDelayService;
import dev.vibe.setting.BooleanSetting;
import dev.vibe.setting.ColorSetting;
import dev.vibe.setting.ModeSetting;
import dev.vibe.setting.NumberSetting;
import dev.vibe.setting.RangeSetting;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S06PacketUpdateHealth;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S13PacketDestroyEntities;
import net.minecraft.network.play.server.S14PacketEntity;
import net.minecraft.network.play.server.S18PacketEntityTeleport;
import net.minecraft.network.play.server.S19PacketEntityHeadLook;
import net.minecraft.network.play.server.S27PacketExplosion;
import net.minecraft.network.play.server.S40PacketDisconnect;

/**
 * Tracks a single attacked target and holds only its ordered movement stream.
 * The conservative distance check prevents stale visual positions from being
 * used after the server-side target has moved outside normal melee reach.
 */
public final class BacktrackModule extends Module {

    public enum PacketAction { PASS, QUEUE, FLUSH }

    private final Minecraft minecraft = Minecraft.getMinecraft();
    private final ModeSetting mode = addSetting(new ModeSetting("Mode", "Modern", "Modern", "Legacy"));
    private final RangeSetting latency = addSetting(new RangeSetting("Latency (ms)", 50.0D, 100.0D, 0.0D, 500.0D, 1.0D));
    private final NumberSetting trackingTime = addSetting(new NumberSetting("Tracking Time (ms)", 950.0D, 100.0D, 2500.0D, 25.0D));
    private final NumberSetting safeDistance = addSetting(new NumberSetting("Safe Server Distance", 2.85D, 1.0D, 3.0D, 0.05D,
            () -> mode.is("Modern")));
    private final BooleanSetting pauseOnHurt = addSetting(new BooleanSetting("Pause On Hurt", true,
            () -> mode.is("Modern")));
    private final BooleanSetting renderServerPosition = addSetting(new BooleanSetting("Render Server Position", true));
    private final ColorSetting renderColor = addSetting(new ColorSetting("Server Position Color", 0x64058669,
            () -> renderServerPosition.isEnabled()));
    private int targetId = -1;
    private long targetExpires;
    private double serverX;
    private double serverY;
    private double serverZ;
    private boolean hasServerPosition;

    public BacktrackModule() {
        super("Backtrack", "Safely buffers target movement updates", Category.COMBAT, org.lwjgl.input.Keyboard.KEY_NONE);
    }

    public ModeSetting getMode() { return mode; }
    public RangeSetting getLatency() { return latency; }
    public NumberSetting getTrackingTime() { return trackingTime; }
    public NumberSetting getSafeDistance() { return safeDistance; }
    public BooleanSetting getPauseOnHurt() { return pauseOnHurt; }
    public BooleanSetting getRenderServerPosition() { return renderServerPosition; }
    public ColorSetting getRenderColor() { return renderColor; }
    public int getTargetId() { return targetId; }
    public boolean hasServerPosition() { return hasServerPosition; }
    public double getServerX() { return serverX; }
    public double getServerY() { return serverY; }
    public double getServerZ() { return serverZ; }

    public void onAttack(EntityLivingBase entity) {
        if (!isEnabled() || entity == null) return;
        if (targetId == entity.getEntityId() && hasServerPosition) {
            DebugModule.log("Hit on Backtrack", "hit delayed target " + entity.getName());
        }
        if (targetId >= 0 && targetId != entity.getEntityId()) PacketDelayService.getInstance().flush(PacketDelayService.Owner.BACKTRACK);
        targetId = entity.getEntityId();
        targetExpires = System.currentTimeMillis() + trackingTime.getInt();
        serverX = entity.posX;
        serverY = entity.posY;
        serverZ = entity.posZ;
        hasServerPosition = true;
    }

    public void tick() {
        Entity entity = minecraft.theWorld == null || targetId < 0 ? null : minecraft.theWorld.getEntityByID(targetId);
        if (entity == null || entity.isDead || System.currentTimeMillis() > targetExpires
                || (pauseOnHurt.isEnabled() && entity instanceof EntityLivingBase && ((EntityLivingBase) entity).hurtTime > 0)) {
            reset(true);
        }
    }

    public int getRandomLatency() {
        int min = latency.getMinInt();
        int max = latency.getMaxInt();
        return min >= max ? min : ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    public PacketAction classify(Packet<?> packet) {
        if (isImmediateBoundary(packet)) return PacketAction.FLUSH;
        if (targetId < 0 || minecraft.theWorld == null || System.currentTimeMillis() > targetExpires) return PacketAction.PASS;
        Entity entity = entityFrom(packet);
        if (entity == null || entity.getEntityId() != targetId) return PacketAction.PASS;
        if (!(packet instanceof S14PacketEntity) && !(packet instanceof S18PacketEntityTeleport) && !(packet instanceof S19PacketEntityHeadLook)) {
            return PacketAction.PASS;
        }
        if (packet instanceof S18PacketEntityTeleport) return PacketAction.FLUSH;
        if (pauseOnHurt.isEnabled() && entity instanceof EntityLivingBase && ((EntityLivingBase) entity).hurtTime > 0) return PacketAction.FLUSH;
        if (packet instanceof S14PacketEntity && mode.is("Modern") && !isConservativelySafe(entity, (S14PacketEntity) packet)) {
            return PacketAction.FLUSH;
        }
        return PacketAction.QUEUE;
    }

    private boolean isImmediateBoundary(Packet<?> packet) {
        if (packet instanceof S08PacketPlayerPosLook || packet instanceof S40PacketDisconnect || packet instanceof S27PacketExplosion) return true;
        if (packet instanceof S12PacketEntityVelocity && minecraft.thePlayer != null
                && ((S12PacketEntityVelocity) packet).getEntityID() == minecraft.thePlayer.getEntityId()) return true;
        if (packet instanceof S06PacketUpdateHealth && ((S06PacketUpdateHealth) packet).getHealth() <= 0.0F) return true;
        if (packet instanceof S13PacketDestroyEntities) {
            for (int id : ((S13PacketDestroyEntities) packet).getEntityIDs()) if (id == targetId) return true;
        }
        return false;
    }

    private boolean isConservativelySafe(Entity entity, S14PacketEntity packet) {
        double baseX = hasServerPosition ? serverX : entity.posX;
        double baseY = hasServerPosition ? serverY : entity.posY;
        double baseZ = hasServerPosition ? serverZ : entity.posZ;
        double nextX = baseX + packet.func_149062_c() / 32.0D;
        double nextY = baseY + packet.func_149061_d() / 32.0D;
        double nextZ = baseZ + packet.func_149064_e() / 32.0D;
        double current = boxDistance(entity.posX, entity.posY, entity.posZ, entity);
        double predicted = boxDistance(nextX, nextY, nextZ, entity);
        return predicted >= current - 0.015D && predicted <= safeDistance.getDouble();
    }

    private double boxDistance(double x, double y, double z, Entity entity) {
        double half = entity.width * .5D;
        double dx = Math.max(0.0D, Math.abs(minecraft.thePlayer.posX - x) - half);
        double dz = Math.max(0.0D, Math.abs(minecraft.thePlayer.posZ - z) - half);
        double eye = minecraft.thePlayer.posY + minecraft.thePlayer.getEyeHeight();
        double dy = Math.max(0.0D, Math.abs(eye - (y + entity.height * .5D)) - entity.height * .5D);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public void observeDelayedPacket(Packet<?> packet) {
        Entity entity = entityFrom(packet);
        if (entity == null) return;
        if (packet instanceof S14PacketEntity) {
            S14PacketEntity move = (S14PacketEntity) packet;
            serverX = (hasServerPosition ? serverX : entity.posX) + move.func_149062_c() / 32.0D;
            serverY = (hasServerPosition ? serverY : entity.posY) + move.func_149061_d() / 32.0D;
            serverZ = (hasServerPosition ? serverZ : entity.posZ) + move.func_149064_e() / 32.0D;
        } else if (packet instanceof S18PacketEntityTeleport) {
            S18PacketEntityTeleport teleport = (S18PacketEntityTeleport) packet;
            serverX = teleport.getX() / 32.0D;
            serverY = teleport.getY() / 32.0D;
            serverZ = teleport.getZ() / 32.0D;
        } else {
            serverX = hasServerPosition ? serverX : entity.posX;
            serverY = hasServerPosition ? serverY : entity.posY;
            serverZ = hasServerPosition ? serverZ : entity.posZ;
        }
        hasServerPosition = true;
    }

    private Entity entityFrom(Packet<?> packet) {
        if (minecraft.theWorld == null) return null;
        if (packet instanceof S14PacketEntity) return ((S14PacketEntity) packet).getEntity(minecraft.theWorld);
        if (packet instanceof S19PacketEntityHeadLook) return ((S19PacketEntityHeadLook) packet).getEntity(minecraft.theWorld);
        if (packet instanceof S18PacketEntityTeleport) return minecraft.theWorld.getEntityByID(((S18PacketEntityTeleport) packet).getEntityId());
        return null;
    }

    private void reset(boolean flush) {
        if (flush) PacketDelayService.getInstance().flush(PacketDelayService.Owner.BACKTRACK);
        targetId = -1;
        targetExpires = 0L;
        hasServerPosition = false;
    }

    @Override protected void onDisable() { reset(true); }
}

package dev.vibe.module.impl;

import dev.vibe.Vibe;
import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.network.PacketDelayService;
import dev.vibe.setting.BooleanSetting;
import dev.vibe.setting.ModeSetting;
import dev.vibe.setting.NumberSetting;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.util.ChatComponentText;
import org.lwjgl.input.Keyboard;

/** Direct Vibe bridge of Gothaj's LagRange combat module. */
public final class LagRangeModule extends Module {
    private final ModeSetting releaseMode = addSetting(new ModeSetting("Release Mode", "Smart", "Smart", "Delay", "Full"));
    private final BooleanSetting onlyKillAura = addSetting(new BooleanSetting("Only Kill Aura", true));
    private final NumberSetting delay = addSetting(new NumberSetting("Delay", 150.0D, 50.0D, 1000.0D, 50.0D,
            () -> releaseMode.is("Delay")));
    private final NumberSetting lag = addSetting(new NumberSetting("Lag", 150.0D, 50.0D, 1000.0D, 50.0D,
            () -> releaseMode.is("Full")));
    private final NumberSetting smartReleaseDistance = addSetting(new NumberSetting("Release Distance", 3.0D, 0.0D, 3.0D, 0.05D,
            () -> releaseMode.is("Smart")));
    private final NumberSetting smartSafeDistance = addSetting(new NumberSetting("Safe Distance", 5.0D, 3.0D, 10.0D, 0.05D,
            () -> releaseMode.is("Smart")));
    private final BooleanSetting debug = addSetting(new BooleanSetting("Debug", false));
    private final Minecraft minecraft = Minecraft.getMinecraft();
    private long timer;
    private long hitTimer;
    private EntityLivingBase target;
    private volatile boolean blinking;
    private Object sessionWorld;

    public LagRangeModule() { super("LagRange", "blink to give you reach and move you out of target reach", Category.COMBAT, Keyboard.KEY_NONE); }

    /** Called on Netty's outbound path; it only reads this main-thread state. */
    public boolean shouldBlink(Packet<?> packet) { return isEnabled() && blinking && packet instanceof C03PacketPlayer; }

    public void tick() {
        if (!isEnabled()) { stopBlinking(); return; }
        synchronizeWorld();
        if (!canWork()) { stopBlinking(); timer = System.currentTimeMillis(); return; }
        if ((target.hurtTime <= 0 && inHitRange() && !releaseMode.is("Smart"))
                || !CombatRangeSupport.isMoving() || isPlayerMovingBackwards()) {
            if (inHitRange() && blinking && debug.isEnabled()) chat("Lag Range > hit on target");
            stopBlinking();
            timer = System.currentTimeMillis();
            return;
        }
        blinking = true;
        if (releaseMode.is("Delay")) {
            PacketDelayService.getInstance().releaseOutboundOlderThan(PacketDelayService.Owner.LAG_RANGE, delay.getInt());
        } else if (releaseMode.is("Full")) {
            if (System.currentTimeMillis() - timer >= lag.getInt()) stopBlinking();
        } else {
            tickSmart();
        }
    }

    private void tickSmart() {
        if (CombatRangeSupport.distanceToBox(target) > smartSafeDistance.getDouble() || isPlayerMovingBackwards()
                || minecraft.thePlayer.hurtTime > 0) {
            stopBlinking();
            return;
        }
        int sendCount = getSendCountToBestPosition(smartSafeDistance.getFloat(), target);
        float releaseDistance = isTargetMovingBackwards() ? 3.0F : smartReleaseDistance.getFloat();
        sendCount = getSendCountToBestPosition(releaseDistance, target);
        if (getDistanceToTarget(firstC03(), target) <= releaseDistance) sendCount = 0;
        for (int index = 0; index < sendCount; index++) {
            PacketDelayService.getInstance().releaseNextOutbound(PacketDelayService.Owner.LAG_RANGE);
            if (!inHitRange()) continue;
            if (debug.isEnabled()) chat("Lag Range > hit on target");
            if (System.currentTimeMillis() - hitTimer < 250L) continue;
            hitTimer = System.currentTimeMillis();
            minecraft.getNetHandler().addToSendQueue(new C0APacketAnimation());
            minecraft.getNetHandler().addToSendQueue(new C02PacketUseEntity(target, C02PacketUseEntity.Action.ATTACK));
        }
        PacketDelayService.getInstance().releaseOutboundOlderThan(PacketDelayService.Owner.LAG_RANGE, 1000L);
    }

    private boolean canWork() {
        if (minecraft.thePlayer == null || minecraft.theWorld == null) { target = null; return false; }
        target = CombatRangeSupport.getTarget(20.0D);
        if (target == null) return false;
        KillAuraModule aura = Vibe.getInstance().getModuleManager().getModule(KillAuraModule.class);
        if ((aura == null || !aura.isEnabled()) && onlyKillAura.isEnabled()
                || minecraft.thePlayer.ticksExisted < 10 || CombatRangeSupport.isInWeb(minecraft.thePlayer) || minecraft.thePlayer.isInLava()
                || minecraft.thePlayer.isInWater() || minecraft.thePlayer.isCollidedHorizontally
                || !minecraft.gameSettings.keyBindForward.isKeyDown() || minecraft.thePlayer.hurtTime > 0) {
            target = null;
            return false;
        }
        return true;
    }

    private double getDistanceToTarget(C03PacketPlayer packet, EntityLivingBase value) {
        return packet == null || value == null ? Double.MAX_VALUE
                : CombatRangeSupport.distanceToBoxFromPosition(packet.getPositionX(), packet.getPositionY(), packet.getPositionZ(), value);
    }

    private int getSendCountToBestPosition(float distance, EntityLivingBase value) {
        int count = 0;
        for (Packet<?> packet : PacketDelayService.getInstance().queuedOutbound(PacketDelayService.Owner.LAG_RANGE)) {
            count++;
            if (packet instanceof C03PacketPlayer && getDistanceToTarget((C03PacketPlayer) packet, value) <= distance) return count;
        }
        return 0;
    }

    private C03PacketPlayer firstC03() {
        List<Packet<?>> packets = PacketDelayService.getInstance().queuedOutbound(PacketDelayService.Owner.LAG_RANGE);
        for (Packet<?> packet : packets) if (packet instanceof C03PacketPlayer) return (C03PacketPlayer) packet;
        return null;
    }

    private boolean inHitRange() { return CombatRangeSupport.distanceToBox(target) <= 3.0D; }
    private boolean isTargetMovingBackwards() { return minecraft.thePlayer.getDistance(target.posX, target.posY, target.posZ)
            >= minecraft.thePlayer.getDistance(target.lastTickPosX, target.lastTickPosY, target.lastTickPosZ); }
    private boolean isPlayerMovingBackwards() { return CombatRangeSupport.distanceToBox(target)
            > CombatRangeSupport.distanceToBoxFromPosition(minecraft.thePlayer.lastTickPosX, minecraft.thePlayer.lastTickPosY,
                    minecraft.thePlayer.lastTickPosZ, target); }
    private void stopBlinking() { blinking = false; PacketDelayService.getInstance().flush(PacketDelayService.Owner.LAG_RANGE); }
    private void chat(String message) { if (minecraft.thePlayer != null) minecraft.thePlayer.addChatMessage(new ChatComponentText(message)); }
    @Override protected void onEnable() { timer = System.currentTimeMillis(); sessionWorld = minecraft.theWorld; }
    @Override protected void onDisable() { target = null; sessionWorld = null; stopBlinking(); }
    private void synchronizeWorld() {
        Object world = minecraft.theWorld;
        if (world == sessionWorld) return;
        sessionWorld = world;
        target = null;
        timer = System.currentTimeMillis();
        stopBlinking();
    }
}

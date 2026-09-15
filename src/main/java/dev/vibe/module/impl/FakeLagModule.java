package dev.vibe.module.impl;

import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.network.PacketDelayService;
import dev.vibe.setting.ModeSetting;
import dev.vibe.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C00PacketKeepAlive;
import net.minecraft.network.play.client.C01PacketChatMessage;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.network.play.client.C0DPacketCloseWindow;
import net.minecraft.network.play.client.C0EPacketClickWindow;
import net.minecraft.network.play.client.C0FPacketConfirmTransaction;

/**
 * A protocol-safe movement queue. Action, window, acknowledgement and
 * interaction traffic is deliberately never delayed: queued movement is
 * dispatched first and the action then retains vanilla ordering.
 */
public final class FakeLagModule extends Module {

    public enum PacketAction { PASS, QUEUE, FLUSH }

    private final Minecraft minecraft = Minecraft.getMinecraft();
    private final ModeSetting mode = addSetting(new ModeSetting("Mode", "Latency", "Latency", "Repel"));
    private final NumberSetting delay = addSetting(new NumberSetting("Delay (ms)", 100.0D, 1.0D, 1000.0D, 1.0D));
    private long repelUntil;
    private long lastDebugAt;

    public FakeLagModule() {
        super("FakeLag", "Queues movement packets while preserving action order", Category.WORLD, org.lwjgl.input.Keyboard.KEY_NONE);
    }

    public ModeSetting getMode() { return mode; }
    public NumberSetting getDelay() { return delay; }

    public void onAttack() {
        if (isEnabled() && mode.is("Repel")) repelUntil = System.currentTimeMillis() + delay.getInt();
    }

    public PacketAction classify(Packet<?> packet) {
        if (!isEnabled() || minecraft.thePlayer == null || minecraft.theWorld == null) return PacketAction.FLUSH;
        if (minecraft.currentScreen != null) return PacketAction.FLUSH;
        if (isActionBoundary(packet)) return PacketAction.FLUSH;
        if (!(packet instanceof C03PacketPlayer)) return PacketAction.PASS;
        boolean shouldQueue = mode.is("Latency") || System.currentTimeMillis() < repelUntil;
        if (shouldQueue) {
            long now = System.currentTimeMillis();
            if (now - lastDebugAt > 750L) {
                lastDebugAt = now;
                DebugModule.log("Performed FakeLag", "queued movement");
            }
            return PacketAction.QUEUE;
        }
        return PacketAction.PASS;
    }

    private boolean isActionBoundary(Packet<?> packet) {
        return packet instanceof C00PacketKeepAlive
                || packet instanceof C01PacketChatMessage
                || packet instanceof C02PacketUseEntity
                || packet instanceof C07PacketPlayerDigging
                || packet instanceof C08PacketPlayerBlockPlacement
                || packet instanceof C09PacketHeldItemChange
                || packet instanceof C0APacketAnimation
                || packet instanceof C0BPacketEntityAction
                || packet instanceof C0DPacketCloseWindow
                || packet instanceof C0EPacketClickWindow
                || packet instanceof C0FPacketConfirmTransaction;
    }

    @Override protected void onDisable() {
        repelUntil = 0L;
        lastDebugAt = 0L;
        PacketDelayService.getInstance().flush(PacketDelayService.Owner.FAKE_LAG);
    }
}

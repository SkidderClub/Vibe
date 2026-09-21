package dev.vibe.module.impl;

import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.setting.ModeSetting;
import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C03PacketPlayer.C04PacketPlayerPosition;
import org.lwjgl.input.Keyboard;

/** Legacy LiquidBounce AAC no-fall modes with explicit ground spoof modes. */
public final class NoFallModule extends Module {
    private final ModeSetting mode = addSetting(new ModeSetting("Mode", "No Ground", "No Ground", "Always On Ground",
            "AAC", "LAAC", "AAC3.3.11", "AAC3.3.15"));
    private final Minecraft minecraft = Minecraft.getMinecraft();
    private int currentState;
    private boolean jumped;
    private boolean serverOnGround;

    public NoFallModule() { super("NoFall", "Prevents fall damage using legacy AAC packet modes", Category.MOVEMENT, Keyboard.KEY_NONE); }

    public void tick() {
        if (!isEnabled() || minecraft.thePlayer == null || minecraft.currentScreen != null) return;
        EntityPlayerSP p = minecraft.thePlayer;
        if (AacMovementSupport.inLiquid(p)) return;
        if (mode.is("No Ground") || mode.is("Always On Ground")) return;
        if (mode.is("AAC")) {
            if (p.fallDistance > 2.0F) { send(new C03PacketPlayer(true)); currentState = 2; }
            else if (currentState == 2 && p.fallDistance < 2.0F) { p.motionY = 0.1D; currentState = 3; return; }
            if (currentState == 3) { p.motionY = 0.1D; currentState = 4; }
            else if (currentState == 4) { p.motionY = 0.1D; currentState = 5; }
            else if (currentState == 5) { p.motionY = 0.1D; currentState = 1; }
        } else if (mode.is("AAC3.3.11")) {
            if (p.fallDistance > 2.0F) {
                AacMovementSupport.stopXZ(p);
                send(new C04PacketPlayerPosition(p.posX, p.posY - 10E-4D, p.posZ, serverOnGround));
                send(new C03PacketPlayer(true));
            }
        } else if (mode.is("AAC3.3.15")) {
            if (!minecraft.isIntegratedServerRunning() && p.fallDistance > 2.0F) {
                send(new C04PacketPlayerPosition(p.posX, Double.NaN, p.posZ, false));
                p.fallDistance = -9999.0F;
            }
        } else {
            if (p.onGround) jumped = false;
            if (p.motionY > 0.0D) jumped = true;
            if (!jumped && p.onGround && !p.isOnLadder() && !CombatRangeSupport.isInWeb(p)) p.motionY = -6.0D;
            if (!jumped && !p.onGround && !p.isOnLadder() && !CombatRangeSupport.isInWeb(p) && p.motionY < 0.0D)
                AacMovementSupport.stopXZ(p);
        }
    }

    /** Called at the Netty boundary before any queued packet module. */
    public void handleOutbound(Packet<?> packet) {
        if (!isEnabled() || !(packet instanceof C03PacketPlayer)) return;
        C03PacketPlayer player = (C03PacketPlayer) packet;
        serverOnGround = readGround(player);
        if (mode.is("No Ground")) writeGround(player, false);
        else if (mode.is("Always On Ground")) writeGround(player, true);
    }

    private void send(Packet<?> packet) { if (minecraft.getNetHandler() != null) minecraft.getNetHandler().addToSendQueue(packet); }

    private static boolean readGround(C03PacketPlayer packet) {
        try { return groundField().getBoolean(packet); } catch (Throwable ignored) { return false; }
    }
    private static void writeGround(C03PacketPlayer packet, boolean value) {
        try { groundField().setBoolean(packet, value); } catch (Throwable ignored) { }
    }
    private static Field ground;
    private static Field groundField() throws NoSuchFieldException {
        if (ground != null) return ground;
        for (String name : new String[] {"onGround", "field_149474_g"}) {
            try { ground = C03PacketPlayer.class.getDeclaredField(name); ground.setAccessible(true); return ground; }
            catch (NoSuchFieldException ignored) { }
        }
        for (Field candidate : C03PacketPlayer.class.getDeclaredFields()) {
            if (candidate.getType() == Boolean.TYPE) { candidate.setAccessible(true); ground = candidate; return candidate; }
        }
        throw new NoSuchFieldException("C03 onGround");
    }

    @Override protected void onDisable() { currentState = 0; jumped = false; serverOnGround = false; }
}

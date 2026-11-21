package dev.vibe.movement;

import io.netty.buffer.Unpooled;
import java.util.ArrayDeque;
import java.util.Locale;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.server.*;
import net.minecraft.init.Items;

/** Only the Rewound game and disabler state needed by Flight. */
public final class AdjustFlightTracker {
    private final Minecraft mc = Minecraft.getMinecraft();
    private final Queue<Runnable> incoming = new ConcurrentLinkedQueue<>();
    private final ArrayDeque<Long> sent = new ArrayDeque<>();
    private volatile long pearlThrow;
    private long activeUntil, respawnUntil, started;
    private boolean pearlSpawned, inGame, ended, allowFlying;
    private String game = "UNKNOWN_GAME";

    public synchronized void outbound(final Packet<?> packet) {
        packetCount();
        sent.add(System.currentTimeMillis());
        if (packet instanceof C08PacketPlayerBlockPlacement) {
            final C08PacketPlayerBlockPlacement placement = (C08PacketPlayerBlockPlacement) packet;
            if (placement.getStack() != null && placement.getStack().getItem() == Items.ender_pearl)
                pearlThrow = System.currentTimeMillis();
        }
    }

    public synchronized int packetCount() {
        final long cutoff = System.currentTimeMillis() - 1000;
        while (!sent.isEmpty() && sent.peek() < cutoff)
            sent.remove();

        return sent.size();
    }

    public void receive(final Packet<?> packet, final Runnable damaged) {
        if (packet instanceof S3FPacketCustomPayload) {
            final S3FPacketCustomPayload payload = (S3FPacketCustomPayload) packet;
            if (!payload.getChannelName().startsWith("rewound:")) return;
            final byte[] data = new byte[payload.getBufferData().readableBytes()];
            payload.getBufferData().getBytes(payload.getBufferData().readerIndex(), data);
            incoming.add(() -> payload(payload.getChannelName(), data));
        } else if (packet instanceof S01PacketJoinGame || packet instanceof S08PacketPlayerPosLook
                || packet instanceof S0EPacketSpawnObject || packet instanceof S27PacketExplosion
                || packet instanceof S39PacketPlayerAbilities || packet instanceof S19PacketEntityStatus)
            incoming.add(() -> apply(packet, damaged));
    }

    public void tick() {
        Runnable action;
        while ((action = incoming.poll()) != null)
            action.run();
    }

    public void clear() {
        incoming.clear();
        reset();
    }

    private synchronized void reset() {
        sent.clear();
        activeUntil = pearlThrow = respawnUntil = started = 0;
        pearlSpawned = inGame = ended = allowFlying = false;
        game = "UNKNOWN_GAME";
    }

    public boolean active() {
        return System.currentTimeMillis() < activeUntil;
    }

    public void activate() {
        activeUntil = System.currentTimeMillis() + 3000;
    }

    public float progress() {
        return 1 - Math.max(0, activeUntil - System.currentTimeMillis()) / 3000.0F;
    }

    public boolean bedWars() {
        return game.equals("BEDWARS");
    }

    public boolean canDamage() {
        return inGame && !ended && started != 0 && !game.equals("PIT") && !game.equals("REPLAY")
                && (!bedWars() || System.currentTimeMillis() - started >= 5000);
    }

    private void apply(final Packet<?> packet, final Runnable damaged) {
        final long now = System.currentTimeMillis();
        if (packet instanceof S01PacketJoinGame) {
            reset();
            return;
        }

        if (mc.thePlayer == null) return;
        final int ping = mc.getNetHandler() == null || mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID()) == null
                ? 0 : mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID()).getResponseTime();
        if (packet instanceof S08PacketPlayerPosLook) {
            if (pearlThrow != 0 && now - pearlThrow < ping + 500 && pearlSpawned)
                activate();

            pearlSpawned = false;
            if (inGame && now < respawnUntil)
                activate();

            respawnUntil = 0;
        } else if (packet instanceof S0EPacketSpawnObject) {
            if (((S0EPacketSpawnObject) packet).getType() == 65 && pearlThrow != 0 && now - pearlThrow < ping + 70)
                pearlSpawned = true;
        } else if (packet instanceof S27PacketExplosion) {
            final S27PacketExplosion explosion = (S27PacketExplosion) packet;
            if (Math.sqrt(explosion.func_149149_c() * explosion.func_149149_c()
                    + explosion.func_149144_d() * explosion.func_149144_d()
                    + explosion.func_149147_e() * explosion.func_149147_e()) > 0.05)
                activate();
        } else if (packet instanceof S39PacketPlayerAbilities) {
            final boolean allowed = ((S39PacketPlayerAbilities) packet).isAllowFlying();
            if (inGame && allowFlying && !allowed)
                respawnUntil = now + 70;

            allowFlying = allowed;
        } else if (packet instanceof S19PacketEntityStatus) {
            final S19PacketEntityStatus status = (S19PacketEntityStatus) packet;
            if (status.getOpCode() == 2 && status.getEntity(mc.theWorld) == mc.thePlayer)
                damaged.run();
        }
    }

    private void payload(final String channel, final byte[] data) {
        if (channel.equals("rewound:hello")) {
            reset();
            return;
        }

        final PacketBuffer buffer = new PacketBuffer(Unpooled.wrappedBuffer(data));
        try {
            if (!buffer.readBoolean() || buffer.readVarIntFromBuffer() != 1) return;
            if (channel.equals("rewound:location")) {
                buffer.readStringFromBuffer(32767);
                final String type = optional(buffer), lobby = optional(buffer), mode = optional(buffer);
                optional(buffer);
                final String nextGame = type == null ? "UNKNOWN_GAME" : type.replace(" ", "_").toUpperCase(Locale.ROOT);
                if (!game.equals(nextGame)) {
                    ended = false;
                    if (nextGame.equals("PIT"))
                        inGame = true;
                }

                game = nextGame;
                if (mode != null)
                    inGame = true;
                else if (lobby != null)
                    inGame = false;
            } else if (channel.equals("rewound:game_start") || channel.equals("rewound:game_rejoin")) {
                for (int i = 0; i < 4; i++)
                    buffer.readStringFromBuffer(32767);

                if (started == 0)
                    started = System.currentTimeMillis();
            } else if (channel.equals("rewound:game_finish")) {
                for (int i = 0; i < 4; i++)
                    buffer.readStringFromBuffer(32767);

                buffer.readVarIntFromBuffer();
                buffer.readVarIntFromBuffer();
                ended = true;
            }
        } catch (RuntimeException malformed) {
            // An incomplete server payload must never authorize self-damage.
            inGame = false;
        } finally {
            buffer.release();
        }
    }

    private static String optional(final PacketBuffer buffer) {
        return buffer.readBoolean() ? buffer.readStringFromBuffer(32767) : null;
    }
}

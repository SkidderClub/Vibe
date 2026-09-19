package dev.vibe.network;

import dev.vibe.Vibe;
import dev.vibe.module.impl.BacktrackModule;
import dev.vibe.module.impl.FakeLagModule;
import dev.vibe.module.impl.GirlfriendModule;
import dev.vibe.module.impl.MoveFixModule;
import dev.vibe.module.impl.CuteVisualsModule;
import dev.vibe.script.ScriptRuntime;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;

/**
 * One ordered Netty interceptor shared by modules which deliberately delay a
 * packet.  Delaying through the original pipeline preserves packet ordering
 * and avoids reinjecting packets through Minecraft's receive queue.
 */
public final class PacketDelayService {

    public enum Owner { FAKE_LAG, BACKTRACK }

    private static final String HANDLER = "vibe_packet_delay";
    private static final PacketDelayService INSTANCE = new PacketDelayService();

    private final Minecraft minecraft = Minecraft.getMinecraft();
    private final List<Entry> inbound = new CopyOnWriteArrayList<Entry>();
    private final List<Entry> outbound = new CopyOnWriteArrayList<Entry>();
    private volatile Channel installedChannel;
    private volatile ChannelHandlerContext context;
    private volatile long lastInboundRelease;
    private volatile long lastOutboundRelease;

    private PacketDelayService() {
    }

    public static PacketDelayService getInstance() {
        return INSTANCE;
    }

    /** Called from the client tick, after a connection has been established. */
    public void tick() {
        NetworkManager manager = minecraft.getNetHandler() == null ? null : minecraft.getNetHandler().getNetworkManager();
        Channel channel = manager == null ? null : manager.channel();
        if (channel == null || !channel.isOpen()) {
            flushAll();
            installedChannel = null;
            context = null;
            return;
        }
        if (channel != installedChannel) install(channel);
        releaseDue();
    }

    private void install(final Channel channel) {
        final Channel previous = installedChannel;
        installedChannel = channel;
        channel.eventLoop().execute(new Runnable() {
            @Override public void run() {
                if (previous != null && previous != channel && previous.pipeline().get(HANDLER) != null) {
                    previous.pipeline().remove(HANDLER);
                }
                if (channel.pipeline().get(HANDLER) == null) {
                    if (channel.pipeline().get("packet_handler") != null) {
                        channel.pipeline().addBefore("packet_handler", HANDLER, new DelayHandler());
                    } else {
                        channel.pipeline().addLast(HANDLER, new DelayHandler());
                    }
                }
            }
        });
    }

    private boolean delayOutbound(Packet<?> packet, ChannelHandlerContext ctx, ChannelPromise promise) {
        MoveFixModule moveFix = Vibe.getInstance() == null ? null
                : Vibe.getInstance().getModuleManager().getModule(MoveFixModule.class);
        if (moveFix != null) moveFix.applyToOutgoing(packet);
        // These modules receive the exact client-to-server dig lifecycle.
        // They queue it for their next main-thread tick, so this Netty path
        // never reads or mutates the Minecraft world directly.
        if (packet instanceof C07PacketPlayerDigging && Vibe.getInstance() != null) {
            // Statistics consumes the completed destroy packet on its next
            // client tick. Do not inspect world state from Netty.
            if (Vibe.getInstance().getStatistics() != null) Vibe.getInstance().getStatistics().recordDigging((C07PacketPlayerDigging) packet);
            GirlfriendModule girlfriend = Vibe.getInstance().getModuleManager().getModule(GirlfriendModule.class);
            if (girlfriend != null) girlfriend.onDigging((C07PacketPlayerDigging) packet);
            CuteVisualsModule cuteVisuals = Vibe.getInstance().getModuleManager().getModule(CuteVisualsModule.class);
            if (cuteVisuals != null) cuteVisuals.onDigging((C07PacketPlayerDigging) packet);
        }
        ScriptRuntime scripts = Vibe.getInstance() == null ? null : Vibe.getInstance().getScriptRuntime();
        if (scripts != null && !scripts.outbound(packet)) {
            // Cancelling at the Netty boundary must also complete the promise;
            // otherwise a script veto would leave the channel write pending.
            promise.trySuccess();
            return true;
        }
        if (scripts != null) scripts.dispatched(packet);
        FakeLagModule fakeLag = Vibe.getInstance() == null ? null
                : Vibe.getInstance().getModuleManager().getModule(FakeLagModule.class);
        if (fakeLag == null || !fakeLag.isEnabled()) return false;
        FakeLagModule.PacketAction action = fakeLag.classify(packet);
        if (action == FakeLagModule.PacketAction.FLUSH) {
            release(outbound, Long.MAX_VALUE, true, Owner.FAKE_LAG);
            return false;
        }
        if (action != FakeLagModule.PacketAction.QUEUE) return false;
        queue(outbound, new Entry(Owner.FAKE_LAG, packet, ctx, promise, nextRelease(false, fakeLag.getDelay().getInt())));
        return true;
    }

    private boolean delayInbound(Packet<?> packet, ChannelHandlerContext ctx) {
        if (packet instanceof S08PacketPlayerPosLook && Vibe.getInstance() != null) {
            final S08PacketPlayerPosLook correction = (S08PacketPlayerPosLook) packet;
            minecraft.addScheduledTask(new Runnable() {
                @Override public void run() {
                    dev.vibe.module.impl.FlagDetectorModule module = Vibe.getInstance().getModuleManager().getModule(dev.vibe.module.impl.FlagDetectorModule.class);
                    if (module != null) module.observe(correction);
                }
            });
        }
        ScriptRuntime scripts = Vibe.getInstance() == null ? null : Vibe.getInstance().getScriptRuntime();
        if (scripts != null && !scripts.inbound(packet)) return true;
        BacktrackModule backtrack = Vibe.getInstance() == null ? null
                : Vibe.getInstance().getModuleManager().getModule(BacktrackModule.class);
        if (backtrack == null || !backtrack.isEnabled()) return false;
        BacktrackModule.PacketAction action = backtrack.classify(packet);
        if (action == BacktrackModule.PacketAction.FLUSH) {
            // This runs on the channel's event loop. Replaying the buffered
            // stream first preserves the original receive order before the
            // boundary packet (teleport, velocity, death, disconnect) passes.
            release(inbound, Long.MAX_VALUE, false, Owner.BACKTRACK);
            return false;
        }
        if (action != BacktrackModule.PacketAction.QUEUE) return false;
        backtrack.observeDelayedPacket(packet);
        queue(inbound, new Entry(Owner.BACKTRACK, packet, ctx, null, nextRelease(true, backtrack.getRandomLatency())));
        return true;
    }

    private synchronized long nextRelease(boolean incoming, int delay) {
        long now = System.currentTimeMillis();
        long release = now + Math.max(1, delay);
        if (incoming) {
            release = Math.max(release, lastInboundRelease + 1L);
            lastInboundRelease = release;
        } else {
            release = Math.max(release, lastOutboundRelease + 1L);
            lastOutboundRelease = release;
        }
        return release;
    }

    private void queue(List<Entry> entries, Entry entry) {
        entries.add(entry);
    }

    private void releaseDue() {
        final long now = System.currentTimeMillis();
        final Channel channel = installedChannel;
        if (channel == null || !channel.isOpen()) return;
        channel.eventLoop().execute(new Runnable() {
            @Override public void run() {
                release(inbound, now, false, null);
                release(outbound, now, true, null);
            }
        });
    }

    public void flush(Owner owner) {
        Channel channel = installedChannel;
        if (channel == null || !channel.isOpen()) {
            removeOwner(inbound, owner);
            removeOwner(outbound, owner);
            return;
        }
        channel.eventLoop().execute(new Runnable() {
            @Override public void run() {
                release(inbound, Long.MAX_VALUE, false, owner);
                release(outbound, Long.MAX_VALUE, true, owner);
            }
        });
    }

    public void flushAll() {
        flush(Owner.FAKE_LAG);
        flush(Owner.BACKTRACK);
    }

    private void removeOwner(List<Entry> entries, Owner owner) {
        for (Entry entry : entries) if (entry.owner == owner) entries.remove(entry);
    }

    private void release(List<Entry> entries, long timestamp, boolean writing, Owner only) {
        java.util.ArrayList<Entry> ready = new java.util.ArrayList<Entry>();
        for (Entry entry : entries) {
            if ((only == null || entry.owner == only) && entry.releaseAt <= timestamp) ready.add(entry);
        }
        java.util.Collections.sort(ready, new Comparator<Entry>() {
            @Override public int compare(Entry first, Entry second) {
                return first.releaseAt < second.releaseAt ? -1 : first.releaseAt == second.releaseAt ? 0 : 1;
            }
        });
        for (Entry entry : ready) {
            entries.remove(entry);
            try {
                if (writing) entry.context.write(entry.packet, entry.promise);
                else entry.context.fireChannelRead(entry.packet);
            } catch (Throwable ignored) {
                // A closed channel can race a world disconnect. Dropping the
                // stale entry is safer than reviving it against a new world.
            }
        }
        if (writing && !ready.isEmpty() && context != null) context.flush();
    }

    private final class DelayHandler extends ChannelDuplexHandler {
        @Override public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
            context = ctx;
            super.handlerAdded(ctx);
        }

        @Override public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
            if (context == ctx) context = null;
            super.handlerRemoved(ctx);
        }

        @Override public void channelRead(ChannelHandlerContext ctx, Object message) throws Exception {
            if (message instanceof Packet && delayInbound((Packet<?>) message, ctx)) return;
            super.channelRead(ctx, message);
        }

        @Override public void write(ChannelHandlerContext ctx, Object message, ChannelPromise promise) throws Exception {
            if (message instanceof Packet && delayOutbound((Packet<?>) message, ctx, promise)) return;
            super.write(ctx, message, promise);
        }
    }

    private static final class Entry {
        private final Owner owner;
        private final Packet<?> packet;
        private final ChannelHandlerContext context;
        private final ChannelPromise promise;
        private final long releaseAt;

        private Entry(Owner owner, Packet<?> packet, ChannelHandlerContext context, ChannelPromise promise, long releaseAt) {
            this.owner = owner;
            this.packet = packet;
            this.context = context;
            this.promise = promise;
            this.releaseAt = releaseAt;
        }
    }
}

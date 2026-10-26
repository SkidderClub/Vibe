package dev.vibe.miniblox;

import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import java.lang.reflect.Field;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C17PacketCustomPayload;
import net.minecraft.network.play.server.S3FPacketCustomPayload;

/** Implements the wire layout documented by the translation layer's MovePacket.java/entity.js. */
public final class MinibloxProtocol extends ChannelDuplexHandler {
    private static volatile MinibloxProtocol active;
    private static final Field X = reported("lastReportedPosX", "field_175172_bI");
    private static final Field Y = reported("lastReportedPosY", "field_175166_bJ");
    private static final Field Z = reported("lastReportedPosZ", "field_175167_bK");
    private volatile Movement movement;
    private boolean identified;

    public static void install(Channel channel) {
        channel.eventLoop().execute(() -> {
            if (!channel.isOpen() || channel.pipeline().get("vibe_miniblox") != null) return;
            MinibloxProtocol handler = new MinibloxProtocol();
            // The caller has verified the runtime-owned loopback endpoint. Forge announces
            // vanilla connections on JoinGame, after upstream's layer:player payload, so
            // that earlier payload cannot be required here to enable movement.
            handler.identified = true;
            // PacketDelayService adds its handler later, immediately before packet_handler.
            // Outgoing packets therefore reach us after MoveFix/NoFall/scripts and after a blink releases them.
            String before = channel.pipeline().get("vibe_packet_delay") != null ? "vibe_packet_delay" : "packet_handler";
            if (channel.pipeline().get(before) != null) channel.pipeline().addBefore(before, "vibe_miniblox", handler);
            else channel.pipeline().addLast("vibe_miniblox", handler);
            active = handler;
        });
    }

    /** Main thread, at the walking packet boundary while MoveFix's rotation scope is active. */
    public static void captureHook(Object entity) {
        MinibloxProtocol handler = active;
        if (handler == null || !(entity instanceof EntityPlayerSP)) return;
        EntityPlayerSP player = (EntityPlayerSP) entity;
        if (player.movementInput == null) return;
        handler.movement = new Movement(position(X, player, player.posX), position(Y, player, player.getEntityBoundingBox().minY),
                position(Z, player, player.posZ), player.rotationYaw, player.rotationPitch,
                player.movementInput.moveForward, player.movementInput.moveStrafe,
                player.movementInput.jump, player.movementInput.sneak, player.isSprinting(), player.isUsingItem());
    }

    @Override public void channelRead(ChannelHandlerContext ctx, Object message) throws Exception {
        if (message instanceof S3FPacketCustomPayload && "layer:player".equals(((S3FPacketCustomPayload) message).getChannelName())) identified = true;
        super.channelRead(ctx, message);
    }
    @Override public void write(ChannelHandlerContext ctx, Object message, ChannelPromise promise) throws Exception {
        Movement sample = movement;
        if (identified && sample != null && message instanceof C03PacketPlayer) {
            C03PacketPlayer player = (C03PacketPlayer) message;
            ctx.write(new C17PacketCustomPayload("miniblox:movepacket", encode(sample, player)), ctx.voidPromise());
        }
        super.write(ctx, message, promise);
    }
    @Override public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (active == this) active = null;
        movement = null; identified = false; super.channelInactive(ctx);
    }

    static PacketBuffer encode(Movement input, C03PacketPlayer packet) {
        PacketBuffer data = new PacketBuffer(Unpooled.buffer(45));
        data.writeDouble(input.x); data.writeDouble(input.y); data.writeDouble(input.z);
        data.writeFloat(packet.getRotating() ? packet.getYaw() : input.yaw);
        data.writeFloat(packet.getRotating() ? packet.getPitch() : input.pitch);
        data.writeFloat(input.forward); data.writeFloat(input.sideways);
        data.writeBoolean(input.jump); data.writeBoolean(input.sneak); data.writeBoolean(packet.isOnGround());
        data.writeBoolean(input.sprint); data.writeBoolean(input.usingItem);
        return data;
    }
    static final class Movement {
        final double x, y, z; final float yaw, pitch, forward, sideways;
        final boolean jump, sneak, sprint, usingItem;
        Movement(double x, double y, double z, float yaw, float pitch, float forward, float sideways,
                boolean jump, boolean sneak, boolean sprint, boolean usingItem) {
            this.x=x; this.y=y; this.z=z; this.yaw=yaw; this.pitch=pitch; this.forward=forward; this.sideways=sideways;
            this.jump=jump; this.sneak=sneak; this.sprint=sprint; this.usingItem=usingItem;
        }
    }
    private static Field reported(String... names) {
        for (String name : names) try { Field field = EntityPlayerSP.class.getDeclaredField(name); field.setAccessible(true); return field; }
        catch (ReflectiveOperationException ignored) { }
        return null;
    }
    private static double position(Field field, EntityPlayerSP player, double fallback) {
        try { return field == null ? fallback : field.getDouble(player); } catch (IllegalAccessException ignored) { return fallback; }
    }
}

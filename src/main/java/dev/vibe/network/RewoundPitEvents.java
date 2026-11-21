package dev.vibe.network;

import cc.rewound.modapi.packet.impl.clientbound.ClientboundHelloPacket;
import cc.rewound.modapi.packet.impl.clientbound.event.ClientboundLocationPacket;
import cc.rewound.modapi.packet.impl.clientbound.event.ClientboundPitEventsPacket;
import cc.rewound.modapi.serializer.PacketSerializer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.AttributeKey;
import java.util.Objects;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.C17PacketCustomPayload;
import net.minecraft.network.play.server.S3FPacketCustomPayload;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

/** The Rewound 1.0.4 plugin-message transport shares one registration across Vibe features. */
public final class RewoundPitEvents {
    private static final String HELLO = "rewound:hello";
    private static final String LOCATION = "rewound:location";
    private static final String EVENTS = "rewound:pit_events";
    private static final AttributeKey<Session> SESSION = AttributeKey.valueOf("vibe.rewound.session");

    @SubscribeEvent
    public void connected(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        if (event.isLocal) return;
        NetworkManager manager = event.manager;
        manager.channel().pipeline().addBefore("packet_handler", "vibe_rewound_events", new ChannelInboundHandlerAdapter() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object message) throws Exception {
                if (message instanceof S3FPacketCustomPayload) {
                    S3FPacketCustomPayload payload = (S3FPacketCustomPayload) message;
                    if (receive(ctx.channel(), payload.getChannelName(), payload.getBufferData())) {
                        manager.sendPacket(new C17PacketCustomPayload("rewound:register", registration()));
                    }
                }
                super.channelRead(ctx, message);
            }
        });
    }

    public static ClientboundPitEventsPacket snapshot(NetHandlerPlayClient connection) {
        Session session = session(connection);
        return session == null ? null : session.events;
    }

    /** Null until the server has identified its location or sent a Pit event update. */
    public static Boolean pitLocation(NetHandlerPlayClient connection) {
        return pitLocation(session(connection));
    }

    private static Session session(NetHandlerPlayClient connection) {
        if (connection == null) return null;
        Channel channel = connection.getNetworkManager().channel();
        return channel == null || !channel.isOpen() ? null : channel.attr(SESSION).get();
    }

    static Boolean pitLocation(Channel channel) {
        return pitLocation(channel.attr(SESSION).get());
    }

    private static Boolean pitLocation(Session session) {
        if (session == null) return null;
        Boolean location = session.inPit;
        if (location != null) return location;
        return session.events == null ? null : Boolean.TRUE;
    }

    static ClientboundPitEventsPacket snapshot(Channel channel) {
        Session session = channel.attr(SESSION).get();
        return session == null ? null : session.events;
    }

    /** Returns true only when a validated hello needs a fresh subscription. */
    static boolean receive(Channel channel, String identifier, ByteBuf payload) {
        if (!HELLO.equals(identifier) && !LOCATION.equals(identifier) && !EVENTS.equals(identifier)) return false;
        if (payload == null || !payload.isReadable() || payload.readableBytes() > 4096) return false;
        ByteBuf bytes = payload.duplicate();
        PacketSerializer serializer = new PacketSerializer(bytes);
        try {
            if (!serializer.readBoolean()) return false;
            if (HELLO.equals(identifier)) {
                new ClientboundHelloPacket(serializer);
                channel.attr(SESSION).set(new Session());
                return true;
            }
            Session session = channel.attr(SESSION).get();
            if (session == null) return false;
            if (LOCATION.equals(identifier)) {
                ClientboundLocationPacket location = new ClientboundLocationPacket(serializer);
                if (location.isExpectedVersion() && !bytes.isReadable()) session.move(location);
            } else if (!Boolean.FALSE.equals(session.inPit)) {
                ClientboundPitEventsPacket events = new ClientboundPitEventsPacket(serializer);
                if (events.isExpectedVersion() && !bytes.isReadable() && events.getUpcoming().size() <= 2) {
                    session.events = events;
                }
            }
        } catch (RuntimeException malformed) {
            // A bad update does not replace the last valid snapshot or consume the packet for other mods.
        }
        return false;
    }

    static PacketBuffer registration() {
        PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());
        PacketSerializer serializer = new PacketSerializer(buffer);
        serializer.writeVarInt(1).writeVarInt(5);
        serializer.writeString(LOCATION).writeVarInt(ClientboundLocationPacket.CURRENT_VERSION);
        for (String channel : new String[]{"rewound:game_start", "rewound:game_finish", "rewound:game_rejoin"}) {
            serializer.writeString(channel).writeVarInt(1);
        }
        serializer.writeString(EVENTS).writeVarInt(ClientboundPitEventsPacket.CURRENT_VERSION);
        return buffer;
    }

    private static final class Session {
        private String server;
        private volatile Boolean inPit;
        private volatile ClientboundPitEventsPacket events;

        private void move(ClientboundLocationPacket location) {
            String nextServer = location.getServerName();
            inPit = location.getServerType().map(type -> "PIT".equals(type.name())).orElse(false)
                    && !location.getLobbyName().isPresent();
            if (!inPit || server != null && !Objects.equals(server, nextServer)) events = null;
            server = nextServer;
        }
    }
}

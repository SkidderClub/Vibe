package dev.vibe.network;

import cc.rewound.modapi.packet.impl.clientbound.ClientboundHelloPacket;
import cc.rewound.modapi.packet.impl.clientbound.event.ClientboundLocationPacket;
import cc.rewound.modapi.packet.impl.clientbound.event.ClientboundPitEventsPacket;
import cc.rewound.modapi.packet.impl.clientbound.event.ClientboundPitEventsPacket.PitEvent;
import cc.rewound.modapi.serializer.PacketSerializer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import net.hypixel.data.region.Environment;
import net.hypixel.data.type.ServerType;
import org.junit.Test;
import static org.junit.Assert.*;

public class RewoundPitEventsTest {
    @Test
    public void registersExistingConsumersAlongsidePit() {
        ByteBuf data = RewoundPitEvents.registration();
        try {
            PacketSerializer serializer = new PacketSerializer(data);
            assertEquals(1, serializer.readVarInt());
            assertEquals(5, serializer.readVarInt());
            Map<String, Integer> events = new LinkedHashMap<>();
            while (data.isReadable()) events.put(serializer.readString(), serializer.readVarInt());
            assertEquals(Arrays.asList("rewound:location", "rewound:game_start", "rewound:game_finish",
                    "rewound:game_rejoin", "rewound:pit_events"), Arrays.asList(events.keySet().toArray()));
            assertTrue(events.values().stream().allMatch(version -> version == 1));
        } finally {
            data.release();
        }
    }

    @Test
    public void decodesRealApiPacketsAndDropsSnapshotsOnTransfer() {
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        EmbeddedChannel nextConnection = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        ByteBuf hello = Unpooled.buffer();
        ByteBuf pit = Unpooled.buffer();
        ByteBuf location = Unpooled.buffer();
        ByteBuf lobby = Unpooled.buffer();
        try {
            new ClientboundHelloPacket(Environment.PRODUCTION).write(new PacketSerializer(hello.writeBoolean(true)));
            PitEvent active = new PitEvent("spire", "Spire", true, 90000L, 200000L);
            PitEvent minor = new PitEvent("koth", "King of the Hill", false, 201000L, 0L);
            PitEvent major = new PitEvent("raffle", "Raffle", true, 500000L, 0L);
            ClientboundPitEventsPacket schedule = new ClientboundPitEventsPacket(active, Arrays.asList(minor, major));
            schedule.write(new PacketSerializer(pit.writeBoolean(true)));

            assertFalse(RewoundPitEvents.receive(channel, "rewound:pit_events", pit));
            assertNull(RewoundPitEvents.snapshot(channel));
            assertTrue(RewoundPitEvents.receive(channel, "rewound:hello", hello));
            assertNull(RewoundPitEvents.pitLocation(channel));
            assertFalse(RewoundPitEvents.receive(channel, "rewound:pit_events", pit));
            assertEquals(0, pit.readerIndex());
            assertEquals(0, hello.readerIndex());
            assertEquals(active, RewoundPitEvents.snapshot(channel).getCurrent().get());
            assertEquals(Boolean.TRUE, RewoundPitEvents.pitLocation(channel));
            assertEquals(minor, RewoundPitEvents.snapshot(channel).getNext(false).get());
            assertEquals(major, RewoundPitEvents.snapshot(channel).getNext(true).get());
            assertNull(RewoundPitEvents.snapshot(nextConnection));

            ByteBuf broken = pit.slice(0, pit.readableBytes() - 1);
            assertFalse(RewoundPitEvents.receive(channel, "rewound:pit_events", broken));
            assertEquals(active, RewoundPitEvents.snapshot(channel).getCurrent().get());
            new ClientboundLocationPacket("pit1", ServerType.valueOf("PIT").get(), null, null, null)
                    .write(new PacketSerializer(location.writeBoolean(true)));
            RewoundPitEvents.receive(channel, "rewound:location", location);
            assertEquals(Boolean.TRUE, RewoundPitEvents.pitLocation(channel));
            assertNotNull(RewoundPitEvents.snapshot(channel));
            new ClientboundLocationPacket("lobby", null, "MAIN", null, null)
                    .write(new PacketSerializer(lobby.writeBoolean(true)));
            RewoundPitEvents.receive(channel, "rewound:location", lobby);
            assertEquals(Boolean.FALSE, RewoundPitEvents.pitLocation(channel));
            assertNull(RewoundPitEvents.snapshot(channel));

            RewoundPitEvents.receive(channel, "rewound:pit_events", pit);
            assertNull(RewoundPitEvents.snapshot(channel));
            assertTrue(RewoundPitEvents.receive(channel, "rewound:hello", hello));
            RewoundPitEvents.receive(channel, "rewound:pit_events", pit);
            assertNotNull(RewoundPitEvents.snapshot(channel));
            assertTrue(RewoundPitEvents.receive(channel, "rewound:hello", hello));
            assertNull(RewoundPitEvents.snapshot(channel));
        } finally {
            hello.release();
            pit.release();
            location.release();
            lobby.release();
            channel.finish();
            nextConnection.finish();
        }
    }
}

package dev.vibe.miniblox;

import dev.vibe.path.PathTestFixture;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.*;
import net.minecraft.network.play.server.S3FPacketCustomPayload;
import org.junit.Test;
import static org.junit.Assert.*;

public class MinibloxProtocolTest {
    private final MinibloxProtocol.Movement sample = new MinibloxProtocol.Movement(1.25,64,-2.5,120,-15,.3F,-.3F,true,true,true,true);
    @Test public void binaryLayoutMatchesUpstreamIncludingOptionalUsingItem() {
        PacketBuffer data = MinibloxProtocol.encode(sample, new C03PacketPlayer(true));
        try {
            assertEquals(45, data.readableBytes()); assertEquals(1.25,data.readDouble(),0); assertEquals(64,data.readDouble(),0); assertEquals(-2.5,data.readDouble(),0);
            assertEquals(120,data.readFloat(),0); assertEquals(-15,data.readFloat(),0); assertEquals(.3F,data.readFloat(),0); assertEquals(-.3F,data.readFloat(),0);
            for(int i=0;i<5;i++) assertTrue(data.readBoolean()); assertEquals(0,data.readableBytes());
        } finally { data.release(); }
    }
    @Test public void preservesFinalOutgoingRotationAndGroundFlag() {
        PacketBuffer data = MinibloxProtocol.encode(sample, new C03PacketPlayer.C05PacketPlayerLook(89,32,false));
        try { assertEquals(89,data.getFloat(24),0); assertEquals(32,data.getFloat(28),0); assertFalse(data.getBoolean(42)); }
        finally { data.release(); }
    }
    @Test public void onlyIdentifiedBridgeGetsAdjacentMovementPayload() throws Exception {
        MinibloxProtocol handler = new MinibloxProtocol();
        PathTestFixture.set(MinibloxProtocol.class,handler,"movement",sample);
        EmbeddedChannel channel = new EmbeddedChannel(handler);
        C03PacketPlayer packet = new C03PacketPlayer(true);
        try {
            channel.writeOutbound(packet); assertSame(packet,channel.readOutbound()); assertNull(channel.readOutbound());
            identify(channel);
            for(int i=0;i<3;i++) {
                channel.writeOutbound(packet);
                C17PacketCustomPayload custom = (C17PacketCustomPayload) channel.readOutbound();
                assertEquals("miniblox:movepacket",custom.getChannelName()); assertEquals(45,custom.getBufferData().readableBytes());
                custom.getBufferData().release(); assertSame(packet,channel.readOutbound()); assertNull(channel.readOutbound());
            }
            C00PacketKeepAlive keepAlive = new C00PacketKeepAlive(); channel.writeOutbound(keepAlive);
            assertSame(keepAlive,channel.readOutbound()); assertNull(channel.readOutbound());
        } finally { channel.finish(); }
    }
    @Test public void cancelledMovementDoesNotGeneratePayloadAndDisconnectClearsSnapshot() throws Exception {
        MinibloxProtocol handler = new MinibloxProtocol(); PathTestFixture.set(MinibloxProtocol.class,handler,"movement",sample);
        EmbeddedChannel channel = new EmbeddedChannel(handler, new ChannelDuplexHandler() {
            @Override public void write(ChannelHandlerContext ctx,Object message,ChannelPromise promise) { promise.setSuccess(); }
        });
        identify(channel); channel.writeOutbound(new C03PacketPlayer(true)); assertNull(channel.readOutbound());
        channel.finish();
        java.lang.reflect.Field field = MinibloxProtocol.class.getDeclaredField("movement"); field.setAccessible(true); assertNull(field.get(handler));
    }
    @Test public void managedEndpointWorksWhenForgeHasAlreadyMissedTheIdentificationPayload() throws Exception {
        net.minecraft.client.Minecraft previous = net.minecraft.client.Minecraft.getMinecraft();
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelDuplexHandler());
        try {
            net.minecraft.client.Minecraft mc = PathTestFixture.allocate(net.minecraft.client.Minecraft.class);
            PathTestFixture.set(net.minecraft.client.Minecraft.class,null,"theMinecraft",mc);
            mc.gameSettings = PathTestFixture.allocate(net.minecraft.client.settings.GameSettings.class);
            PreviewPlayer player = PathTestFixture.allocate(PreviewPlayer.class); mc.thePlayer=player;
            player.setEntityBoundingBox(new net.minecraft.util.AxisAlignedBB(1,64,2,2,66,3));
            player.movementInput=new net.minecraft.util.MovementInput();
            player.movementInput.moveForward=.3F;player.movementInput.moveStrafe=-.3F;player.movementInput.jump=true;
            PathTestFixture.set(net.minecraft.client.entity.EntityPlayerSP.class,player,"lastReportedPosX",7.25);
            PathTestFixture.set(net.minecraft.client.entity.EntityPlayerSP.class,player,"lastReportedPosY",70.5);
            PathTestFixture.set(net.minecraft.client.entity.EntityPlayerSP.class,player,"lastReportedPosZ",-2.5);
            dev.vibe.module.impl.movement.MoveFixModule fix=new dev.vibe.module.impl.movement.MoveFixModule();
            fix.setFakeRotation("miniblox-test",90,25);fix.beginPacketRotation(player);
            MinibloxProtocol.install(channel);channel.runPendingTasks();MinibloxProtocol.captureHook(player);
            fix.endPacketRotation(player);assertEquals(0,player.rotationYaw,0);
            channel.writeOutbound(new C03PacketPlayer(true));
            C17PacketCustomPayload custom=(C17PacketCustomPayload)channel.readOutbound();assertNotNull(custom);
            PacketBuffer data=custom.getBufferData();
            try {
                assertEquals(7.25,data.getDouble(0),0);assertEquals(70.5,data.getDouble(8),0);assertEquals(-2.5,data.getDouble(16),0);
                assertEquals(90,data.getFloat(24),0);assertEquals(25,data.getFloat(28),0);
                assertEquals(.3F,data.getFloat(32),0);assertEquals(-.3F,data.getFloat(36),0);assertTrue(data.getBoolean(40));
            } finally {data.release();}
            assertTrue(channel.readOutbound() instanceof C03PacketPlayer);assertNull(channel.readOutbound());
        } finally {channel.finish();PathTestFixture.set(net.minecraft.client.Minecraft.class,null,"theMinecraft",previous);}
    }
    private static final class PreviewPlayer extends net.minecraft.client.entity.EntityPlayerSP {
        private PreviewPlayer(){super(null,null,null,null);}
        @Override public boolean isSprinting(){return true;}
        @Override public boolean isUsingItem(){return false;}
    }
    private static void identify(EmbeddedChannel channel) {
        PacketBuffer buffer = new PacketBuffer(Unpooled.buffer(0));
        channel.writeInbound(new S3FPacketCustomPayload("layer:player",buffer));
        channel.readInbound(); buffer.release();
    }
}

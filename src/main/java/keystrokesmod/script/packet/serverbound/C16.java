package keystrokesmod.script.packet.serverbound;
import net.minecraft.network.play.client.C16PacketClientStatus;
public class C16 extends CPacket { public String status; public C16(String status){super(new C16PacketClientStatus(C16PacketClientStatus.EnumState.valueOf(status.toUpperCase(java.util.Locale.ROOT))));this.status=status;} public C16(C16PacketClientStatus value){super(value);status=value.getStatus().name();} public C16PacketClientStatus convert(){return (C16PacketClientStatus)packet;} }

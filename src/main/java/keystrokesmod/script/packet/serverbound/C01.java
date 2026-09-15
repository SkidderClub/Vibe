package keystrokesmod.script.packet.serverbound;
import net.minecraft.network.play.client.C01PacketChatMessage;
public class C01 extends CPacket { public String message; public C01(String value){super(new C01PacketChatMessage(value));message=value;} public C01(C01PacketChatMessage value,byte ignored){super(value);message=value.getMessage();} public C01PacketChatMessage convert(){return packet instanceof C01PacketChatMessage?(C01PacketChatMessage)packet:new C01PacketChatMessage(message);} }

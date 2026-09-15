package keystrokesmod.script.packet.serverbound;
import net.minecraft.network.play.client.C0DPacketCloseWindow;
public class C0D extends CPacket { public int windowId; public C0D(int value){super(new C0DPacketCloseWindow(value));windowId=value;} public C0D(C0DPacketCloseWindow value){super(value);windowId=0;} public C0DPacketCloseWindow convert(){return (C0DPacketCloseWindow)packet;} }

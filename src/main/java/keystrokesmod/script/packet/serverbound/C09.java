package keystrokesmod.script.packet.serverbound;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
public class C09 extends CPacket { public int slot; public C09(int slot){super(new C09PacketHeldItemChange(slot));this.slot=slot;} public C09(C09PacketHeldItemChange value,boolean ignored){super(value);slot=value.getSlotId();} public C09PacketHeldItemChange convert(){return (C09PacketHeldItemChange)packet;} }

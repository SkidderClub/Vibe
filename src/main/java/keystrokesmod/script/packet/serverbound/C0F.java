package keystrokesmod.script.packet.serverbound;
import net.minecraft.network.play.client.C0FPacketConfirmTransaction;
public class C0F extends CPacket { public int windowId; public short uid; public C0F(int id,short uid){super(new C0FPacketConfirmTransaction(id,uid,true));windowId=id;this.uid=uid;} public C0F(C0FPacketConfirmTransaction value){super(value);windowId=value.getWindowId();uid=value.getUid();} public C0FPacketConfirmTransaction convert(){return (C0FPacketConfirmTransaction)packet;} }

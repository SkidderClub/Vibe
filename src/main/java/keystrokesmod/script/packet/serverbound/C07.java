package keystrokesmod.script.packet.serverbound;
import keystrokesmod.script.model.Vec3;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.util.EnumFacing;
public class C07 extends CPacket { public Vec3 position; public String status,facing; public C07(Vec3 value,String status,String facing){super(new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.valueOf(status.toUpperCase(java.util.Locale.ROOT)),Vec3.getBlockPos(value),EnumFacing.valueOf(facing.toUpperCase(java.util.Locale.ROOT))));position=value;this.status=status;this.facing=facing;} public C07(C07PacketPlayerDigging value){super(value);position=new Vec3(value.getPosition());status=value.getStatus().name();facing=value.getFacing().name();} public C07PacketPlayerDigging convert(){return (C07PacketPlayerDigging)packet;} }

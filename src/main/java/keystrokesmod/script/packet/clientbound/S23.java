package keystrokesmod.script.packet.clientbound;
import keystrokesmod.script.model.Block; import keystrokesmod.script.model.Vec3; import net.minecraft.network.play.server.S23PacketBlockChange;
public class S23 extends SPacket { public Vec3 position; public Block block; public S23(S23PacketBlockChange value,byte ignored){super(value);position=new Vec3(value.getBlockPosition());block=new Block(value.getBlockState(),value.getBlockPosition());} public S23(Vec3 position){super(null);this.position=position;block=new Block(position);} }

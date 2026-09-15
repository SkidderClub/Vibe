package keystrokesmod.script.packet.clientbound;
import keystrokesmod.script.model.Vec3; import net.minecraft.network.play.server.S12PacketEntityVelocity;
public class S12 extends SPacket { public int entityId; public Vec3 motion; public S12(S12PacketEntityVelocity value){super(value);entityId=value.getEntityID();motion=new Vec3(value.getMotionX()/8000D,value.getMotionY()/8000D,value.getMotionZ()/8000D);} public S12(int id,Vec3 motion){super(new S12PacketEntityVelocity(id,motion.x,motion.y,motion.z));entityId=id;this.motion=motion;} }

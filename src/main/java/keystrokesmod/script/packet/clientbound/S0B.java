package keystrokesmod.script.packet.clientbound;
import net.minecraft.network.play.server.S0BPacketAnimation;
public class S0B extends SPacket { public int entityId,type; public S0B(S0BPacketAnimation value){super(value);entityId=value.getEntityID();type=value.getAnimationType();} public S0B(int entityId,int type){super(new S0BPacketAnimation());this.entityId=entityId;this.type=type;} }

package keystrokesmod.script.packet.clientbound;
import keystrokesmod.script.model.Vec3; import net.minecraft.network.play.server.S29PacketSoundEffect;
public class S29 extends SPacket { public String sound; public Vec3 position; public float volume,pitch; public S29(S29PacketSoundEffect value){super(value);sound=value.getSoundName();position=new Vec3(value.getX(),value.getY(),value.getZ());volume=value.getVolume();pitch=value.getPitch();} public S29(String sound,Vec3 position,float volume,float pitch){super(null);this.sound=sound;this.position=position;this.volume=volume;this.pitch=pitch;} }

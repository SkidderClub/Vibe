package keystrokesmod.script.packet.clientbound;
import net.minecraft.network.play.server.S06PacketUpdateHealth;
public class S06 extends SPacket { public float health,saturation; public int food; public S06(S06PacketUpdateHealth value){super(value);health=value.getHealth();saturation=value.getSaturationLevel();food=value.getFoodLevel();} public S06(float health,float saturation,int food){super(new S06PacketUpdateHealth(health,food,saturation));this.health=health;this.saturation=saturation;this.food=food;} }

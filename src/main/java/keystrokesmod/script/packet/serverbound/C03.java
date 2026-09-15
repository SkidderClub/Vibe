package keystrokesmod.script.packet.serverbound;
import keystrokesmod.script.model.Vec3;
import net.minecraft.network.play.client.C03PacketPlayer;
public class C03 extends CPacket {
    public Vec3 position;
    public float yaw, pitch;
    public boolean ground;

    public C03(C03PacketPlayer packet, byte a, byte b, byte c, byte d, byte e, byte f) {
        super(packet);
        if (packet instanceof C03PacketPlayer.C04PacketPlayerPosition || packet instanceof C03PacketPlayer.C06PacketPlayerPosLook) {
            position = new Vec3(packet.getPositionX(), packet.getPositionY(), packet.getPositionZ());
        }
        if (packet instanceof C03PacketPlayer.C05PacketPlayerLook || packet instanceof C03PacketPlayer.C06PacketPlayerPosLook) {
            yaw = packet.getYaw();
            pitch = packet.getPitch();
        }
        ground = packet.isOnGround();
    }

    public C03(boolean ground) { super(new C03PacketPlayer(ground)); this.ground = ground; }
    public C03(Vec3 value, boolean ground) { super(new C03PacketPlayer.C04PacketPlayerPosition(value.x, value.y, value.z, ground)); position = value; this.ground = ground; }
    public C03(float yaw, float pitch, boolean ground) { super(new C03PacketPlayer.C05PacketPlayerLook(yaw, pitch, ground)); this.yaw = yaw; this.pitch = pitch; this.ground = ground; }
    public C03(Vec3 value, float yaw, float pitch, boolean ground) { super(new C03PacketPlayer.C06PacketPlayerPosLook(value.x, value.y, value.z, yaw, pitch, ground)); position = value; this.yaw = yaw; this.pitch = pitch; this.ground = ground; }
}

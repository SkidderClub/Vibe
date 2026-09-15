package keystrokesmod.script.packet.serverbound;

/** Base Raven packet wrapper. Unknown packets remain safely inspectable. */
public class CPacket {
    public net.minecraft.network.Packet packet;
    public String name;
    public CPacket(net.minecraft.network.Packet value) { packet=value; name=value==null?"":value.getClass().getSimpleName(); }
}

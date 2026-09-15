package keystrokesmod.script.packet.clientbound;

/** Base Raven packet wrapper. Unknown packets remain safely inspectable. */
public class SPacket {
    public net.minecraft.network.Packet packet;
    public String name;
    public SPacket(net.minecraft.network.Packet value) { packet=value; name=value==null?"":value.getClass().getSimpleName(); }
}

package keystrokesmod.script.packet.clientbound;
import net.minecraft.network.play.server.S3APacketTabComplete;
public class S3A extends SPacket { public String[] matches; public S3A(S3APacketTabComplete value,byte ignored){super(value);matches=value.func_149630_c();} public S3A(String[] value){super(null);matches=value;} }

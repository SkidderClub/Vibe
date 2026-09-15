package keystrokesmod.script.packet;
import java.util.LinkedHashMap; import java.util.Map; import keystrokesmod.script.packet.clientbound.SPacket; import keystrokesmod.script.packet.serverbound.CPacket; import net.minecraft.network.Packet;
/** Compatibility map holder. PacketHandler performs the actual typed conversion. */
public final class PacketMappings { public static final Map<Class<? extends Packet<?>>,Class<? extends CPacket>> minecraftToScriptC=new LinkedHashMap<Class<? extends Packet<?>>,Class<? extends CPacket>>(); public static final Map<Class<? extends Packet<?>>,Class<? extends SPacket>> minecraftToScriptS=new LinkedHashMap<Class<? extends Packet<?>>,Class<? extends SPacket>>(); private PacketMappings(){} }

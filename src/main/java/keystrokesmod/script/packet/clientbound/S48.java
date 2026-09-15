package keystrokesmod.script.packet.clientbound;
import net.minecraft.network.play.server.S48PacketResourcePackSend;
public class S48 extends SPacket { public String url,hash; public S48(S48PacketResourcePackSend value){super(value);url=value.getURL();hash=value.getHash();} public S48(String url,String hash){super(null);this.url=url;this.hash=hash;} }

package keystrokesmod.script.packet.clientbound;
import keystrokesmod.script.model.ItemStack; import net.minecraft.network.play.server.S2FPacketSetSlot;
public class S2F extends SPacket { public int windowId,slot; public ItemStack itemStack; public S2F(S2FPacketSetSlot value){super(value);windowId=value.func_149175_c();slot=value.func_149173_d();itemStack=ItemStack.convert(value.func_149174_e());} public S2F(int id,int slot,ItemStack item){super(null);windowId=id;this.slot=slot;itemStack=item;} }

package keystrokesmod.script.packet.clientbound;
import keystrokesmod.script.model.ItemStack; import net.minecraft.network.play.server.S04PacketEntityEquipment;
public class S04 extends SPacket { public int entityId,slot; public ItemStack item; public S04(S04PacketEntityEquipment value){super(value);entityId=value.getEntityID();slot=value.getEquipmentSlot();item=ItemStack.convert(value.getItemStack());} public S04(int entityId,int slot,ItemStack item){super(new S04PacketEntityEquipment(entityId,slot,item==null?null:item.itemStack));this.entityId=entityId;this.slot=slot;this.item=item;} }

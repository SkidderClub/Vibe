package keystrokesmod.script.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;

public class ItemStack {
    public String type, name, displayName;
    public int stackSize, maxStackSize, durability, maxDurability, meta;
    public boolean isBlock;
    public net.minecraft.item.ItemStack itemStack;
    public ItemStack(net.minecraft.item.ItemStack stack, byte ignored) {
        if (stack == null) return;
        itemStack=stack; isBlock=stack.getItem() instanceof ItemBlock;
        type=isBlock?((ItemBlock)stack.getItem()).getBlock().getClass().getSimpleName():stack.getItem().getClass().getSimpleName();
        String registry=String.valueOf(stack.getItem().getRegistryName()); name=registry.startsWith("minecraft:")?registry.substring(10):registry;
        displayName=stack.getDisplayName(); stackSize=stack.stackSize; maxStackSize=stack.getMaxStackSize(); durability=stack.getMaxDamage()-stack.getItemDamage(); maxDurability=stack.getMaxDamage(); meta=stack.getMetadata();
    }
    public ItemStack(String name) { this(fromName(name), (byte)0); }
    private static net.minecraft.item.ItemStack fromName(String value) { String[] parts=value.split(":"); Item item=(Item)Item.itemRegistry.getObject(new ResourceLocation("minecraft:"+parts[0])); int meta=0; try { if(parts.length>1)meta=Integer.parseInt(parts[1]); }catch(Exception ignored){} return item==null?null:new net.minecraft.item.ItemStack(item,1,meta); }
    public List<String> getTooltip() { return itemStack==null?new ArrayList<String>():itemStack.getTooltip(Minecraft.getMinecraft().thePlayer,true); }
    public List<Object[]> getEnchantments() { if(itemStack==null)return null; Map<Integer,Integer> values=EnchantmentHelper.getEnchantments(itemStack); if(values.isEmpty())return null; List<Object[]> result=new ArrayList<Object[]>(); for(Map.Entry<Integer,Integer> entry:values.entrySet()){Enchantment e=Enchantment.getEnchantmentById(entry.getKey()); result.add(new Object[]{StatCollector.translateToFallback(e.getName()).toLowerCase().replace(" ","_"),entry.getValue()});}return result; }
    public static ItemStack convert(net.minecraft.item.ItemStack value){return value==null?null:new ItemStack(value,(byte)0);}
    @Override public String toString(){return "ItemStack("+type+","+name+")";}
}

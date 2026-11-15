package dev.vibe.pit;

import java.util.*;
import java.util.regex.*;
import net.minecraft.inventory.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;

/** Only server-provided chest slots are searched; player inventory items never become menu buttons. */
public final class PitMenus {
    private PitMenus(){}
    private static final Pattern NOTIFICATION=Pattern.compile("(?s)^(?:STREAK|KILLSTREAK|KILLS?|(?:MULTI|DOUBLE|TRIPLE|QUADRUPLE|QUADRA|QUAD|QUINTUPLE|PENTA|MEGA)[ -]?KILL|BOUNTY(?: CLAIMED| INCREASED)?|ASSIST|PIT LEVEL UP|MEGASTREAK)!.*");
    public static String title(ContainerChest chest){return PitStatus.plain(chest.getLowerChestInventory().getDisplayName().getUnformattedText()).toLowerCase(Locale.ROOT);}
    public static String text(ItemStack item){
        if(item==null)return "";
        String name=item.hasDisplayName()?item.getTagCompound().getCompoundTag("display").getString("Name"):item.getDisplayName();
        StringBuilder out=new StringBuilder(PitStatus.plain(name));
        if(item.hasTagCompound()&&item.getTagCompound().hasKey("display",10)){
            NBTTagList lore=item.getTagCompound().getCompoundTag("display").getTagList("Lore",8);
            for(int i=0;i<lore.tagCount();i++)out.append('\n').append(PitStatus.plain(lore.getStringTagAt(i)));
        }return out.toString();
    }
    public static int findPerkSlot(ContainerChest chest,int number){
        Pattern slot=Pattern.compile("perk slot\\s*#?"+number+"\\b",Pattern.CASE_INSENSITIVE);
        int size=chest.getLowerChestInventory().getSizeInventory();
        for(int i=0;i<size;i++)if(slot.matcher(text(chest.getSlot(i).getStack())).find())return i;
        int fixed=10+number;
        if(fixed<size){ItemStack stack=chest.getSlot(fixed).getStack();
            if(PitPerk.from(stack)!=null||stack!=null&&stack.getItem()==net.minecraft.init.Items.dye&&stack.getMetadata()==8)return fixed;
        }
        return -1;
    }
    public static int findItem(ContainerChest chest,String item){
        int size=chest.getLowerChestInventory().getSizeInventory();
        for(int i=0;i<size;i++){ItemStack stack=chest.getSlot(i).getStack();if(stack!=null&&item.equals(net.minecraft.item.Item.itemRegistry.getNameForObject(stack.getItem()).getResourcePath()))return i;}return -1;
    }
    public static boolean locked(ItemStack stack){String s=text(stack).toLowerCase(Locale.ROOT);return s.contains("requires")||s.contains("locked")&&!s.contains("unlocked")||s.contains("not unlocked")||s.contains("not enough")||s.contains("insufficient")||s.contains("cannot afford")||s.contains("can't afford");}
    public static boolean prestigeReady(ItemStack stack){
        String s=text(stack).toLowerCase(Locale.ROOT);if(locked(stack)||!s.contains("click to prestige"))return false;
        if(s.contains("cooldown:")&&!s.matches("(?s).*cooldown:\\s*ready\\b.*"))return false;
        Matcher gold=Pattern.compile("gold this prestige\\s*:\\s*([\\d,.]+)\\s*/\\s*([\\d,.]+)").matcher(s);
        return !gold.find()||PitStatus.amount(gold.group(1))>=PitStatus.amount(gold.group(2));
    }
    public static boolean noisyMessage(String raw){return NOTIFICATION.matcher(PitStatus.plain(raw)).matches();}
}

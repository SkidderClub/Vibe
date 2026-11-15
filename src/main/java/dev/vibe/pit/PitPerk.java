package dev.vibe.pit;

import net.minecraft.init.*;
import net.minecraft.item.*;

public enum PitPerk {
    GOLDEN_HEADS("Golden Heads",500,10,"gold_block"),
    FISHING_ROD("Fishing Rod",1000,10,"fishing_rod"),
    LAVA_BUCKET("Lava Bucket",1000,10,"lava_bucket"),
    STRENGTH("Strength-Chaining",2000,20,"redstone"),
    SAFETY("Safety First",3000,20,"chainmail_helmet"),
    MINEMEN("Minemen",3000,20,"cobblestone"),
    QUIVER("Endless Quiver",2000,20,"anvil"),
    TRICKLE("Trickle-down",1000,40,"gold_ingot"),
    DIAMOND("Lucky Diamond",4000,40,"diamond"),
    SPAMMER("Spammer",4000,40,"bow"),
    BOUNTY("Bounty Hunter",2000,50,"golden_leggings"),
    STREAKER("Streaker",8000,50,"wheat"),
    GLADIATOR("Gladiator",4000,60,"fence"),
    VAMPIRE("Vampire",4000,60,"bone");
    public final String label;public final int price,level;private final String item;
    PitPerk(String label,int price,int level,String item){this.label=label;this.price=price;this.level=level;this.item=item;}
    public boolean matches(ItemStack stack){return stack!=null&&item.equals(Item.itemRegistry.getNameForObject(stack.getItem()).getResourcePath());}
    public static PitPerk from(ItemStack stack){for(PitPerk perk:values())if(perk.matches(stack))return perk;return null;}
    public static PitPerk named(String label){for(PitPerk perk:values())if(perk.label.equals(label))return perk;return null;}
    public static String[] options(){String[] all=new String[values().length+1];all[0]="None";for(int i=0;i<values().length;i++)all[i+1]=values()[i].label;return all;}
    public boolean affordable(PitStatus status,int slot){return status.active&&status.level>=Math.max(level,new int[]{10,35,70,20}[slot])&&status.gold>=price&&(slot!=3||status.prestige>=3);}
}

package dev.vibe.inventory;

import java.util.List;
import java.util.Locale;
import net.minecraft.init.Items;
import net.minecraft.item.*;
import net.minecraft.potion.*;

/** Common classification keeps the inventory editor and consumption policies consistent. */
public final class Consumables {
    private Consumables() { }
    public static boolean soup(ItemStack s) { return s != null && s.getItem() == Items.mushroom_stew; }
    public static boolean head(ItemStack s) {
        if (s == null) return false;
        if (s.getItem() == Items.skull && s.getMetadata() == 3) return true;
        return s.getItem() == Items.golden_apple && s.hasDisplayName()
                && s.getTagCompound().getCompoundTag("display").getString("Name").toLowerCase(Locale.ROOT).contains("head");
    }
    public static boolean gapple(ItemStack s) { return s != null && s.getItem() == Items.golden_apple && !head(s); }
    public static boolean food(ItemStack s) {
        return s != null && s.getItem() instanceof ItemFood && !soup(s) && !head(s) && !gapple(s);
    }
    public static List<PotionEffect> effects(ItemStack s) {
        return s != null && s.getItem() instanceof ItemPotion ? ((ItemPotion)s.getItem()).getEffects(s) : null;
    }
    public static boolean beneficialPotion(ItemStack s) {
        List<PotionEffect> list = effects(s);
        if (list == null || list.isEmpty()) return false;
        for (PotionEffect effect : list) {
            int id = effect.getPotionID();
            if (id < 0 || id >= Potion.potionTypes.length || Potion.potionTypes[id] == null || Potion.potionTypes[id].isBadEffect()) return false;
        }
        return true;
    }
}

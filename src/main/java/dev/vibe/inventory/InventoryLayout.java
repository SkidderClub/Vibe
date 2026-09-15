package dev.vibe.inventory;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * Compact persisted model shared by the editor and the inventory manager.
 * Slots 0-35 are the normal inventory (0-8 are the hotbar), 36-39 are
 * helmet/chestplate/leggings/boots, and 40-63 form the expandable drop bin.
 */
public final class InventoryLayout {

    public enum Type {
        SWORD("Sword", new ItemStack(Items.diamond_sword)),
        PICKAXE("Pickaxe", new ItemStack(Items.diamond_pickaxe)),
        AXE("Axe", new ItemStack(Items.diamond_axe)),
        SHOVEL("Shovel", new ItemStack(Items.diamond_shovel)),
        HOE("Hoe", new ItemStack(Items.diamond_hoe)),
        ROD("Rod", new ItemStack(Items.fishing_rod)),
        BOW("Bow", new ItemStack(Items.bow)),
        BLOCK("Blocks", new ItemStack(Blocks.planks)),
        STAIRS("Stairs", new ItemStack(Blocks.oak_stairs)),
        SLAB("Slabs", new ItemStack(Blocks.wooden_slab)),
        FLOWER("Flowers", new ItemStack(Blocks.red_flower)),
        SAND("Falling Blocks", new ItemStack(Blocks.sand)),
        BARRIER("Keep Free", new ItemStack(Blocks.barrier)),
        IGNORE("Ignore Slot", new ItemStack(Items.paper)),
        ARROW("Arrow", new ItemStack(Items.arrow)),
        FIREBALL("Fireball", new ItemStack(Items.fire_charge)),
        TNT("TNT", new ItemStack(Blocks.tnt)),
        GOLDEN_APPLE("Golden Apple", new ItemStack(Items.golden_apple)),
        FOOD("Food", new ItemStack(Items.carrot)),
        HEALING_POTION("Healing Potion", new ItemStack(Items.potionitem, 1, 16421)),
        COMPASS("Compass", new ItemStack(Items.compass)),
        ENDER_PEARL("Ender Pearl", new ItemStack(Items.ender_pearl)),
        SNOWBALL("Snowballs/Eggs", new ItemStack(Items.snowball)),
        FLINT_STEEL("Flint & Steel", new ItemStack(Items.flint_and_steel)),
        WATER_BUCKET("Water Bucket", new ItemStack(Items.water_bucket)),
        LAVA_BUCKET("Lava Bucket", new ItemStack(Items.lava_bucket)),
        HELMET("Helmet", new ItemStack(Items.diamond_helmet)),
        CHESTPLATE("Chestplate", new ItemStack(Items.diamond_chestplate)),
        LEGGINGS("Leggings", new ItemStack(Items.diamond_leggings)),
        BOOTS("Boots", new ItemStack(Items.diamond_boots));

        private final String label;
        private final ItemStack placeholder;

        Type(String label, ItemStack placeholder) {
            this.label = label;
            this.placeholder = placeholder;
        }

        public String getLabel() { return label; }
        public ItemStack createPlaceholder() { return placeholder.copy(); }
    }

    private final Map<Integer, Type> entries = new LinkedHashMap<Integer, Type>();

    public static InventoryLayout decode(String encoded) {
        InventoryLayout layout = new InventoryLayout();
        if (encoded == null || encoded.trim().isEmpty()) return layout;
        for (String token : encoded.split(";")) {
            String[] pair = token.split("=", 2);
            if (pair.length != 2) continue;
            try {
                int slot = Integer.parseInt(pair[0]);
                if (slot < 0 || slot > 63) continue;
                layout.entries.put(slot, Type.valueOf(pair[1]));
            } catch (Exception ignored) {
            }
        }
        return layout;
    }

    public String encode() {
        StringBuilder result = new StringBuilder();
        for (Map.Entry<Integer, Type> entry : entries.entrySet()) {
            if (result.length() > 0) result.append(';');
            result.append(entry.getKey()).append('=').append(entry.getValue().name());
        }
        return result.toString();
    }

    public Type get(int slot) { return entries.get(slot); }

    public void set(int slot, Type type) {
        if (slot < 0 || slot > 63) return;
        if (type == null) {
            entries.remove(slot);
        } else {
            // A category represents one preferred job, never a duplicated
            // item. Moving Sword/Blocks/etc. therefore relocates the single
            // assignment instead of silently creating a second copy.
            java.util.Iterator<Map.Entry<Integer, Type>> iterator = entries.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<Integer, Type> entry = iterator.next();
                if (entry.getKey() != slot && entry.getValue() == type) iterator.remove();
            }
            entries.put(slot, type);
        }
        if (slot >= 40) normalizeBin();
    }

    public boolean isAssigned(Type type) {
        return type != null && entries.containsValue(type);
    }

    public boolean isDropType(Type type) {
        if (type == null) return false;
        for (Map.Entry<Integer, Type> entry : entries.entrySet()) {
            if (entry.getKey() >= 40 && entry.getValue() == type && type != Type.IGNORE) return true;
        }
        return false;
    }

    public Map<Integer, Type> assignments() { return new LinkedHashMap<Integer, Type>(entries); }

    public int getDropBinSize() {
        int count = 0;
        for (Integer slot : entries.keySet()) if (slot >= 40) count++;
        return Math.min(24, count + 1);
    }

    /** An Ignore placeholder makes this physical inventory position untouchable. */
    public boolean isIgnoredContainerSlot(int containerSlot) {
        for (Map.Entry<Integer, Type> entry : entries.entrySet()) {
            if (entry.getValue() == Type.IGNORE && toContainerSlot(entry.getKey()) == containerSlot) return true;
        }
        return false;
    }

    private void normalizeBin() {
        java.util.List<Type> types = new java.util.ArrayList<Type>();
        for (int slot = 40; slot <= 63; slot++) {
            Type type = entries.remove(slot);
            if (type != null) types.add(type);
        }
        for (int index = 0; index < types.size() && index < 24; index++) entries.put(40 + index, types.get(index));
    }

    public static int toContainerSlot(int layoutSlot) {
        if (layoutSlot >= 0 && layoutSlot <= 8) return 36 + layoutSlot;
        if (layoutSlot >= 9 && layoutSlot <= 35) return layoutSlot;
        if (layoutSlot >= 36 && layoutSlot <= 39) return 5 + (layoutSlot - 36);
        return -1;
    }

    public static boolean matches(Type type, ItemStack stack) {
        if (type == null || stack == null || stack.getItem() == null) return false;
        Item item = stack.getItem();
        switch (type) {
            case SWORD: return item instanceof net.minecraft.item.ItemSword;
            case PICKAXE: return item instanceof net.minecraft.item.ItemPickaxe;
            case AXE: return item instanceof net.minecraft.item.ItemAxe;
            case SHOVEL: return item instanceof net.minecraft.item.ItemSpade;
            case HOE: return item instanceof net.minecraft.item.ItemHoe;
            case ROD: return item == Items.fishing_rod;
            case BOW: return item == Items.bow;
            case BLOCK: return item instanceof net.minecraft.item.ItemBlock && !isStairs(stack) && !isSlab(stack);
            case STAIRS: return isStairs(stack);
            case SLAB: return isSlab(stack);
            case FLOWER: return item == Item.getItemFromBlock(Blocks.red_flower) || item == Item.getItemFromBlock(Blocks.yellow_flower);
            case SAND: return item == Item.getItemFromBlock(Blocks.sand) || item == Item.getItemFromBlock(Blocks.gravel)
                    || item == Item.getItemFromBlock(Blocks.anvil);
            case ARROW: return item == Items.arrow;
            case FIREBALL: return item == Items.fire_charge;
            case TNT: return item == Item.getItemFromBlock(Blocks.tnt);
            case GOLDEN_APPLE: return item == Items.golden_apple;
            case FOOD: return item instanceof net.minecraft.item.ItemFood && item != Items.golden_apple;
            case HEALING_POTION: return healingPotion(stack);
            case COMPASS: return item == Items.compass;
            case ENDER_PEARL: return item == Items.ender_pearl;
            case SNOWBALL: return item == Items.snowball || item == Items.egg;
            case FLINT_STEEL: return item == Items.flint_and_steel;
            case WATER_BUCKET: return item == Items.water_bucket;
            case LAVA_BUCKET: return item == Items.lava_bucket;
            case HELMET: return armor(stack, 0);
            case CHESTPLATE: return armor(stack, 1);
            case LEGGINGS: return armor(stack, 2);
            case BOOTS: return armor(stack, 3);
            default: return false;
        }
    }

    private static boolean armor(ItemStack stack, int armourType) {
        return stack.getItem() instanceof net.minecraft.item.ItemArmor
                && ((net.minecraft.item.ItemArmor) stack.getItem()).armorType == armourType;
    }

    private static boolean isStairs(ItemStack stack) {
        return stack.getItem() instanceof net.minecraft.item.ItemBlock
                && ((net.minecraft.item.ItemBlock) stack.getItem()).getBlock() instanceof net.minecraft.block.BlockStairs;
    }

    private static boolean isSlab(ItemStack stack) {
        return stack.getItem() instanceof net.minecraft.item.ItemBlock
                && ((net.minecraft.item.ItemBlock) stack.getItem()).getBlock() instanceof net.minecraft.block.BlockSlab;
    }

    private static boolean healingPotion(ItemStack stack) {
        if (!(stack.getItem() instanceof net.minecraft.item.ItemPotion)
                || !net.minecraft.item.ItemPotion.isSplash(stack.getItemDamage())) return false;
        java.util.List<net.minecraft.potion.PotionEffect> effects = ((net.minecraft.item.ItemPotion) stack.getItem()).getEffects(stack);
        if (effects == null) return false;
        for (net.minecraft.potion.PotionEffect effect : effects) {
            if (effect.getPotionID() == net.minecraft.potion.Potion.heal.id) return true;
        }
        return false;
    }
}

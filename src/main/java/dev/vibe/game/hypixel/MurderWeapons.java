package dev.vibe.game.hypixel;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

/** Knife signatures use registry IDs; @damage distinguishes creative item variants. */
public final class MurderWeapons {
    public static final String LEGACY_DEFAULTS = "iron_sword,stone_sword,golden_sword,diamond_sword,wooden_sword,shears";
    // All 47 skins in the supplied inventory screenshots, in screenshot order.
    public static final String DEFAULTS = String.join(",", Arrays.asList(
            "iron_sword", "chest", "ender_chest", "stone_sword", "iron_shovel", "stick", "wooden_axe",
            "wooden_sword", "deadbush", "reeds", "stone_shovel", "blaze_rod", "diamond_shovel", "quartz",
            "pumpkin_pie", "golden_pickaxe", "leather", "name_tag", "coal@0", "flint", "bone",
            "carrot", "golden_carrot", "cookie", "diamond_axe", "double_plant@4", "prismarine_shard", "cooked_beef",
            "netherbrick", "cooked_chicken", "record_blocks", "golden_hoe", "dye@4", "golden_sword", "diamond_sword",
            "diamond_hoe", "shears", "fish@1", "dye@1", "bread", "boat", "speckled_melon",
            "book", "sapling@3", "golden_axe", "diamond_pickaxe", "golden_shovel"));

    private MurderWeapons() { }

    public static Set<String> parse(String csv) {
        Set<String> result = new LinkedHashSet<String>();
        if (csv != null) for (String entry : csv.split(",")) {
            String key = normalize(entry);
            if (!key.isEmpty()) result.add(key);
        }
        return result;
    }

    public static String normalize(String value) {
        String key = value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        return key.startsWith("minecraft:") ? key.substring(10) : key;
    }

    public static boolean isLegacyDefault(String csv) { return parse(LEGACY_DEFAULTS).equals(parse(csv)); }

    public static String itemId(ItemStack stack) {
        ResourceLocation id = stack == null ? null : Item.itemRegistry.getNameForObject(stack.getItem());
        return id == null ? "" : normalize(id.toString());
    }

    public static String signature(ItemStack stack) {
        String id = itemId(stack);
        return stack != null && stack.getHasSubtypes() ? id + "@" + stack.getMetadata() : id;
    }

    public static boolean matches(Set<String> signatures, ItemStack stack) {
        if (stack == null || stack.stackSize <= 0 || stack.getItem() == null) return false;
        String id = itemId(stack);
        if (signatures.contains(id) || signatures.contains(signature(stack))) return true;
        // Keep old hand-written, unlocalized names working without using custom display names.
        String legacy = stack.getItem().getUnlocalizedName().replace("item.", "").replace("tile.", "");
        return signatures.contains(normalize(legacy));
    }

    public static ItemStack stack(String signature) {
        String key = normalize(signature);
        int at = key.lastIndexOf('@'), metadata = 0;
        if (at >= 0) {
            try { metadata = Integer.parseInt(key.substring(at + 1)); }
            catch (NumberFormatException invalid) { return null; }
            if (metadata < 0 || metadata > 32767) return null;
            key = key.substring(0, at);
        }
        Item item = Item.itemRegistry.getObject(new ResourceLocation(key));
        if (item == null) {
            for (Object value : Item.itemRegistry) if (value instanceof Item) {
                Item candidate = (Item) value;
                String legacy = normalize(candidate.getUnlocalizedName().replace("item.", "").replace("tile.", ""));
                if (legacy.equals(key)) { item = candidate; break; }
            }
        }
        return item == null ? null : new ItemStack(item, 1, metadata);
    }

    public static Set<String> defaults() { return Collections.unmodifiableSet(parse(DEFAULTS)); }
}

package dev.vibe.inventory;

import java.util.*;
import net.minecraft.enchantment.*;
import net.minecraft.init.*;
import net.minecraft.item.*;

/** Pure, live-inventory crafting decisions. Each plan makes one necessary item or prerequisite. */
public final class CraftingPlanner {
    public static final class Ingredient {
        public final Item item; public final int metadata;
        public Ingredient(Item item, int metadata) { this.item = item; this.metadata = metadata; }
        public boolean matches(ItemStack s) { return s != null && s.stackSize > 0 && s.getItem() == item && (metadata < 0 || s.getMetadata() == metadata); }
    }
    public static final class Recipe {
        public final String name;
        public final ItemStack output;
        public final Ingredient[] grid;
        Recipe(String name, ItemStack output, Ingredient[] grid) { this.name = name; this.output = output; this.grid = grid; }
        public boolean matchesOutput(ItemStack s) { return s != null && ItemStack.areItemsEqual(output, s) && s.stackSize == output.stackSize; }
    }
    private static final class Material {
        final String name; final Item resource; final ItemSword sword; final ItemArmor[] armor;
        Material(String name, Item resource, Item sword, Item... armor) {
            this.name = name; this.resource = resource; this.sword = (ItemSword)sword;
            this.armor = new ItemArmor[armor.length]; for (int i = 0; i < armor.length; i++) this.armor[i] = (ItemArmor)armor[i];
        }
    }
    private static List<Material> materials() {
        return Arrays.asList(
                new Material("Diamond", Items.diamond, Items.diamond_sword, Items.diamond_helmet, Items.diamond_chestplate, Items.diamond_leggings, Items.diamond_boots),
                new Material("Iron", Items.iron_ingot, Items.iron_sword, Items.iron_helmet, Items.iron_chestplate, Items.iron_leggings, Items.iron_boots),
                new Material("Stone", Item.getItemFromBlock(Blocks.cobblestone), Items.stone_sword),
                new Material("Wood", Item.getItemFromBlock(Blocks.planks), Items.wooden_sword),
                new Material("Gold", Items.gold_ingot, Items.golden_sword, Items.golden_helmet, Items.golden_chestplate, Items.golden_leggings, Items.golden_boots),
                new Material("Leather", Items.leather, null, Items.leather_helmet, Items.leather_chestplate, Items.leather_leggings, Items.leather_boots));
    }
    private static final String[] PIECES = {"Helmet","Chestplate","Leggings","Boots"};
    private static final int[][] ARMOR_GRID = {
            {0,1,2,3,5}, {0,2,3,4,5,6,7,8}, {0,1,2,3,5,6,8}, {0,2,3,5}};
    public Recipe next(ItemStack[] inventory, boolean[] usable, Set<String> gear, String selection) {
        List<Material> materials = materials();
        if (gear.contains("Sword")) {
            // The 1.8.9 accessor reports the material bonus: wood/gold are zero.
            // An absent sword must rank below that legitimate zero-bonus sword.
            double existing = Double.NEGATIVE_INFINITY;
            for (ItemStack s : inventory) if (s != null && s.getItem() instanceof ItemSword)
                existing = Math.max(existing, ((ItemSword)s.getItem()).getDamageVsEntity() + EnchantmentHelper.getEnchantmentLevel(Enchantment.sharpness.effectId, s) * 1.25);
            for (Material m : materials) {
                Ingredient resource = new Ingredient(m.resource, -1);
                if (m.sword == null || !allowed(selection, m) || m.sword.getDamageVsEntity() <= existing) continue;
                Ingredient stick = new Ingredient(Items.stick, -1), planks = new Ingredient(Item.getItemFromBlock(Blocks.planks), -1);
                if (count(inventory, usable, resource) < 2) {
                    if (m.resource == planks.item) {
                        Recipe prerequisite = planks(inventory, usable);
                        if (prerequisite != null) return prerequisite;
                    }
                    continue;
                }
                if (count(inventory, usable, stick) > 0) return shaped(m.name + " Sword", new ItemStack(m.sword), resource, new int[]{1,4}, stick, new int[]{7});
                int requiredPlanks = m.resource == planks.item ? 4 : 2;
                if (count(inventory, usable, planks) >= requiredPlanks)
                    return shaped("Sticks", new ItemStack(Items.stick, 4), planks, new int[]{1,4}, null, new int[0]);
                Recipe prerequisite = planks(inventory, usable);
                if (prerequisite != null) return prerequisite;
                if (m.resource == planks.item) continue;
                // Reserve the resources for the requested sword until its stick can be made.
                return null;
            }
        }
        Recipe best = null; int bestGain = 0, bestCost = Integer.MAX_VALUE;
        for (Material m : materials) {
            if (!allowed(selection, m)) continue;
            for (int part = 0; part < m.armor.length; part++) {
                if (!gear.contains(PIECES[part])) continue;
                ItemArmor candidate = m.armor[part];
                int current = 0; boolean sameOrBetter = false;
                for (ItemStack s : inventory) if (s != null && s.getItem() instanceof ItemArmor && ((ItemArmor)s.getItem()).armorType == part) {
                    ItemArmor owned = (ItemArmor)s.getItem();
                    current = Math.max(current, owned.damageReduceAmount);
                    if (rank(owned.getArmorMaterial()) >= rank(candidate.getArmorMaterial())) sameOrBetter = true;
                }
                int gain = candidate.damageReduceAmount - current, cost = ARMOR_GRID[part].length;
                if (sameOrBetter || gain <= 0 || count(inventory, usable, new Ingredient(m.resource, -1)) < cost) continue;
                if (gain > bestGain || (gain == bestGain && cost < bestCost)) {
                    best = shaped(m.name + " " + PIECES[part], new ItemStack(candidate), new Ingredient(m.resource, -1), ARMOR_GRID[part], null, new int[0]);
                    bestGain = gain; bestCost = cost;
                }
            }
        }
        return best;
    }
    private static int rank(ItemArmor.ArmorMaterial material) {
        switch (material) { case DIAMOND:return 4;case IRON:return 3;case CHAIN:return 2;case GOLD:return 1;default:return 0; }
    }
    private static Recipe planks(ItemStack[] inventory, boolean[] usable) {
        for (int i = 0; i < inventory.length; i++) if (usable[i] && inventory[i] != null) {
            ItemStack log = inventory[i]; Item item = log.getItem();
            if (item == Item.getItemFromBlock(Blocks.log) || item == Item.getItemFromBlock(Blocks.log2)) {
                int metadata = (log.getMetadata() & 3) + (item == Item.getItemFromBlock(Blocks.log2) ? 4 : 0);
                return shaped("Planks", new ItemStack(Blocks.planks, 4, metadata), new Ingredient(item, log.getMetadata()), new int[]{4}, null, new int[0]);
            }
        }
        return null;
    }
    private static boolean allowed(String selection, Material m) { return selection.equals("Best Available") || selection.equals(m.name); }
    private static int count(ItemStack[] inventory, boolean[] usable, Ingredient ingredient) {
        int count = 0; for (int i = 0; i < inventory.length; i++) if (usable[i] && ingredient.matches(inventory[i])) count += inventory[i].stackSize;
        return count;
    }
    private static Recipe shaped(String name, ItemStack out, Ingredient a, int[] slotsA, Ingredient b, int[] slotsB) {
        Ingredient[] grid = new Ingredient[9]; for (int i : slotsA) grid[i] = a; for (int i : slotsB) grid[i] = b;
        return new Recipe(name, out, grid);
    }
}

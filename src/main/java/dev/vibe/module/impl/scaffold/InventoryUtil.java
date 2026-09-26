/*
 * Ported from Gothaj (CFR 0.152), revision 8aa9e1c056bc5b6393b29dc03bcb06b529b40a07.
 * Source mapping and Forge adaptations: LICENSES/GOTHAJ-SCAFFOLD.md.
 */
package dev.vibe.module.impl.scaffold;

import java.util.Arrays;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

public final class InventoryUtil {
    public static final Minecraft mc = Minecraft.getMinecraft();
    public static final List<Block> invalidBlocks = Arrays.asList(Blocks.enchanting_table, Blocks.carpet, Blocks.glass_pane, Blocks.ladder, Blocks.web, Blocks.stained_glass_pane, Blocks.iron_bars, Blocks.air, Blocks.water, Blocks.flowing_water, Blocks.lava, Blocks.ladder, Blocks.soul_sand, Blocks.ice, Blocks.sand, Blocks.flowing_lava, Blocks.snow_layer, Blocks.chest, Blocks.ender_chest, Blocks.torch, Blocks.anvil, Blocks.trapped_chest, Blocks.noteblock, Blocks.jukebox, Blocks.wooden_pressure_plate, Blocks.stone_pressure_plate, Blocks.light_weighted_pressure_plate, Blocks.heavy_weighted_pressure_plate, Blocks.stone_button, Blocks.tnt, Blocks.wooden_button, Blocks.lever, Blocks.crafting_table, Blocks.furnace, Blocks.stone_slab, Blocks.wooden_slab, Blocks.stone_slab2, Blocks.brown_mushroom, Blocks.red_mushroom, Blocks.gold_block, Blocks.red_flower, Blocks.yellow_flower, Blocks.flower_pot);
    public static int getBlockSlot() {
        int heldStack;
        ItemBlock heldBlock;
        int bestSlot = -1;
        int largestStack = 0;
        if (InventoryUtil.mc.thePlayer.getHeldItem() != null && InventoryUtil.mc.thePlayer.getHeldItem().getItem() instanceof ItemBlock && !invalidBlocks.contains((heldBlock = (ItemBlock)InventoryUtil.mc.thePlayer.getHeldItem().getItem()).getBlock()) && (heldStack = InventoryUtil.mc.thePlayer.getHeldItem().stackSize) > 1) {
            return InventoryUtil.mc.thePlayer.inventory.currentItem;
        }
        for (int i = 36; i < 45; ++i) {
            Block block;
            ItemStack stack = InventoryUtil.mc.thePlayer.inventoryContainer.getSlot(i).getStack();
            if (stack == null || !(stack.getItem() instanceof ItemBlock) || invalidBlocks.contains(block = ((ItemBlock)stack.getItem()).getBlock()) || stack.stackSize <= 0 || stack.stackSize <= largestStack) continue;
            largestStack = stack.stackSize;
            bestSlot = i - 36;
        }
        return bestSlot;
    }

    public static int getBlockCount() {
        int blockCount = 0;
        for (int i = 9; i < 45; ++i) {
            ItemStack stack = InventoryUtil.mc.thePlayer.inventoryContainer.getSlot(i).getStack();
            if (stack == null || !(stack.getItem() instanceof ItemBlock) || invalidBlocks.contains(((ItemBlock)stack.getItem()).getBlock())) continue;
            blockCount += stack.stackSize;
        }
        return blockCount;
    }

    public static int findSlot(Item item) {
        ItemStack[] inv = InventoryUtil.mc.thePlayer.inventory.mainInventory;
        for (int i = 0; i < inv.length; ++i) {
            ItemStack s = inv[i];
            if (s == null || s.getItem() != item) continue;
            return i;
        }
        return -1;
    }
}

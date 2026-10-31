package dev.vibe.module.impl.world;

import dev.vibe.Vibe;
import dev.vibe.inventory.*;
import dev.vibe.inventory.CraftingPlanner.*;
import dev.vibe.module.Category;
import dev.vibe.module.impl.client.InventoryEditorModule;
import dev.vibe.setting.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiCrafting;
import net.minecraft.inventory.*;
import net.minecraft.item.*;
import org.lwjgl.input.*;

/** One validated vanilla controller interaction per delay; never uses a custom craft packet. */
public final class AutoCrafterModule extends dev.vibe.module.Module {
    public final MultiSelectSetting gear = addSetting(new MultiSelectSetting("Craft", Arrays.asList("Sword","Helmet","Chestplate","Leggings","Boots"), Arrays.asList("Sword","Helmet","Chestplate","Leggings","Boots")));
    public final ModeSetting material = addSetting(new ModeSetting("Material", "Best Available", "Best Available","Diamond","Iron","Gold","Stone","Wood","Leather"));
    public final RangeSetting startDelay = addSetting(new RangeSetting("Start Delay (ms)", 250, 400, 0, 3000, 10));
    public final RangeSetting clickDelay = addSetting(new RangeSetting("Click Delay (ms)", 150, 220, 0, 2000, 10));
    public final RangeSetting craftDelay = addSetting(new RangeSetting("Craft Delay (ms)", 200, 350, 0, 3000, 10));
    public final RangeSetting recipeDelay = addSetting(new RangeSetting("Recipe Delay (ms)", 250, 400, 0, 3000, 10));
    private final Minecraft mc = Minecraft.getMinecraft();
    private final Random random = new Random();
    private final ContainerClickDispatcher clicks = new ContainerClickDispatcher();
    private final CraftingPlanner planner = new CraftingPlanner();
    private ContainerWorkbench table;
    private Recipe recipe;
    private int source = -1;
    private ItemStack carried;
    private long due;
    private long tickTime;
    private boolean readyResult;
    public AutoCrafterModule() { super("Crafter", "Craft necessary swords and armor in an open crafting table", Category.WORLD, Keyboard.KEY_NONE); }
    public void tick() { tick(System.currentTimeMillis()); }
    public void tick(long now) {
        tickTime = now;
        if (!isEnabled() || mc.thePlayer == null || !mc.thePlayer.isEntityAlive() || !(mc.currentScreen instanceof GuiCrafting)
                || !(mc.thePlayer.openContainer instanceof ContainerWorkbench)) { reset(); return; }
        ContainerWorkbench live = (ContainerWorkbench)mc.thePlayer.openContainer;
        if (table != live) { reset(); table = live; due = now + pick(startDelay); return; }
        if (Mouse.isCreated() && (Mouse.isButtonDown(0) || Mouse.isButtonDown(1))) {
            recipe = null; source = -1; carried = null; readyResult = false; due = now + pick(startDelay); return;
        }
        if (PlayerInventorySession.isBusy() || now < due) return;
        if (recipe == null) {
            if (mc.thePlayer.inventory.getItemStack() != null || !emptyGrid()) return;
            InventoryLayout layout = layout();
            ItemStack[] items = new ItemStack[40]; boolean[] usable = new boolean[40];
            for (int i = 0; i < 36; i++) { items[i] = mc.thePlayer.inventory.getStackInSlot(i); usable[i] = layout.get(i) != InventoryLayout.Type.IGNORE; }
            for (int i = 0; i < 4; i++) items[36 + i] = mc.thePlayer.getCurrentArmor(i);
            Set<String> selected = new HashSet<String>(); for (String option : gear.getOptions()) if (gear.isSelectedIgnoreCase(option)) selected.add(option);
            recipe = planner.next(items, usable, selected, material.getValue());
            if (recipe == null) { due = now + 250; return; }
        }
        ItemStack cursor = mc.thePlayer.inventory.getItemStack();
        if (!validGrid()) { abort(); return; }
        if (source >= 0) {
            if (cursor == null) {
                if (carried != null) { abort(); return; }
                source = -1;
            } else {
                if (carried == null || !same(cursor, carried)) { abort(); return; }
                int target = missingFor(cursor);
                if (target >= 0) {
                    if (click(target, 1, 0, clickDelay)) {
                        ItemStack after = mc.thePlayer.inventory.getItemStack();
                        if (after == null) { source = -1; carried = null; }
                        else carried = after.copy();
                    }
                } else if (canReturn(source, cursor)) {
                    if (click(source, 0, 0, clickDelay)) { source = -1; carried = null; }
                } else {
                    int empty = emptyInventorySlot();
                    if (empty >= 0 && click(empty, 0, 0, clickDelay)) { source = -1; carried = null; }
                }
                return;
            }
        }
        if (cursor != null) return;
        int missing = firstMissing();
        if (missing >= 0) {
            Ingredient wanted = recipe.grid[missing - 1]; int from = findIngredient(wanted);
            if (from < 0) { abort(); return; }
            ItemStack expected = table.getSlot(from).getStack().copy();
            if (click(from, 0, 0, clickDelay)) {
                ItemStack after = mc.thePlayer.inventory.getItemStack();
                if (after == null || !same(after, expected)) { abort(); return; }
                source = from; carried = after.copy();
            }
            return;
        }
        if (!recipe.matchesOutput(table.getSlot(0).getStack())) { abort(); return; }
        if (!readyResult) { readyResult = true; due = now + pick(craftDelay); return; }
        if (!canFit(recipe.output)) return;
        if (click(0, 0, 1, recipeDelay)) {
            // A shift-click consumes exactly the single recipe placed above. Rejected state
            // is not replayed as another click or cleared out from underneath the server.
            recipe = null; readyResult = false; carried = null; source = -1;
        }
    }
    private boolean click(int slot, int button, int mode, RangeSetting delay) {
        long interval = pick(delay);
        if (!clicks.click(table, slot, button, mode, interval)) return false;
        due = tickTime + Math.max(125, interval); return true;
    }
    private boolean validGrid() {
        for (int i = 1; i <= 9; i++) {
            ItemStack s = table.getSlot(i).getStack(); Ingredient ingredient = recipe.grid[i - 1];
            if (s != null && (ingredient == null || !ingredient.matches(s) || s.stackSize != 1)) return false;
        }
        return true;
    }
    private int missingFor(ItemStack s) { for (int i = 1; i <= 9; i++) if (recipe.grid[i - 1] != null && table.getSlot(i).getStack() == null && recipe.grid[i - 1].matches(s)) return i; return -1; }
    private int firstMissing() { for (int i = 1; i <= 9; i++) if (recipe.grid[i - 1] != null && table.getSlot(i).getStack() == null) return i; return -1; }
    private boolean emptyGrid() { for (int i = 1; i <= 9; i++) if (table.getSlot(i).getHasStack()) return false; return true; }
    private int findIngredient(Ingredient ingredient) {
        InventoryLayout layout = layout();
        for (int i = 10; i < table.inventorySlots.size(); i++) {
            Slot slot = table.getSlot(i);
            if (slot.inventory == mc.thePlayer.inventory && layout.get(slot.getSlotIndex()) != InventoryLayout.Type.IGNORE && ingredient.matches(slot.getStack())) return i;
        }
        return -1;
    }
    private boolean canReturn(int slot, ItemStack s) {
        ItemStack existing = table.getSlot(slot).getStack();
        return existing == null || (same(existing, s) && existing.stackSize + s.stackSize <= Math.min(existing.getMaxStackSize(), table.getSlot(slot).getSlotStackLimit()));
    }
    private boolean canFit(ItemStack s) {
        int remaining = s.stackSize;
        for (int i = 10; i < table.inventorySlots.size(); i++) {
            ItemStack existing = table.getSlot(i).getStack();
            if (existing == null) remaining -= Math.min(s.getMaxStackSize(), table.getSlot(i).getSlotStackLimit());
            else if (same(existing, s)) remaining -= Math.max(0, Math.min(existing.getMaxStackSize(), table.getSlot(i).getSlotStackLimit()) - existing.stackSize);
        }
        return remaining <= 0;
    }
    private int emptyInventorySlot() { for (int i = 10; i < table.inventorySlots.size(); i++) if (!table.getSlot(i).getHasStack()) return i; return -1; }
    private static boolean same(ItemStack a, ItemStack b) { return a != null && b != null && ItemStack.areItemsEqual(a, b) && ItemStack.areItemStackTagsEqual(a, b); }
    private InventoryLayout layout() { InventoryEditorModule e = Vibe.getInstance().getModuleManager().getModule(InventoryEditorModule.class); return e == null ? new InventoryLayout() : e.getLayout(); }
    private long pick(RangeSetting s) { return s.getMinInt() + random.nextInt(s.getMaxInt() - s.getMinInt() + 1); }
    private void abort() { recipe = null; source = -1; carried = null; readyResult = false; due = tickTime + 500; }
    private void reset() { abort(); table = null; clicks.reset(); }
    @Override protected void onDisable() { reset(); }
}

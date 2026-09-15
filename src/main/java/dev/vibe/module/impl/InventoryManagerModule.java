package dev.vibe.module.impl;

import dev.vibe.Vibe;
import dev.vibe.inventory.ContainerClickDispatcher;
import dev.vibe.inventory.InventoryLayout;
import dev.vibe.inventory.InventoryLayout.Type;
import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.setting.BooleanSetting;
import dev.vibe.setting.ModeSetting;
import dev.vibe.setting.NumberSetting;
import dev.vibe.setting.RangeSetting;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerPlayer;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemFood;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemTool;
import org.lwjgl.input.Keyboard;

/**
 * Inventory sorter using RavenBS-style, one-action-at-a-time container I/O.
 * A plan is recomputed from the live container and only one vanilla
 * PlayerController.windowClick is sent per delay interval. Pickup swaps are
 * never queued as raw clicks: the carried stack is checked between each of
 * their three steps, so a rejected transaction cannot desynchronise the next
 * click or touch a newly opened container.
 */
public final class InventoryManagerModule extends Module {
    private final ModeSetting mode = addSetting(new ModeSetting("Mode", "OpenInv", "OpenInv", "Vanilla"));
    private final RangeSetting startDelay = addSetting(new RangeSetting("Start Delay (ms)", 160.0D, 320.0D, 0.0D, 2500.0D, 10.0D));
    private final RangeSetting delay = addSetting(new RangeSetting("Sort Delay (ms)", 80.0D, 150.0D, 0.0D, 1500.0D, 5.0D));
    private final BooleanSetting dropDuplicates = addSetting(new BooleanSetting("Drop Duplicates", false));
    private final NumberSetting maxFoodSlots = addSetting(new NumberSetting("Max Food Slots", 8.0D, 0.0D, 36.0D, 1.0D));
    private final NumberSetting maxBlockSlots = addSetting(new NumberSetting("Max Block Slots", 18.0D, 0.0D, 36.0D, 1.0D));
    private final Minecraft minecraft = Minecraft.getMinecraft();
    private final Random random = new Random();
    private final ContainerClickDispatcher clicks = new ContainerClickDispatcher();
    private PendingAction pendingAction;
    private long startedAt;
    private long nextAction;
    private int cursorSlot = -1;
    private long cursorUntil;

    public InventoryManagerModule() {
        super("Inventory Manager", "Sort tools, armour and supplies into the Inventory Editor layout", Category.WORLD, Keyboard.KEY_NONE);
    }

    public void tick() {
        if (!isEnabled() || minecraft.thePlayer == null || !canManage()) {
            resetRuntime();
            return;
        }
        // Never issue a player-inventory click while another container owns
        // the active window. This is the same live-window guard RavenBS uses
        // before every delayedClick call.
        if (!(minecraft.currentScreen instanceof GuiInventory)
                || minecraft.thePlayer.openContainer != minecraft.thePlayer.inventoryContainer
                || !(minecraft.thePlayer.inventoryContainer instanceof ContainerPlayer)) {
            resetRuntime();
            return;
        }

        Container container = minecraft.thePlayer.inventoryContainer;
        long now = System.currentTimeMillis();
        if (startedAt == 0L) {
            startedAt = now;
            nextAction = now + pick(startDelay);
            return;
        }
        if (now < nextAction) return;

        InventoryLayout layout = layout();
        if (pendingAction == null) {
            pendingAction = arrangeOne(container, layout);
            if (pendingAction == null) pendingAction = dropOneBinItem(container, layout);
            if (pendingAction == null && dropDuplicates.isEnabled()) pendingAction = dropOneDuplicate(container, layout);
            if (pendingAction == null) pendingAction = dropExcess(container, layout);
        }
        if (pendingAction == null) {
            nextAction = now + Math.max(250L, pick(delay));
            return;
        }

        boolean sent = executePending(container, pendingAction, now);
        if (sent || pendingAction == null) {
            // The next step is delayed exactly like RavenBS's delayedClick;
            // no burst of pickup, put and return clicks is emitted in one
            // client tick.
            nextAction = now + Math.max(125L, pick(delay));
        }
    }

    @Override
    protected void onDisable() {
        resetRuntime();
    }

    private boolean canManage() {
        return !mode.is("OpenInv") || minecraft.currentScreen instanceof GuiInventory;
    }

    private InventoryLayout layout() {
        InventoryEditorModule editor = Vibe.getInstance().getModuleManager().getModule(InventoryEditorModule.class);
        return editor == null ? new InventoryLayout() : editor.getLayout();
    }

    private PendingAction arrangeOne(Container container, InventoryLayout layout) {
        for (Map.Entry<Integer, Type> assignment : layout.assignments().entrySet()) {
            if (assignment.getKey() >= 40) continue;
            int target = InventoryLayout.toContainerSlot(assignment.getKey());
            if (target < 0 || target >= container.inventorySlots.size() || layout.isIgnoredContainerSlot(target)) continue;
            Type type = assignment.getValue();
            ItemStack existing = container.getSlot(target).getStack();
            if (type == Type.BARRIER) {
                if (existing != null) return PendingAction.drop(target, existing);
                continue;
            }
            int source = findBest(container, layout, type, target);
            if (source < 0 || source == target) continue;
            ItemStack best = container.getSlot(source).getStack();
            if (!InventoryLayout.matches(type, existing) || score(best) > score(existing) + 0.001D) {
                // Mode 2 is the atomic vanilla number-key swap for hotbar
                // assignments. It avoids manufacturing a carried stack.
                if (assignment.getKey() >= 0 && assignment.getKey() <= 8) {
                    return PendingAction.hotbarSwap(source, assignment.getKey(), type);
                }
                return PendingAction.pickup(source, target, type);
            }
        }
        return null;
    }

    private int findBest(Container container, InventoryLayout layout, Type type, int preferred) {
        int best = InventoryLayout.matches(type, container.getSlot(preferred).getStack()) ? preferred : -1;
        double bestScore = best < 0 ? Double.NEGATIVE_INFINITY : score(container.getSlot(best).getStack());
        for (int slot : playerSlots()) {
            if (layout.isIgnoredContainerSlot(slot)) continue;
            ItemStack stack = container.getSlot(slot).getStack();
            if (!InventoryLayout.matches(type, stack)) continue;
            double value = score(stack);
            if (best < 0 || value > bestScore) {
                best = slot;
                bestScore = value;
            }
        }
        return best;
    }

    private PendingAction dropOneBinItem(Container container, InventoryLayout layout) {
        for (int slot : playerSlots()) {
            if (layout.isIgnoredContainerSlot(slot)) continue;
            ItemStack stack = container.getSlot(slot).getStack();
            Type type = classify(stack);
            if (type != null && layout.isDropType(type)) return PendingAction.drop(slot, stack);
        }
        return null;
    }

    private PendingAction dropOneDuplicate(Container container, InventoryLayout layout) {
        for (int slot : playerSlots()) {
            if (layout.isIgnoredContainerSlot(slot)) continue;
            ItemStack stack = container.getSlot(slot).getStack();
            Type type = classify(stack);
            if (type == null || !isUnique(type)) continue;
            // Preserve the existing cleaner priority and tie behaviour. Only
            // the execution of the selected action is new.
            int best = findBest(container, layout, type, slot);
            if (best >= 0 && best != slot && score(container.getSlot(best).getStack()) >= score(stack)) {
                return PendingAction.drop(slot, stack);
            }
        }
        return null;
    }

    private PendingAction dropExcess(Container container, InventoryLayout layout) {
        List<Integer> food = new ArrayList<Integer>();
        List<Integer> blocks = new ArrayList<Integer>();
        for (int slot : playerSlots()) {
            if (layout.isIgnoredContainerSlot(slot)) continue;
            ItemStack stack = container.getSlot(slot).getStack();
            if (stack == null) continue;
            if (stack.getItem() instanceof ItemFood && stack.getItem() != Items.golden_apple) food.add(slot);
            if (stack.getItem() instanceof ItemBlock) blocks.add(slot);
        }
        if (food.size() > maxFoodSlots.getInt()) {
            int slot = food.get(food.size() - 1);
            return PendingAction.drop(slot, container.getSlot(slot).getStack());
        }
        if (blocks.size() > maxBlockSlots.getInt()) {
            int smallest = blocks.get(0);
            for (int slot : blocks) {
                if (container.getSlot(slot).getStack().stackSize < container.getSlot(smallest).getStack().stackSize) smallest = slot;
            }
            return PendingAction.drop(smallest, container.getSlot(smallest).getStack());
        }
        return null;
    }

    private boolean executePending(Container container, PendingAction action, long now) {
        if (action == null || container != minecraft.thePlayer.openContainer || action.slot < 0
                || action.slot >= container.inventorySlots.size()) {
            pendingAction = null;
            return false;
        }
        long interval = Math.max(125L, pick(delay));
        if (action.kind == ActionKind.DROP) {
            ItemStack stack = container.getSlot(action.slot).getStack();
            // Drops keep the exact original manager decision (including
            // Barrier and stair/slab slots) but refuse a stale action when
            // that slot now contains another item.
            if (stack == null || stack.getItem() != action.expectedItem || stack.getItemDamage() != action.expectedDamage) {
                pendingAction = null;
                return false;
            }
            if (!clicks.click(container, action.slot, 1, 4, interval)) return false;
            cursorSlot = action.slot;
            cursorUntil = now + 450L;
            pendingAction = null;
            return true;
        }
        if (action.kind == ActionKind.HOTBAR_SWAP) {
            ItemStack source = container.getSlot(action.slot).getStack();
            if (!InventoryLayout.matches(action.type, source) || action.hotbarButton < 0 || action.hotbarButton > 8) {
                pendingAction = null;
                return false;
            }
            if (!clicks.click(container, action.slot, action.hotbarButton, 2, interval)) return false;
            cursorSlot = action.slot;
            cursorUntil = now + 450L;
            pendingAction = null;
            return true;
        }

        // Pickup, put and return are separate controller actions. Never send
        // step two/three until the client has the expected carried stack.
        if (action.phase == 0) {
            if (minecraft.thePlayer.inventory.getItemStack() != null
                    || !InventoryLayout.matches(action.type, container.getSlot(action.slot).getStack())) {
                pendingAction = null;
                return false;
            }
            if (!clicks.click(container, action.slot, 0, 0, interval)) return false;
            action.phase = 1;
            cursorSlot = action.slot;
        } else if (action.phase == 1) {
            if (minecraft.thePlayer.inventory.getItemStack() == null || action.target < 0
                    || action.target >= container.inventorySlots.size()) {
                pendingAction = null;
                return false;
            }
            if (!clicks.click(container, action.target, 0, 0, interval)) return false;
            action.phase = 2;
            cursorSlot = action.target;
        } else {
            if (minecraft.thePlayer.inventory.getItemStack() == null) {
                pendingAction = null;
                return false;
            }
            if (!clicks.click(container, action.slot, 0, 0, interval)) return false;
            cursorSlot = action.slot;
            pendingAction = null;
        }
        cursorUntil = now + 450L;
        return true;
    }

    private boolean isUnique(Type type) {
        return type == Type.SWORD || type == Type.PICKAXE || type == Type.AXE || type == Type.SHOVEL || type == Type.HOE
                || type == Type.HELMET || type == Type.CHESTPLATE || type == Type.LEGGINGS || type == Type.BOOTS;
    }

    private long pick(RangeSetting setting) {
        int min = setting.getMinInt();
        int max = setting.getMaxInt();
        return min + (max <= min ? 0 : random.nextInt(max - min + 1));
    }

    public boolean shouldDrawCursor() {
        return isEnabled() && cursorSlot >= 0 && System.currentTimeMillis() < cursorUntil;
    }

    public int getCursorSlot() { return cursorSlot; }

    private void resetRuntime() {
        startedAt = 0L;
        nextAction = 0L;
        pendingAction = null;
        cursorSlot = -1;
        cursorUntil = 0L;
        clicks.reset();
    }

    private List<Integer> playerSlots() {
        List<Integer> result = new ArrayList<Integer>();
        for (int slot = 9; slot <= 44; slot++) result.add(slot);
        return result;
    }

    public static Type classify(ItemStack stack) {
        for (Type type : Type.values()) {
            if (type != Type.BARRIER && InventoryLayout.matches(type, stack)) return type;
        }
        return null;
    }

    /** Shared filter used by Chest Stealer: leave low-value chest clutter behind. */
    public static boolean shouldKeep(ItemStack stack) {
        if (stack == null) return false;
        return classify(stack) != null || stack.getItem() instanceof ItemBlock || stack.getItem() instanceof ItemFood
                || stack.getItem() instanceof ItemSword || stack.getItem() instanceof ItemTool || stack.getItem() instanceof ItemArmor;
    }

    /**
     * Smart ItemESP follows the actual inventory-editor drop-bin policy,
     * rather than the older generic chest-loot predicate. Items assigned to
     * a drop-bin category are deliberately hidden because the manager will
     * discard them; unassigned stacks stay visible because this manager never
     * discards them merely for being unclassified.
     */
    public static boolean shouldKeepForItemEsp(ItemStack stack) {
        if (stack == null) return false;
        Vibe vibe = Vibe.getInstance();
        InventoryEditorModule editor = vibe == null || vibe.getModuleManager() == null
                ? null : vibe.getModuleManager().getModule(InventoryEditorModule.class);
        if (editor == null) return shouldKeep(stack);
        Type type = classify(stack);
        return type == null || !editor.getLayout().isDropType(type);
    }

    private static double score(ItemStack stack) {
        if (stack == null) return Double.NEGATIVE_INFINITY;
        double result = stack.stackSize * 0.01D;
        if (stack.getItem() instanceof ItemSword) {
            result += ((ItemSword) stack.getItem()).getDamageVsEntity()
                    + EnchantmentHelper.getEnchantmentLevel(Enchantment.sharpness.effectId, stack) * 1.25D;
            int fire = EnchantmentHelper.getEnchantmentLevel(Enchantment.fireAspect.effectId, stack);
            result += fire == 1 ? 0.66D : fire >= 2 ? 0.99D : 0.0D;
        } else if (stack.getItem() instanceof ItemTool) {
            double toolBase = stack.getItem() instanceof net.minecraft.item.ItemPickaxe ? 4.0D
                    : stack.getItem() instanceof net.minecraft.item.ItemAxe ? 3.0D : 1.0D;
            result += toolBase + EnchantmentHelper.getEnchantmentLevel(Enchantment.efficiency.effectId, stack) * 0.4D;
        } else if (stack.getItem() instanceof ItemArmor) {
            result += ((ItemArmor) stack.getItem()).damageReduceAmount
                    + EnchantmentHelper.getEnchantmentLevel(Enchantment.protection.effectId, stack) * 0.25D;
        } else {
            result += stack.stackSize * 0.1D;
        }
        return result - stack.getItemDamage() * 0.0001D;
    }

    private enum ActionKind { DROP, HOTBAR_SWAP, PICKUP }

    private static final class PendingAction {
        private final ActionKind kind;
        private final int slot;
        private final int target;
        private final int hotbarButton;
        private final Type type;
        private final Item expectedItem;
        private final int expectedDamage;
        private int phase;

        private PendingAction(ActionKind kind, int slot, int target, int hotbarButton, Type type, Item expectedItem, int expectedDamage) {
            this.kind = kind;
            this.slot = slot;
            this.target = target;
            this.hotbarButton = hotbarButton;
            this.type = type;
            this.expectedItem = expectedItem;
            this.expectedDamage = expectedDamage;
        }

        private static PendingAction drop(int slot, ItemStack stack) {
            return new PendingAction(ActionKind.DROP, slot, -1, -1, null, stack.getItem(), stack.getItemDamage());
        }

        private static PendingAction hotbarSwap(int source, int button, Type type) {
            return new PendingAction(ActionKind.HOTBAR_SWAP, source, -1, button, type, null, 0);
        }

        private static PendingAction pickup(int source, int target, Type type) {
            return new PendingAction(ActionKind.PICKUP, source, target, -1, type, null, 0);
        }
    }
}

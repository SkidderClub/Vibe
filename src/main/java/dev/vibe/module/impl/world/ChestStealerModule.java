package dev.vibe.module.impl.world;

import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.setting.BooleanSetting;
import dev.vibe.setting.ModeSetting;
import dev.vibe.setting.RangeSetting;
import dev.vibe.inventory.ContainerClickDispatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Keyboard;

/** Delayed chest looter which retains only items Inventory Manager considers useful. */
public final class ChestStealerModule extends Module {

    private final ModeSetting mode = addSetting(new ModeSetting("Mode", "Default", "Default", "Advanced"));
    private final RangeSetting startDelay = addSetting(new RangeSetting("Start Delay (ms)", 100.0D, 220.0D, 0.0D, 2500.0D, 10.0D));
    private final RangeSetting stealDelay = addSetting(new RangeSetting("Steal Delay (ms)", 70.0D, 125.0D, 0.0D, 1500.0D, 5.0D,
            () -> mode.is("Default")));
    private final BooleanSetting autoClose = addSetting(new BooleanSetting("Auto Close", true));
    private final BooleanSetting titleCheck = addSetting(new BooleanSetting("Title Check", true));
    private final RangeSetting closeDelay = addSetting(new RangeSetting("Close Delay (ms)", 120.0D, 260.0D, 0.0D, 2500.0D, 10.0D,
            () -> autoClose.isEnabled()));
    private final ModeSetting selection = addSetting(new ModeSetting("Selection", "Random", () -> mode.is("Default"), "Random", "Left To Right"));
    private final RangeSetting advancedSpeed = addSetting(new RangeSetting("Cursor Speed", 175.0D, 260.0D, 25.0D, 900.0D, 5.0D,
            () -> mode.is("Advanced")));
    private final ChestAura aura = new ChestAura(this);

    private final Minecraft minecraft = Minecraft.getMinecraft();
    private final Random random = new Random();
    private int windowId = -1;
    private long openedAt;
    private long nextAction;
    private long emptySince;
    private long closeAt;
    private float cursorX;
    private float cursorY;
    private float lastCursorTravel;
    private boolean cursorReady;
    private final ContainerClickDispatcher clicks = new ContainerClickDispatcher();

    public ChestStealerModule() {
        super("ChestStealer", "Loot useful items and optionally open nearby chests", Category.WORLD, Keyboard.KEY_NONE);
    }

    public void tick() {
        if(dev.vibe.module.impl.meme.RewoundPitModule.reservesActions()){reset();return;}
        ContainerChest chest = activeChest();
        if (!isEnabled() || chest == null || minecraft.thePlayer == null) {
            reset();
            return;
        }
        long now = System.currentTimeMillis();
        if (windowId != chest.windowId) {
            windowId = chest.windowId;
            openedAt = now;
            nextAction = now + pick(startDelay);
            emptySince = 0L;
            cursorReady = false;
        }
        if(now<nextAction)return;
        ItemStack carried = minecraft.thePlayer.inventory.getItemStack();
        if (carried != null) {
            // The previous take left a stack on the cursor. Put it into the
            // player inventory before shift-clicking anything else.
            int place = placeCarried(chest, carried);
            if (place < 0) return;
            long interval = mode.is("Advanced") ? advancedDelay(null) : pick(stealDelay);
            if (!clicks.click(chest, place, 0, 0, interval)) return;
            nextAction = now + interval;
            return;
        }
        List<Slot> candidates = usefulSlots(chest);
        if (candidates.isEmpty()) {
            if (emptySince == 0L) {emptySince = now;closeAt=now+pick(closeDelay);}
            if (autoClose.isEnabled() && now >= closeAt) {
                minecraft.thePlayer.closeScreen();
                reset();
            }
            return;
        }
        emptySince = 0L;
        if (now < nextAction) return;
        Slot selected = mode.is("Advanced") ? advanceCursor(candidates) : selectDefault(candidates);
        if (selected == null) return;
        long interval = mode.is("Advanced") ? advancedDelay(selected) : pick(stealDelay);
        if (!clicks.click(chest, selected.slotNumber, 0, 1, interval)) return;
        // One shift-click per dispatched vanilla input interval prevents a
        // chest transaction burst under strict click-window validation.
        nextAction = now + interval;
    }

    @Override
    protected void onDisable() {
        aura.clearPending();
        reset();
    }

    public void applyRotation() { if (isEnabled()) aura.aim(); }
    public void afterWalking() { if (isEnabled()) aura.open(); }

    <T extends dev.vibe.setting.Setting<?>> T auraSetting(T setting) { return addSetting(setting); }

    private ContainerChest activeChest() {
        if (!(minecraft.currentScreen instanceof GuiChest) || minecraft.thePlayer == null
                || !(minecraft.thePlayer.openContainer instanceof ContainerChest)) return null;
        ContainerChest chest = (ContainerChest) minecraft.thePlayer.openContainer;
        return titleCheck.isEnabled() && !hasVanillaChestTitle(chest) ? null : chest;
    }

    /** Title check deliberately accepts any title containing "chest". */
    private boolean hasVanillaChestTitle(ContainerChest chest) {
        IInventory inventory = chest.getLowerChestInventory();
        return inventory != null && inventory.getDisplayName() != null && inventory.getDisplayName().getUnformattedText()
                .toLowerCase(java.util.Locale.ROOT).contains("chest");
    }

    private List<Slot> usefulSlots(ContainerChest chest) {
        List<Slot> result = new ArrayList<Slot>();
        int chestSlots = chest.getLowerChestInventory().getSizeInventory();
        for (int index = 0; index < chestSlots && index < chest.inventorySlots.size(); index++) {
            Slot slot = chest.getSlot(index);
            if (slot != null && slot.getHasStack() && InventoryManagerModule.shouldKeep(slot.getStack())
                    && canStore(chest, slot.getStack())) result.add(slot);
        }
        return result;
    }

    /** Player-side slot that can take the carried stack without swapping another item onto the cursor. */
    private int placeCarried(ContainerChest chest, ItemStack carried) {
        int chestSlots = chest.getLowerChestInventory().getSizeInventory();
        int merge = -1;
        for (int index = chestSlots; index < chest.inventorySlots.size(); index++) {
            Slot slot = chest.getSlot(index);
            if (!dev.vibe.inventory.CursorClicks.canPlace(slot, carried)) continue;
            if (!slot.getHasStack()) return index;
            if (merge < 0) merge = index;
        }
        return merge;
    }

    private boolean canStore(ContainerChest chest, ItemStack stack) {
        return placeCarried(chest, stack) >= 0;
    }

    private Slot selectDefault(List<Slot> candidates) {
        return selection.is("Random") ? candidates.get(random.nextInt(candidates.size())) : candidates.get(0);
    }

    private Slot advanceCursor(List<Slot> candidates) {
        if (!cursorReady) {
            cursorX = 88.0F;
            cursorY = 88.0F;
            cursorReady = true;
        }
        Slot nearest = null;
        float closest = Float.MAX_VALUE;
        for (Slot slot : candidates) {
            float dx = slot.xDisplayPosition + 8 - cursorX;
            float dy = slot.yDisplayPosition + 8 - cursorY;
            float distance = dx * dx + dy * dy;
            if (distance < closest) { closest = distance; nearest = slot; }
        }
        if (nearest != null) {
            lastCursorTravel = (float) Math.sqrt(closest);
            cursorX = nearest.xDisplayPosition + 8;
            cursorY = nearest.yDisplayPosition + 8;
        }
        return nearest;
    }

    private long advancedDelay(Slot target) {
        double speed = (advancedSpeed.getMin() + advancedSpeed.getMax()) * 0.5D;
        return Math.max(18L, Math.round(1000.0D * Math.max(4.0F, lastCursorTravel) / speed));
    }

    public boolean shouldDrawCursor() { return isEnabled() && mode.is("Advanced") && windowId >= 0 && cursorReady; }
    public float getCursorX() { return cursorX; }
    public float getCursorY() { return cursorY; }

    private long pick(RangeSetting setting) {
        int minimum = setting.getMinInt();
        int maximum = setting.getMaxInt();
        return minimum + (maximum <= minimum ? 0 : random.nextInt(maximum - minimum + 1));
    }

    private void reset() {
        windowId = -1;
        openedAt = 0L;
        nextAction = 0L;
        emptySince = 0L;
        cursorReady = false;
        lastCursorTravel = 0.0F;
        clicks.reset();
    }
}

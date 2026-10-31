package dev.vibe.module.impl.combat;

import dev.vibe.Vibe;
import dev.vibe.input.VanillaClicks;
import dev.vibe.inventory.*;
import dev.vibe.inventory.InventoryLayout.Type;
import dev.vibe.module.Category;
import dev.vibe.module.impl.client.InventoryEditorModule;
import dev.vibe.module.impl.movement.MoveFixModule;
import dev.vibe.module.impl.world.*;
import dev.vibe.setting.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.item.*;
import net.minecraft.potion.*;
import org.lwjgl.input.Keyboard;

/** Delayed item selection and native right-button use; inventory transfers use the shared controller dispatcher. */
public final class AutoBuffModule extends dev.vibe.module.Module {
    public enum Kind {
        POT("Auto Pot", Type.HEALING_POTION, 14), SOUP("Auto Soup", Type.SOUP, 14),
        HEAD("Auto Head", Type.HEAD, 12), EAT("Auto Eat", Type.FOOD, 20), GAPPLE("Auto Gapple", Type.GOLDEN_APPLE, 14);
        public final String label; public final Type type; final int health;
        Kind(String label, Type type, int health) { this.label = label; this.type = type; this.health = health; }
    }
    public final MultiSelectSetting modes = addSetting(new MultiSelectSetting("Modes",
            Arrays.asList("Auto Pot","Auto Soup","Auto Head","Auto Eat","Auto Gapple"),
            Arrays.asList("Auto Pot","Auto Soup","Auto Head","Auto Eat","Auto Gapple")));
    public final Map<Kind,NumberSetting> health = new EnumMap<Kind,NumberSetting>(Kind.class);
    public final Map<Kind,RangeSetting> delays = new EnumMap<Kind,RangeSetting>(Kind.class);
    public final NumberSetting hunger = addSetting(new NumberSetting("Eat Below Hunger", 16, 1, 20, 1, () -> modes.isSelectedIgnoreCase("Auto Eat")));
    public final BooleanSetting eatWalkBackwards = addSetting(new BooleanSetting("Auto Eat Walk Backwards", false, () -> enabled(Kind.EAT)));
    public final BooleanSetting gappleWalkBackwards = addSetting(new BooleanSetting("Auto Gapple Walk Backwards", false, () -> enabled(Kind.GAPPLE)));
    public final BooleanSetting absorption = addSetting(new BooleanSetting("Count Absorption", false));
    public final BooleanSetting groundPots = addSetting(new BooleanSetting("Grounded Splash Potions", true, () -> modes.isSelectedIgnoreCase("Auto Pot")));
    public final BooleanSetting refill = addSetting(new BooleanSetting("Auto Refill", true));
    public final BooleanSetting refillMoving = addSetting(new BooleanSetting("Refill While Moving", false, () -> refill.isEnabled()));
    public final ModeSetting refillMode = addSetting(new ModeSetting("Refill Mode", "Open Inventory", () -> refill.isEnabled(), "Open Inventory", "Spoof", "Inventory Only"));
    public final RangeSetting refillDelay = addSetting(new RangeSetting("Refill Delay (ms)", 160, 240, 0, 3000, 10, () -> refill.isEnabled()));
    public final RangeSetting openDelay = addSetting(new RangeSetting("Inventory Open Delay (ms)", 180, 280, 0, 3000, 10, () -> refill.isEnabled()));
    public final RangeSetting switchDelay = addSetting(new RangeSetting("Slot Switch Delay (ms)", 100, 160, 0, 2000, 10));
    public final RangeSetting restoreDelay = addSetting(new RangeSetting("Slot Restore Delay (ms)", 100, 160, 0, 2000, 10));
    public final NumberSetting timeout = addSetting(new NumberSetting("Use Timeout (ms)", 4000, 1000, 15000, 100));
    private final Minecraft mc = Minecraft.getMinecraft();
    private final Random random = new Random();
    private final ContainerClickDispatcher clicks = new ContainerClickDispatcher();
    private final PlayerInventorySession inventory = new PlayerInventorySession(clicks);
    private enum Phase { IDLE, SELECTED, USING, RESTORE }
    private Phase phase = Phase.IDLE;
    private Kind active, candidate;
    private Object player, world;
    private int slot = -1, previousSlot = -1, selectedTick;
    private ItemStack expected;
    private long due, candidateDue, cooldown, startedUse, refillAt;
    private final Map<Kind,Long> blockedUntil = new EnumMap<Kind,Long>(Kind.class);
    private boolean pressed, refilling;
    private static final Kind[] PRIORITY = {Kind.SOUP, Kind.POT, Kind.HEAD, Kind.GAPPLE, Kind.EAT};

    public AutoBuffModule() {
        super("AutoBuff", "Use healing items and food with delayed hotbar selection and layout-aware refill", Category.COMBAT, Keyboard.KEY_NONE);
        for (Kind kind : Kind.values()) {
            health.put(kind, addSetting(new NumberSetting(kind.label + " Health", kind.health, 1, 20, .5, () -> enabled(kind))));
            delays.put(kind, addSetting(new RangeSetting(kind.label + " Delay (ms)", 250, 450, 0, 5000, 10, () -> enabled(kind))));
        }
    }
    public boolean enabled(Kind kind) { return modes.isSelectedIgnoreCase(kind.label); }
    public void tick() { tick(System.currentTimeMillis()); }
    public void tick(long now) {
        if (!isEnabled() || mc.thePlayer == null || mc.theWorld == null || mc.playerController == null
                || !mc.thePlayer.isEntityAlive() || mc.thePlayer.isSpectator()) { cancel(false); return; }
        if (player != mc.thePlayer || world != mc.theWorld) { cancel(false); player = mc.thePlayer; world = mc.theWorld; }
        if (PlayerInventorySession.isBusy() && !inventory.valid()) { cancel(true); return; }
        if (refilling) {
            if (!refill.isEnabled() || !inventory.valid()) { inventory.close(); refilling = false; return; }
            if (mc.thePlayer.inventory.getItemStack() != null) return;
            if (!refillMoving.isEnabled() && moving()) { inventory.close(); refilling = false; return; }
            if (now < refillAt) return;
            int[] transfer = refillPlan(layout());
            if (transfer == null) {
                inventory.close(); refilling = false; cooldown = now + 150;
            } else {
                long interval = Math.max(125, pick(refillDelay));
                if (clicks.click(mc.thePlayer.inventoryContainer, transfer[0], transfer[1], 2, interval)) refillAt = now + interval;
            }
            return;
        }
        if (phase != Phase.IDLE) {
            if (ScaffoldModule.isActive() || BedAuraModule.ownsMiningInput()
                    || dev.vibe.module.impl.movement.FreecamModule.cameraActiveHook()) { cancel(true); return; }
            if (mc.currentScreen != null || VanillaClicks.physicallyDown(mc.gameSettings.keyBindUseItem)
                    || mc.thePlayer.inventory.currentItem != slot) { cancel(false); return; }
            if (phase == Phase.RESTORE) {
                releaseInput();
                if (now >= due) {
                    if (previousSlot >= 0) mc.thePlayer.inventory.currentItem = previousSlot;
                    clearRotation(); phase = Phase.IDLE; slot = previousSlot = -1; active = null;
                }
                return;
            }
            if (changed(mc.thePlayer.getHeldItem(), expected) || !enabled(active)) { finish(now); return; }
            if (phase == Phase.USING && pressed && !mc.thePlayer.isUsingItem()
                    && !shouldUse(active, mc.thePlayer.getHeldItem())) { finish(now); return; }
            if (splash(expected)) {
                MoveFixModule fix = Vibe.getInstance().getModuleManager().getModule(MoveFixModule.class);
                if (fix == null) { cancel(true); return; }
                fix.setFakeRotation(getId(), mc.thePlayer.rotationYaw, 90);
            }
            if (phase == Phase.SELECTED && now >= due && mc.thePlayer.ticksExisted - selectedTick >= 2) {
                phase = Phase.USING; startedUse = now;
            } else if (phase == Phase.USING && now - startedUse >= timeout.getInt()) {
                blockedUntil.put(active, now + 5000); finish(now);
            }
            return;
        }
        if (now < cooldown || ScaffoldModule.isActive() || BedAuraModule.ownsMiningInput()
                || dev.vibe.module.impl.movement.FreecamModule.cameraActiveHook()
                || mc.thePlayer.isUsingItem() || VanillaClicks.physicallyDown(mc.gameSettings.keyBindUseItem)
                || mc.thePlayer.inventory.getItemStack() != null) { candidate = null; return; }
        if (mc.currentScreen == null) {
            for (Kind kind : PRIORITY) {
                if (!enabled(kind) || now < blockedUntil.getOrDefault(kind, 0L)) continue;
                int found = findHotbar(kind);
                if (found < 0 || !shouldUse(kind, mc.thePlayer.inventory.getStackInSlot(found))) continue;
                if (candidate != kind) { candidate = kind; candidateDue = now + pick(delays.get(kind)); }
                if (now >= candidateDue) {
                    active = kind; slot = found; previousSlot = mc.thePlayer.inventory.currentItem;
                    expected = mc.thePlayer.inventory.getStackInSlot(found).copy();
                    mc.thePlayer.inventory.currentItem = found; selectedTick = mc.thePlayer.ticksExisted;
                    phase = Phase.SELECTED; due = now + pick(switchDelay); pressed = false; candidate = null;
                }
                return;
            }
        }
        candidate = null;
        if (refill.isEnabled() && (refillMoving.isEnabled() || !moving()) && refillPlan(layout()) != null && inventory.open(refillMode.getValue())) {
            refilling = true; refillAt = now + pick(openDelay);
        }
    }
    public boolean shouldUse(Kind kind, ItemStack stack) {
        if (!matches(kind, stack) || mc.thePlayer == null) return false;
        float hp = mc.thePlayer.getHealth() + (absorption.isEnabled() ? mc.thePlayer.getAbsorptionAmount() : 0);
        if (hp > health.get(kind).getDouble()) return false;
        if (kind == Kind.EAT) return mc.thePlayer.getFoodStats().getFoodLevel() < hunger.getInt();
        if (kind == Kind.POT) {
            if (splash(stack) && groundPots.isEnabled() && !mc.thePlayer.onGround) return false;
            for (PotionEffect effect : Consumables.effects(stack)) {
                if (effect.getPotionID() == Potion.heal.id) { if (mc.thePlayer.getHealth() < mc.thePlayer.getMaxHealth()) return true; }
                else {
                    PotionEffect current = mc.thePlayer.getActivePotionEffect(Potion.potionTypes[effect.getPotionID()]);
                    if (current == null || current.getAmplifier() < effect.getAmplifier() || current.getDuration() < 40) return true;
                }
            }
            return false;
        }
        if (kind == Kind.GAPPLE && mc.thePlayer.isPotionActive(Potion.regeneration)) return false;
        return mc.thePlayer.getHealth() < mc.thePlayer.getMaxHealth();
    }
    public static boolean matches(Kind kind, ItemStack s) {
        if (s == null || s.stackSize <= 0) return false;
        switch (kind) {
            case POT: return Consumables.beneficialPotion(s);
            case SOUP: return Consumables.soup(s);
            case HEAD: return Consumables.head(s);
            case EAT: return Consumables.food(s);
            case GAPPLE: return Consumables.gapple(s);
            default: return false;
        }
    }
    private int findHotbar(Kind kind) {
        InventoryLayout layout = layout();
        for (int i = 0; i < 9; i++) if (layout.get(i) != Type.IGNORE && layout.get(i) != Type.BARRIER && matches(kind, mc.thePlayer.inventory.getStackInSlot(i))) return i;
        return -1;
    }
    /** Source is always a live main-inventory slot; destinations honor repeated editor assignments. */
    public int[] refillPlan(InventoryLayout layout) {
        if (mc.thePlayer == null) return null;
        for (Kind kind : PRIORITY) {
            if (!enabled(kind) || layout.isDropType(kind.type)) continue;
            boolean assigned = false;
            for (int i = 0; i < 9; i++) {
                Type role = layout.get(i);
                if (role != kind.type && !(kind == Kind.POT && role == Type.POTION)) continue;
                assigned = true;
                if (!InventoryLayout.matches(role, mc.thePlayer.inventory.getStackInSlot(i))) {
                    int source = refillSource(kind, role, layout);
                    if (source >= 0) return new int[]{source, i};
                }
            }
            int source = refillSource(kind, null, layout);
            if (source >= 0 && !assigned && findHotbar(kind) < 0) for (int i = 8; i >= 0; i--) {
                ItemStack existing = mc.thePlayer.inventory.getStackInSlot(i);
                if (layout.get(i) == null && (existing == null || existing.getItem() == net.minecraft.init.Items.bowl || existing.getItem() == net.minecraft.init.Items.glass_bottle))
                    return new int[]{source, i};
            }
        }
        return null;
    }
    private int refillSource(Kind kind, Type role, InventoryLayout layout) {
        for (int i = 9; i < 36; i++) {
            ItemStack s = mc.thePlayer.inventory.getStackInSlot(i);
            if (layout.get(i) != Type.IGNORE && matches(kind, s) && (role == null || InventoryLayout.matches(role, s))) return i;
        }
        return -1;
    }
    private InventoryLayout layout() {
        InventoryEditorModule editor = Vibe.getInstance().getModuleManager().getModule(InventoryEditorModule.class);
        return editor == null ? new InventoryLayout() : editor.getLayout();
    }
    private boolean moving() {
        return !mc.thePlayer.onGround || Math.hypot(mc.thePlayer.motionX, mc.thePlayer.motionZ) > .03
                || mc.thePlayer.moveForward != 0 || mc.thePlayer.moveStrafing != 0;
    }
    private static boolean splash(ItemStack s) { return s != null && s.getItem() instanceof ItemPotion && ItemPotion.isSplash(s.getMetadata()); }
    private static boolean changed(ItemStack current, ItemStack before) {
        return current == null || before == null || current.getItem() != before.getItem() || current.getMetadata() != before.getMetadata() || current.stackSize < before.stackSize;
    }
    private void finish(long now) {
        releaseInput(); clearRotation(); cooldown = now + pick(delays.get(active));
        phase = Phase.RESTORE; due = now + pick(restoreDelay);
    }
    private void releaseInput() {
        if (mc.gameSettings != null && (phase != Phase.IDLE || pressed)) {
            if (!VanillaClicks.physicallyDown(mc.gameSettings.keyBindUseItem)) VanillaClicks.discardPresses(mc.gameSettings.keyBindUseItem);
            VanillaClicks.restore(mc.gameSettings.keyBindUseItem); VanillaClicks.restore(mc.gameSettings.keyBindAttack);
        }
        pressed = false;
    }
    private void clearRotation() {
        if (Vibe.getInstance() == null) return;
        MoveFixModule fix = Vibe.getInstance().getModuleManager().getModule(MoveFixModule.class);
        if (fix != null) fix.discardOwnedState(getId());
    }
    private void cancel(boolean restoreSlot) {
        inventory.close(); refilling = false; releaseInput(); clearRotation();
        if (restoreSlot && mc.thePlayer != null && player == mc.thePlayer && mc.thePlayer.inventory.currentItem == slot && previousSlot >= 0)
            mc.thePlayer.inventory.currentItem = previousSlot;
        phase = Phase.IDLE; active = candidate = null; expected = null; slot = previousSlot = -1; player = world = null;
    }
    @Override protected void onDisable() { cancel(true); }
    private long pick(RangeSetting s) { return s.getMinInt() + random.nextInt(s.getMaxInt() - s.getMinInt() + 1); }
    private static AutoBuffModule module() { return Vibe.getInstance() == null ? null : Vibe.getInstance().getModuleManager().getModule(AutoBuffModule.class); }
    public static boolean ownsUseInput() { AutoBuffModule m = module(); return m != null && m.isEnabled() && m.phase != Phase.IDLE; }
    /** Recomputed with vanilla movement each tick, before MoveFix corrects the camera-relative input. */
    public static void inputHook(net.minecraft.util.MovementInput input) {
        AutoBuffModule m = module();
        if (m == null || !m.isEnabled() || m.phase != Phase.USING || !m.pressed || !m.enabled(m.active)
                || m.mc.thePlayer == null || m.player != m.mc.thePlayer || m.world != m.mc.theWorld
                || m.mc.currentScreen != null || !m.mc.thePlayer.isEntityAlive() || m.mc.thePlayer.isSpectator()
                || !m.mc.thePlayer.isUsingItem() || m.mc.thePlayer.inventory.currentItem != m.slot
                || changed(m.mc.thePlayer.getHeldItem(), m.expected)
                || VanillaClicks.physicallyDown(m.mc.gameSettings.keyBindUseItem)
                || dev.vibe.module.impl.movement.FreecamModule.cameraActiveHook()
                || ScaffoldModule.isActive() || BedAuraModule.ownsMiningInput()) return;
        if (m.active == Kind.EAT && m.eatWalkBackwards.isEnabled()
                || m.active == Kind.GAPPLE && m.gappleWalkBackwards.isEnabled()) {
            input.moveForward = input.sneak ? -0.3F : -1.0F;
            input.moveStrafe = 0.0F;
        }
    }
    /** Called on the native input pass, so vanilla owns held-slot sync and use-item packets. */
    public static void prepareInputHook() {
        AutoBuffModule m = module();
        if (m == null || !ownsUseInput() || m.mc.currentScreen != null) return;
        if (dev.vibe.module.impl.movement.FreecamModule.cameraActiveHook() || ScaffoldModule.isActive() || BedAuraModule.ownsMiningInput()) { m.cancel(true); return; }
        if (splash(m.expected) && m.phase != Phase.RESTORE) {
            MoveFixModule fix = Vibe.getInstance().getModuleManager().getModule(MoveFixModule.class);
            if (fix != null) fix.setFakeRotation(m.getId(), m.mc.thePlayer.rotationYaw, 90);
        }
        VanillaClicks.discardPresses(m.mc.gameSettings.keyBindAttack);
        net.minecraft.client.settings.KeyBinding.setKeyBindState(m.mc.gameSettings.keyBindAttack.getKeyCode(), false);
        VanillaClicks.discardPresses(m.mc.gameSettings.keyBindUseItem);
        if (m.phase != Phase.USING) {
            net.minecraft.client.settings.KeyBinding.setKeyBindState(m.mc.gameSettings.keyBindUseItem.getKeyCode(), false);
            return;
        }
        if (!m.pressed) {
            VanillaClicks.pulseUse(m.mc.gameSettings.keyBindUseItem); m.pressed = true;
        } else net.minecraft.client.settings.KeyBinding.setKeyBindState(m.mc.gameSettings.keyBindUseItem.getKeyCode(),
                m.expected.getMaxItemUseDuration() > 0 && m.mc.thePlayer.isUsingItem());
    }
}

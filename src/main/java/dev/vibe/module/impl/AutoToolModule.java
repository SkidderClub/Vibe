package dev.vibe.module.impl;

import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.setting.BooleanSetting;
import dev.vibe.setting.ColorSetting;
import dev.vibe.setting.ModeSetting;
import dev.vibe.setting.MultiSelectSetting;
import dev.vibe.setting.RangeSetting;
import java.util.Arrays;
import java.util.Random;
import dev.vibe.Vibe;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemTool;
import net.minecraft.util.MovingObjectPosition;
import org.lwjgl.input.Keyboard;

/**
 * Chooses the strongest matching hotbar stack for a genuine block break or
 * entity attack. The Basic mode changes the normal selected slot. Silent mode
 * retains the rendered slot and changes only the server-held hotbar slot for
 * the short action window requested by the player.
 */
public final class AutoToolModule extends Module {

    private final MultiSelectSetting types = addSetting(new MultiSelectSetting("Types",
            Arrays.asList("Weapon", "Tools"), Arrays.asList("Weapon", "Tools")));
    private final ModeSetting mode = addSetting(new ModeSetting("Mode", "Basic", "Basic", "Silent"));
    private final RangeSetting startDelay = addSetting(new RangeSetting("Start Delay (ms)", 40.0D, 95.0D,
            0.0D, 1000.0D, 5.0D));
    private final BooleanSetting switchBack = addSetting(new BooleanSetting("Switch Back", true,
            () -> mode.is("Basic")));
    private final RangeSetting switchBackDelay = addSetting(new RangeSetting("Switch Back Delay (ms)", 80.0D, 160.0D,
            0.0D, 2000.0D, 5.0D, () -> mode.is("Silent") || switchBack.isEnabled()));
    private final ColorSetting silentColor = addSetting(new ColorSetting("Silent Slot Color", 0xFF2DE2C2,
            () -> mode.is("Silent")));

    private final Minecraft minecraft = Minecraft.getMinecraft();
    private final Random random = new Random();
    private long wantedSince;
    private long lastAction;
    private int originalSlot = -1;
    private int spoofedSlot = -1;
    private int wantedSlot = -1;
    private long waitStart, waitBack;
    private net.minecraft.entity.player.EntityPlayer owner;

    public AutoToolModule() {
        super("AutoTool", "Select the best hotbar tool for the current vanilla action", Category.WORLD, Keyboard.KEY_NONE);
    }

    /** Runs at tick start before Minecraft consumes a held attack input. */
    public void tick() {
        if (owner != minecraft.thePlayer) {
            originalSlot = spoofedSlot = wantedSlot = -1;
            wantedSince = 0;
            owner = minecraft.thePlayer;
        }
        if (spoofedSlot >= 0 && (!mode.is("Silent") || minecraft.thePlayer == null
                || minecraft.thePlayer.inventory.currentItem != originalSlot)) reset(true);
        if (!isEnabled() || minecraft.thePlayer == null || minecraft.theWorld == null || minecraft.currentScreen != null) {
            reset(true);
            return;
        }
        int desired = desiredSlot();
        long now = System.currentTimeMillis();
        if (desired < 0 || desired == minecraft.thePlayer.inventory.currentItem && spoofedSlot < 0) {
            wantedSince = 0L;
            // A Basic switch must stay selected for the complete vanilla
            // destroy sequence. The previous timer restored it while the
            // block controller was still damaging the same target.
            // getIsHittingBlock is false for the tiny hand-off between two
            // adjacent blocks. Keep the Basic selection while attack remains
            // held over *any* block, otherwise it switched back just before
            // vanilla began damaging the next block.
            if (originalSlot >= 0 && !isBreakingBlock() && !isAttemptingBlockBreak()
                    && now - lastAction >= waitBack) {
                reset(true);
            }

            return;
        }
        if (desired == spoofedSlot) {
            lastAction = now;
            return;
        }
        if (wantedSince == 0L || wantedSlot != desired) {
            wantedSlot = desired;
            wantedSince = now;
            waitStart = pick(startDelay);
        }
        if (now - wantedSince < waitStart) {
            return;
        }
        select(desired);
        wantedSince = 0L;

    }

    private int desiredSlot() {
        MovingObjectPosition hit = minecraft.objectMouseOver;
        if (hit == null || !minecraft.gameSettings.keyBindAttack.isKeyDown()) {
            return -1;
        }
        if (hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK && types.isSelected("Tools")) {
            Block block = minecraft.theWorld.getBlockState(hit.getBlockPos()).getBlock();
            return bestTool(block);
        }
        // Do not choose a combat item while the input is actively breaking a
        // block. This lets vanilla retain its start/continue/destroy sequence.
        if (hit.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY && types.isSelected("Weapon")) {
            return bestWeapon();
        }
        return -1;
    }

    private int bestTool(Block block) {
        int visible = minecraft.thePlayer.inventory.currentItem;
        int best = visible;
        net.minecraft.util.BlockPos pos = minecraft.objectMouseOver.getBlockPos();
        float strength = block.getPlayerRelativeBlockHardness(minecraft.thePlayer, minecraft.theWorld, pos);
        try {
            for (int slot = 0; slot < 9; slot++) {
                ItemStack stack = minecraft.thePlayer.inventory.mainInventory[slot];
                if (stack == null || stack.stackSize <= 0) continue;
                minecraft.thePlayer.inventory.currentItem = slot;
                float candidate = block.getPlayerRelativeBlockHardness(minecraft.thePlayer, minecraft.theWorld, pos);
                if (candidate > strength + .000001F) { strength = candidate; best = slot; }
            }
        } finally { minecraft.thePlayer.inventory.currentItem = visible; }
        return best;
    }

    private int bestWeapon() {
        int best = -1;
        float damage = -1.0F;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = minecraft.thePlayer.inventory.mainInventory[slot];
            if (stack == null) continue;
            float candidate = 0.0F;
            if (stack.getItem() instanceof ItemSword) {
                candidate = ((ItemSword) stack.getItem()).getDamageVsEntity();
            } else if (stack.getItem() instanceof ItemTool) {
                // 1.8's ItemTool does not expose its attack modifier through
                // a public accessor. Treat tools as a baseline fallback;
                // swords retain their exact public vanilla damage value.
                candidate = 1.0F;
            }
            if (candidate > damage) {
                damage = candidate;
                best = slot;
            }
        }
        return best;
    }

    private void select(int slot) {
        if (slot < 0 || slot > 8 || minecraft.thePlayer == null) return;
        if (originalSlot < 0) originalSlot = minecraft.thePlayer.inventory.currentItem;
        lastAction = System.currentTimeMillis();
        waitBack = pick(switchBackDelay);
        if (mode.is("Basic")) {
            minecraft.thePlayer.inventory.currentItem = slot;
            minecraft.playerController.updateController();
            spoofedSlot = -1;
            return;
        }
        // Keep the client-selected slot unchanged. The C09 packet is the
        // server-side selection used by block interaction packets, while the
        // hotbar renderer continues to show the player's real selection.
        if (spoofedSlot != slot) {
            spoofedSlot = slot;
            minecraft.playerController.updateController();
        }
    }

    private void reset(boolean restore) {
        wantedSince = 0L;
        if (!restore || minecraft.thePlayer == null || originalSlot < 0) {
            if (restore) {
                originalSlot = -1;
                spoofedSlot = -1;
            }
            return;
        }
        if (spoofedSlot >= 0) {
            spoofedSlot = -1;
            minecraft.playerController.updateController();
        } else if (mode.is("Basic") && switchBack.isEnabled()) {
            minecraft.thePlayer.inventory.currentItem = originalSlot;
            minecraft.playerController.updateController();
        }
        originalSlot = -1;
        spoofedSlot = -1;
        lastAction = 0L;
    }

    private boolean isBreakingBlock() {
        try { return minecraft.playerController != null && minecraft.playerController.getIsHittingBlock(); }
        catch (Throwable ignored) { return false; }
    }

    private boolean isAttemptingBlockBreak() {
        MovingObjectPosition hit = minecraft.objectMouseOver;
        return minecraft.gameSettings != null && minecraft.gameSettings.keyBindAttack.isKeyDown()
                && hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK;
    }

    @Override
    protected void onDisable() {
        reset(true);
    }

    private long pick(RangeSetting setting) {
        int min = setting.getMinInt();
        int max = setting.getMaxInt();
        return min + (max <= min ? 0 : random.nextInt(max - min + 1));
    }

    public boolean hasSilentSlot() { return isEnabled() && mode.is("Silent") && spoofedSlot >= 0; }
    private static AutoToolModule active() {
        Vibe vibe = Vibe.getInstance();
        return vibe == null || vibe.getModuleManager() == null ? null : vibe.getModuleManager().getModule(AutoToolModule.class);
    }
    public static int serverSlotHook(int vanilla) {
        AutoToolModule module = active();
        return module != null && module.hasSilentSlot() ? module.spoofedSlot : vanilla;
    }
    public static int beginActionHook() {
        AutoToolModule module = active();
        if (module == null || !module.hasSilentSlot() || module.minecraft.thePlayer == null) return -1;
        int visible = module.minecraft.thePlayer.inventory.currentItem;
        module.minecraft.thePlayer.inventory.currentItem = module.spoofedSlot;
        return visible;
    }
    public static void endActionHook(int visible) {
        Minecraft mc = Minecraft.getMinecraft();
        if (visible >= 0 && mc.thePlayer != null) mc.thePlayer.inventory.currentItem = visible;
    }
    public int getSpoofedSlot() { return spoofedSlot; }
    public ColorSetting getSilentColor() { return silentColor; }
}

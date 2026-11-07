package dev.vibe.module.impl.world;

import dev.vibe.module.Category;
import dev.vibe.inventory.*;
import dev.vibe.module.Module;
import dev.vibe.module.impl.combat.PickenSwitchModule;
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
import net.minecraft.entity.player.InventoryPlayer;
import java.util.function.Predicate;
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
    public final ItemSwitching switching = new ItemSwitching(this::addSetting, "Basic");
    private final ModeSetting mode = switching.mode;
    private HotbarSwitches.Lease lease;
    public final dev.vibe.setting.NumberSetting durabilityReserve=addSetting(new dev.vibe.setting.NumberSetting("Durability Reserve",0,0,500,1));
    private final RangeSetting startDelay = addSetting(new RangeSetting("Start Delay (ms)", 40.0D, 95.0D,
            0.0D, 1000.0D, 5.0D));
    private final BooleanSetting switchBack = addSetting(new BooleanSetting("Switch Back", true,
            () -> mode.is("Basic")));
    private final RangeSetting switchBackDelay = addSetting(new RangeSetting("Switch Back Delay (ms)", 80.0D, 160.0D,
            0.0D, 2000.0D, 5.0D, () -> mode.is("Silent") || switchBack.isEnabled()));
    private final ColorSetting silentColor = switching.color;

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
        if (HotbarSwitches.busy(this, HotbarSwitches.PICKEN)) return;
        if(lease!=null&&!lease.valid()){lease=null;originalSlot=spoofedSlot=wantedSlot=-1;}
        if(PickenSwitchModule.ownsSlot())return;
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
        boolean safeCurrent = minecraft.thePlayer.getHeldItem()==null||usable(minecraft.thePlayer.getHeldItem());
        int best = safeCurrent?visible:-1;
        net.minecraft.util.BlockPos pos = minecraft.objectMouseOver.getBlockPos();
        float strength = safeCurrent?block.getPlayerRelativeBlockHardness(minecraft.thePlayer, minecraft.theWorld, pos):-1;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = minecraft.thePlayer.inventory.mainInventory[slot];
            if (stack == null || stack.stackSize <= 0 || !usable(stack)) continue;
            final int candidateSlot = slot;
            float candidate = HotbarSwitches.evaluate(candidateSlot,
                    () -> block.getPlayerRelativeBlockHardness(minecraft.thePlayer, minecraft.theWorld, pos));
            if (candidate > strength + .000001F) { strength = candidate; best = slot; }
        }
        return best;
    }

    private int bestWeapon() {
        return bestHotbarWeaponSlot(minecraft.thePlayer.inventory, this::usable);
    }
    public boolean usable(ItemStack stack){return stack!=null&&(durabilityReserve.getInt()==0||!stack.isItemStackDamageable()||stack.getMaxDamage()-stack.getItemDamage()>durabilityReserve.getInt());}
    public static boolean hasHotbarAction(){return HotbarSwitches.hasAction();}

    /** Shared weapon scoring for AutoTool and immediate combat actions. */
    public static int bestHotbarWeaponSlot(InventoryPlayer inventory, Predicate<ItemStack> accepted) {
        int best = -1;
        float damage = -1.0F;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = inventory.mainInventory[slot];
            if (stack == null || stack.stackSize <= 0 || !accepted.test(stack)) continue;
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
        lease=HotbarSwitches.acquire(this,switching,slot,HotbarSwitches.TOOL);
        spoofedSlot=mode.is("Silent")?slot:-1;
    }

    private void reset(boolean restore) {
        wantedSince=0L;
        if(lease!=null){lease.close(true,restore&&(mode.is("Silent")||switchBack.isEnabled()));lease=null;}
        originalSlot=spoofedSlot=wantedSlot=-1;lastAction=0L;
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

    public boolean hasSilentSlot() { HotbarSwitches.Lease active=HotbarSwitches.owned(this);return active!=null&&active.active()&&active.silent(); }
    public static int serverSlotHook(int vanilla) { return HotbarSwitches.serverSlotHook(vanilla); }
    public static int beginActionHook() { return HotbarSwitches.beginActionHook(); }
    public static void endActionHook(int token) { HotbarSwitches.endActionHook(token); }
    /** Compatibility facade; ownership and synchronization live in HotbarSwitches. */
    public static HotbarAction beginHotbarAction(int slot) {
        ItemSwitching options=new ItemSwitching(ignored->{},"Basic");
        HotbarSwitches.Lease action=HotbarSwitches.acquire(new Object(),options,slot,HotbarSwitches.PROJECTILE);
        if(action==null)return null;
        if(!action.active()){action.close(false,true);return null;}
        return new HotbarAction(action);
    }
    public static final class HotbarAction implements AutoCloseable {
        private final HotbarSwitches.Lease action;
        private HotbarAction(HotbarSwitches.Lease action){this.action=action;}
        public void reselect(){action.reselect();}
        @Override public void close(){action.close();}
        public void closePreservingSelection(){action.closePreservingSelection();}
    }
    public int getSpoofedSlot() { HotbarSwitches.Lease active=HotbarSwitches.owned(this);return active==null?-1:active.slot(); }
    public ColorSetting getSilentColor() { return silentColor; }
}

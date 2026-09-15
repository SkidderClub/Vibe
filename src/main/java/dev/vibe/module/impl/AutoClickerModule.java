package dev.vibe.module.impl;

import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.setting.BooleanSetting;
import dev.vibe.setting.ModeSetting;
import dev.vibe.setting.MultiSelectSetting;
import dev.vibe.setting.NumberSetting;
import dev.vibe.setting.RangeSetting;
import java.util.Arrays;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemTool;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.entity.Entity;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/** Left-button click scheduler that gates Minecraft's native held-attack path. */
public final class AutoClickerModule extends Module {

    private final RangeSetting cps = addSetting(new RangeSetting("CPS Range", 6.0D, 13.0D, 1.0D, 20.0D, 0.1D));
    private final BooleanSetting requireHold = addSetting(new BooleanSetting("Hold To Click", true));
    private final BooleanSetting triggerMode = addSetting(new BooleanSetting("Trigger Mode", false));
    private final BooleanSetting limitItems = addSetting(new BooleanSetting("Limit Items", false));
    private final MultiSelectSetting itemWhitelist = addSetting(new MultiSelectSetting("Item Whitelist",
            Arrays.asList("Swords", "Axes", "Pickaxes", "Shovels", "Hoes"), Arrays.asList("Swords"),
            () -> limitItems.isEnabled()));
    private final BooleanSetting breakBlocks = addSetting(new BooleanSetting("Break Blocks", false));
    private final RangeSetting breakBlocksDelay = addSetting(new RangeSetting("Break Blocks Delay", 0.0D, 10.0D,
            0.0D, 2000.0D, 1.0D, () -> breakBlocks.isEnabled()));
    private final BooleanSetting breakBlocksWhitelist = addSetting(new BooleanSetting("Break Blocks Item Filter", false,
            () -> breakBlocks.isEnabled()));
    private final MultiSelectSetting blockBreakItems = addSetting(new MultiSelectSetting("Break Blocks Items",
            Arrays.asList("Pickaxes", "Shovels", "Axes"), Arrays.asList("Pickaxes", "Shovels"),
            () -> breakBlocks.isEnabled() && breakBlocksWhitelist.isEnabled()));
    private final BooleanSetting randomize = addSetting(new BooleanSetting("Randomize CPS", true));
    private final ModeSetting randomization = addSetting(new ModeSetting("Randomization", "Extra", () -> randomize.isEnabled(),
            "Normal", "Extra", "Extra+"));
    private final BooleanSetting jitter = addSetting(new BooleanSetting("Jitter", false));
    private final NumberSetting jitterAmount = addSetting(new NumberSetting("Jitter Amount", 0.45D, 0.05D, 2.0D, 0.05D,
            () -> jitter.isEnabled()));
    private final BooleanSetting forceFirstHit = addSetting(new BooleanSetting("Force First Hit", false));

    private final Minecraft minecraft = Minecraft.getMinecraft();
    private final Random random = new Random();
    private final dev.vibe.input.ClickDeadline schedule = new dev.vibe.input.ClickDeadline();
    private boolean ownsInput;
    private long nextDrift;
    private double driftingCps;
    private int extraClicks;
    private Entity lastForceTarget;

    public AutoClickerModule() {
        super("LeftClicker", "Assist held left-click attacks", Category.COMBAT, Keyboard.KEY_NONE);
    }

    /**
     * Runs at the start of the client tick, before Minecraft handles held
     * input.  It pulses the real attack key binding; Minecraft then invokes
     * its own clickMouse/controller code in the normal input pass.
     */
    public void prepareVanillaClick() {
        if (!isEnabled() || minecraft.thePlayer == null || minecraft.currentScreen != null) {
            restorePhysicalAttackKey();
            lastForceTarget = null;
            return;
        }
        boolean held = dev.vibe.input.VanillaClicks.physicallyDown(minecraft.gameSettings.keyBindAttack);
        if (requireHold.isEnabled() && !held) {
            restorePhysicalAttackKey();
            lastForceTarget = null;
            return;
        }
        if (!isAllowedItem()) {
            restorePhysicalAttackKey();
            lastForceTarget = null;
            return;
        }
        long now = System.currentTimeMillis();
        boolean hittingBlock = isHittingBlock();
        if (hittingBlock) {
            // Never synthesize a block attack. Leave the actual held button
            // untouched so vanilla keeps its normal start/continue/destroy
            // sequence when the user is breaking a block.
            restorePhysicalAttackKey();
            lastForceTarget = null;
            return;
        }
        Entity pointedEntity = pointedEntity();
        if (forceFirstHit.isEnabled() && pointedEntity != null && pointedEntity != lastForceTarget) {
            // A new opponent in reach gets the same immediate edge a normal
            // physical press has; later clicks remain on the CPS scheduler.
            schedule.reset();
            DebugModule.log("FirstHit", "forced click on new target");
        }
        lastForceTarget = pointedEntity;
        ownsInput = true;
        dev.vibe.input.VanillaClicks.discardPresses(minecraft.gameSettings.keyBindAttack);
        if (!isAllowedTarget()) {
            schedule.reset();
            suppressAttackKey();
            return;
        }
        int clicks = schedule.poll(now, () -> delay(now));
        suppressAttackKey();
        for (int i = 0; i < clicks; i++) pulseAttackKey();
        if (clicks > 0 && jitter.isEnabled()) {
            applyJitter();
        }
    }

    @Override
    protected void onDisable() {
        restorePhysicalAttackKey();
        schedule.reset();
        nextDrift = 0L;
        driftingCps = 0.0D;
        extraClicks = 0;
        lastForceTarget = null;
    }

    private boolean isAllowedTarget() {
        if (triggerMode.isEnabled()) {
            return minecraft.objectMouseOver != null
                    && minecraft.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY;
        }
        // A ray miss intentionally remains allowed: a physical held click
        // swings in the air too, and vanilla owns that behavior.
        return true;
    }

    private boolean isHittingBlock() {
        return minecraft.objectMouseOver != null
                && minecraft.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK;
    }

    private boolean isAllowedItem() {
        if (!limitItems.isEnabled()) {
            return true;
        }
        ItemStack stack = minecraft.thePlayer.getHeldItem();
        if (stack == null) {
            return false;
        }
        return matches(stack, itemWhitelist);
    }

    private Entity pointedEntity() {
        return minecraft.objectMouseOver != null && minecraft.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY
                ? minecraft.objectMouseOver.entityHit : null;
    }

    private void pulseAttackKey() {
        dev.vibe.input.VanillaClicks.pulseAttack(minecraft.gameSettings.keyBindAttack);
    }

    private void suppressAttackKey() {
        KeyBinding.setKeyBindState(minecraft.gameSettings.keyBindAttack.getKeyCode(), false);
    }

    private void restorePhysicalAttackKey() {
        ownsInput = false;
        schedule.reset();
        if (minecraft.gameSettings == null) {
            return;
        }
        dev.vibe.input.VanillaClicks.restore(minecraft.gameSettings.keyBindAttack);
    }

    public static void prepareInputHook() {
        dev.vibe.Vibe vibe = dev.vibe.Vibe.getInstance();
        if (vibe == null || vibe.getModuleManager() == null) return;
        AutoClickerModule clicker = vibe.getModuleManager().getModule(AutoClickerModule.class);
        KillAuraModule aura = vibe.getModuleManager().getModule(KillAuraModule.class);
        if (clicker != null && (aura == null || !aura.isEnabled())) clicker.prepareVanillaClick();
    }

    public static int missCooldownHook(int vanilla) {
        dev.vibe.Vibe vibe = dev.vibe.Vibe.getInstance();
        if (vibe == null || vibe.getModuleManager() == null) return vanilla;
        AutoClickerModule clicker = vibe.getModuleManager().getModule(AutoClickerModule.class);
        return clicker != null && clicker.isEnabled() && clicker.ownsInput ? 0 : vanilla;
    }

    private boolean matches(ItemStack stack, MultiSelectSetting allowed) {
        if (stack == null || stack.getItem() == null) {
            return false;
        }
        Item item = stack.getItem();
        if (allowed.isSelected("Swords") && item instanceof ItemSword) {
            return true;
        }
        if (allowed.isSelected("Axes") && item instanceof net.minecraft.item.ItemAxe) {
            return true;
        }
        if (allowed.isSelected("Pickaxes") && item instanceof net.minecraft.item.ItemPickaxe) {
            return true;
        }
        if (allowed.isSelected("Shovels") && item instanceof net.minecraft.item.ItemSpade) {
            return true;
        }
        return allowed.isSelected("Hoes") && item instanceof net.minecraft.item.ItemHoe;
    }

    private long delay(long now) {
        double minimum = cps.getMin();
        double maximum = cps.getMax();
        if (!randomize.isEnabled() || randomization.is("Normal")) {
            double selected = randomize.isEnabled() ? minimum + random.nextDouble() * (maximum - minimum) : maximum;
            return Math.max(1L, Math.round(1000.0D / Math.max(selected, 0.1D)));
        }
        if (randomization.is("Extra")) {
            // Triangular variation stays within the selected rate without
            // injecting unaccounted pauses. Those pauses made a 20 CPS range
            // schedule far below 20 CPS even though the HUD showed the range.
            double selected = averageSample(minimum, maximum);
            long result = Math.round(1000.0D / Math.max(selected, 0.1D)) + random.nextInt(25) - 12;
            return Math.max(1L, result);
        }
        if (driftingCps == 0.0D || now >= nextDrift) {
            double target = minimum + random.nextDouble() * (maximum - minimum);
            driftingCps = driftingCps == 0.0D ? target : driftingCps + (target - driftingCps) * 0.45D;
            nextDrift = now + 1200L + random.nextInt(2001);
        }
        double selected = Math.max(minimum, Math.min(maximum, driftingCps + (random.nextDouble() - 0.5D) * 0.8D));
        long result = Math.round(1000.0D / Math.max(selected, 0.1D)) + random.nextInt(17) - 8;
        extraClicks++;
        return Math.max(1L, result);
    }

    private double averageSample(double minimum, double maximum) {
        double average = (random.nextDouble() + random.nextDouble() + random.nextDouble()) / 3.0D;
        return minimum + average * (maximum - minimum);
    }

    private void applyJitter() {
        float amount = jitterAmount.getFloat();
        minecraft.thePlayer.rotationYaw += (random.nextFloat() - 0.5F) * amount * 2.0F;
        minecraft.thePlayer.rotationPitch = Math.max(-90.0F, Math.min(90.0F,
                minecraft.thePlayer.rotationPitch + (random.nextFloat() - 0.5F) * amount * 2.0F));
    }
}

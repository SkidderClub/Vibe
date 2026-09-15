package dev.vibe.module.impl;

import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.setting.BooleanSetting;
import dev.vibe.setting.NumberSetting;
import dev.vibe.setting.RangeSetting;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import org.lwjgl.input.Keyboard;

/** Edge-aware sneak helper; GodBridge mode uses a local safe-walk stop instead of sneak. */
public final class EagleModule extends Module {

    private final BooleanSetting godBridge = addSetting(new BooleanSetting("GodBridge SafeWalk", false));
    private final NumberSetting edgeDistance = addSetting(new NumberSetting("Block End Distance", 0.18D, 0.02D, 0.8D, 0.01D));
    private final BooleanSetting holdingBlocks = addSetting(new BooleanSetting("Holding Blocks", true));
    private final BooleanSetting allowBackwards = addSetting(new BooleanSetting("Walking Backwards", true));
    private final NumberSetting lookDownPitch = addSetting(new NumberSetting("Look Down Pitch", 0.0D, 0.0D, 90.0D, 1.0D));
    private final BooleanSetting onlyNotSneaking = addSetting(new BooleanSetting("Only When Not Sneaking", true));
    private final BooleanSetting requireSneak = addSetting(new BooleanSetting("Require Sneak", false));
    private final BooleanSetting randomize = addSetting(new BooleanSetting("Randomize Unsneak", true, () -> !godBridge.isEnabled()));
    private final RangeSetting unsneakRange = addSetting(new RangeSetting("Unsneak Range", 100.0D, 200.0D, 0.0D, 500.0D, 5.0D,
            () -> !godBridge.isEnabled() && randomize.isEnabled()));
    private final NumberSetting unsneakDelay = addSetting(new NumberSetting("Unsneak Delay", 100.0D, 0.0D, 500.0D, 5.0D,
            () -> !godBridge.isEnabled() && !randomize.isEnabled()));

    private final Minecraft minecraft = Minecraft.getMinecraft();
    private final Random random = new Random();
    private boolean autoSneaking;
    private long releaseAt;

    public EagleModule() {
        super("Eagle", "Sneak at unsupported block edges", Category.MOVEMENT, Keyboard.KEY_NONE);
    }

    public void tick() {
        if (!isEnabled() || minecraft.thePlayer == null || minecraft.theWorld == null || minecraft.currentScreen != null) {
            releaseSneak();
            return;
        }
        if (!conditionsAllow()) {
            releaseSneak();
            return;
        }
        boolean edge = isAtEdge();
        if (godBridge.isEnabled()) {
            releaseSneak();
            if (edge) {
                minecraft.thePlayer.motionX = 0.0D;
                minecraft.thePlayer.motionZ = 0.0D;
            }
            return;
        }
        if (edge) {
            releaseAt = 0L;
            setSneak(true);
        } else if (autoSneaking) {
            if (releaseAt == 0L) {
                releaseAt = System.currentTimeMillis() + nextUnsneakDelay();
            }
            if (System.currentTimeMillis() >= releaseAt) {
                releaseSneak();
            }
        }
    }

    @Override
    protected void onDisable() {
        releaseSneak();
    }

    private boolean conditionsAllow() {
        if (minecraft.thePlayer.rotationPitch < lookDownPitch.getFloat()) {
            return false;
        }
        if (requireSneak.isEnabled() && !isPhysicalSneakDown()) {
            return false;
        }
        if (holdingBlocks.isEnabled()) {
            ItemStack stack = minecraft.thePlayer.getHeldItem();
            if (stack == null || !(stack.getItem() instanceof ItemBlock)) {
                return false;
            }
        }
        if (!allowBackwards.isEnabled() && minecraft.thePlayer.movementInput.moveForward < 0.0F) {
            return false;
        }
        return !onlyNotSneaking.isEnabled() || !isPhysicalSneakDown();
    }

    private boolean isAtEdge() {
        if (!minecraft.thePlayer.onGround) {
            return false;
        }
        // This mirrors Vape's Legit Scaffold collision test: shift a slightly
        // shrunken player box down one block along the movement vector. No
        // support below that future footprint means it is time to sneak.
        double motionX = minecraft.thePlayer.motionX;
        double motionZ = minecraft.thePlayer.motionZ;
        if (Math.abs(motionX) + Math.abs(motionZ) < 0.001D) {
            float forward = minecraft.thePlayer.movementInput.moveForward;
            float strafe = minecraft.thePlayer.movementInput.moveStrafe;
            double yaw = Math.toRadians(minecraft.thePlayer.rotationYaw);
            motionX = -Math.sin(yaw) * forward + Math.cos(yaw) * strafe;
            motionZ = Math.cos(yaw) * forward + Math.sin(yaw) * strafe;
            double length = Math.sqrt(motionX * motionX + motionZ * motionZ);
            if (length < 0.001D) {
                return false;
            }
            double probe = 0.10D + edgeDistance.getDouble() * 0.25D;
            motionX = motionX / length * probe;
            motionZ = motionZ / length * probe;
        }
        AxisAlignedBB futureFeet = minecraft.thePlayer.getEntityBoundingBox().expand(-0.20D, 0.0D, -0.20D)
                .offset(motionX, -1.0D, motionZ);
        return minecraft.theWorld.getCollidingBoundingBoxes(minecraft.thePlayer, futureFeet).isEmpty();
    }

    private void setSneak(boolean sneak) {
        KeyBinding.setKeyBindState(minecraft.gameSettings.keyBindSneak.getKeyCode(), sneak);
        autoSneaking = sneak;
    }

    private void releaseSneak() {
        if (autoSneaking && !isPhysicalSneakDown()) {
            KeyBinding.setKeyBindState(minecraft.gameSettings.keyBindSneak.getKeyCode(), false);
        }
        autoSneaking = false;
        releaseAt = 0L;
    }

    private boolean isPhysicalSneakDown() {
        int key = minecraft.gameSettings.keyBindSneak.getKeyCode();
        return key > Keyboard.KEY_NONE && Keyboard.isKeyDown(key);
    }

    private long nextUnsneakDelay() {
        if (!randomize.isEnabled()) {
            return unsneakDelay.getInt();
        }
        int min = unsneakRange.getMinInt();
        int max = unsneakRange.getMaxInt();
        return min + (max == min ? 0 : random.nextInt(max - min + 1));
    }
}

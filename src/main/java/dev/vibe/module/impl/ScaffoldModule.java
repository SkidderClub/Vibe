package dev.vibe.module.impl;

import dev.vibe.Vibe;
import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.setting.ColorSetting;
import dev.vibe.setting.ModeSetting;
import dev.vibe.setting.NumberSetting;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import org.lwjgl.input.Keyboard;

/** Places the selected block beneath the player while walking over gaps. */
public final class ScaffoldModule extends Module {
    private final ModeSetting selectMode = addSetting(new ModeSetting("Select Mode", "Basic", "Basic", "Silent"));
    private final ColorSetting silentColor = addSetting(new ColorSetting("Silent Slot Color", 0xFF2DE2C2,
            () -> selectMode.is("Silent")));
    private final ModeSetting sneakMode = addSetting(new ModeSetting("Sneak Mode", "None", "Safewalk", "Eagle", "None"));
    private final NumberSetting placeRange = addSetting(new NumberSetting("Place Range", 4.5D, 2.0D, 6.0D, 0.1D));
    private final NumberSetting placeDelay = addSetting(new NumberSetting("Place Delay (ms)", 0.0D, 0.0D, 500.0D, 5.0D));

    private final Minecraft minecraft = Minecraft.getMinecraft();
    private int originalSlot = -1;
    private int spoofedSlot = -1;
    private boolean autoSneaking;
    private long nextPlace;
    private BlockPos target;
    private net.minecraft.entity.player.EntityPlayer owner;

    public ScaffoldModule() {
        super("Scaffold", "Places blocks beneath the player while walking", Category.WORLD, Keyboard.KEY_NONE);
    }

    public void tick() {
        if (owner != minecraft.thePlayer) {
            originalSlot = -1;
            spoofedSlot = -1;
            autoSneaking = false;
            nextPlace = 0L;
            owner = minecraft.thePlayer;
        }
        if (spoofedSlot >= 0 && originalSlot >= 0 && minecraft.thePlayer != null
                && minecraft.thePlayer.inventory.currentItem != originalSlot) {
            releaseSlot();
        }
        if (!isEnabled() || minecraft.thePlayer == null || minecraft.theWorld == null || minecraft.currentScreen != null) {
            reset();
            return;
        }
        target = findPlacementTarget();
        updateSneak();
        if (target == null) {
            releaseSlot();
            return;
        }
        int slot = findBlockSlot();
        if (slot < 0) {
            releaseSlot();
            return;
        }
        selectSlot(slot);
        long now = System.currentTimeMillis();
        if (now < nextPlace) return;
        Placement placement = findPlacement(target);
        if (placement == null || !withinReach(placement.hitVec)) return;
        if (place(placement)) {
            nextPlace = now + placeDelay.getInt();
        }
    }

    private boolean place(Placement placement) {
        int visible = beginActionHook();
        try {
            ItemStack held = minecraft.thePlayer.getHeldItem();
            if (held == null || !(held.getItem() instanceof ItemBlock)) return false;
            return minecraft.playerController.onPlayerRightClick(minecraft.thePlayer, minecraft.theWorld, held,
                    placement.support, placement.face, placement.hitVec);
        } finally {
            endActionHook(visible);
        }
    }

    private BlockPos findPlacementTarget() {
        BlockPos feet = new BlockPos(MathHelper.floor_double(minecraft.thePlayer.posX),
                MathHelper.floor_double(minecraft.thePlayer.getEntityBoundingBox().minY),
                MathHelper.floor_double(minecraft.thePlayer.posZ));
        BlockPos below = feet.down();
        if (isReplaceable(below)) return below;

        // Probe the four horizontal edges so a player moving over a gap can
        // place the first block before the center of the footprint leaves the
        // supporting platform.
        double[] movement = movementVector();
        double motionX = movement[0];
        double motionZ = movement[1];
        double length = Math.sqrt(motionX * motionX + motionZ * motionZ);
        if (length > 0.001D) {
            int x = MathHelper.floor_double(minecraft.thePlayer.posX + motionX / length * 0.35D);
            int z = MathHelper.floor_double(minecraft.thePlayer.posZ + motionZ / length * 0.35D);
            BlockPos ahead = new BlockPos(x, below.getY(), z);
            if (isReplaceable(ahead)) return ahead;
        }
        return null;
    }

    private Placement findPlacement(BlockPos position) {
        for (EnumFacing face : EnumFacing.values()) {
            if (face == EnumFacing.UP || face == EnumFacing.DOWN) continue;
            BlockPos support = position.offset(face.getOpposite());
            if (!isSolid(support)) continue;
            Vec3 hit = new Vec3(support.getX() + 0.5D + face.getFrontOffsetX() * 0.5D,
                    support.getY() + 0.5D + face.getFrontOffsetY() * 0.5D,
                    support.getZ() + 0.5D + face.getFrontOffsetZ() * 0.5D);
            return new Placement(support, face, hit);
        }
        if (isSolid(position.down())) {
            BlockPos support = position.down();
            return new Placement(support, EnumFacing.UP,
                    new Vec3(support.getX() + 0.5D, support.getY() + 1.0D, support.getZ() + 0.5D));
        }
        return null;
    }

    private int findBlockSlot() {
        int current = minecraft.thePlayer.inventory.currentItem;
        if (isBlockStack(minecraft.thePlayer.inventory.getStackInSlot(current))) return current;
        for (int slot = 0; slot < 9; slot++) {
            if (isBlockStack(minecraft.thePlayer.inventory.getStackInSlot(slot))) return slot;
        }
        return -1;
    }

    private void selectSlot(int slot) {
        if (slot < 0 || slot > 8) return;
        if (selectMode.is("Basic")) {
            if (minecraft.thePlayer.inventory.currentItem != slot) {
                if (originalSlot < 0) originalSlot = minecraft.thePlayer.inventory.currentItem;
                minecraft.thePlayer.inventory.currentItem = slot;
                minecraft.playerController.updateController();
            }
            return;
        }
        if (originalSlot < 0) originalSlot = minecraft.thePlayer.inventory.currentItem;
        if (spoofedSlot != slot) {
            minecraft.thePlayer.sendQueue.addToSendQueue(new C09PacketHeldItemChange(slot));
            spoofedSlot = slot;
        }
    }

    private void releaseSlot() {
        if (spoofedSlot >= 0 && originalSlot >= 0 && minecraft.thePlayer != null) {
            minecraft.thePlayer.sendQueue.addToSendQueue(new C09PacketHeldItemChange(originalSlot));
        }
        if (originalSlot >= 0 && minecraft.thePlayer != null
                && minecraft.thePlayer.inventory.currentItem != originalSlot) {
            minecraft.thePlayer.inventory.currentItem = originalSlot;
            minecraft.playerController.updateController();
        }
        originalSlot = -1;
        spoofedSlot = -1;
    }

    private void updateSneak() {
        if (sneakMode.is("Safewalk")) {
            releaseSneak();
            if (isAtEdge()) {
                double[] movement = movementVector();
                minecraft.thePlayer.motionX = 0.0D;
                minecraft.thePlayer.motionZ = 0.0D;
                // Keep the computed vector available to target selection on
                // the next tick even after Safewalk stops local movement.
                if (Math.abs(movement[0]) + Math.abs(movement[1]) < 0.001D) return;
            }
            return;
        }
        if (sneakMode.is("Eagle") && isAtEdge()) {
            KeyBinding.setKeyBindState(minecraft.gameSettings.keyBindSneak.getKeyCode(), true);
            autoSneaking = true;
        } else if (autoSneaking) {
            releaseSneak();
        }
    }

    private boolean isAtEdge() {
        if (!minecraft.thePlayer.onGround) return false;
        double[] movement = movementVector();
        double x = movement[0];
        double z = movement[1];
        if (Math.abs(x) + Math.abs(z) < 0.001D) return false;
        double length = Math.sqrt(x * x + z * z);
        AxisAlignedBB future = minecraft.thePlayer.getEntityBoundingBox().expand(-0.2D, 0.0D, -0.2D)
                .offset(x / length * 0.15D, -1.0D, z / length * 0.15D);
        return minecraft.theWorld.getCollidingBoundingBoxes(minecraft.thePlayer, future).isEmpty();
    }

    private double[] movementVector() {
        double x = minecraft.thePlayer.motionX;
        double z = minecraft.thePlayer.motionZ;
        if (Math.abs(x) + Math.abs(z) >= 0.001D) return new double[] {x, z};
        float forward = minecraft.thePlayer.movementInput.moveForward;
        float strafe = minecraft.thePlayer.movementInput.moveStrafe;
        double yaw = Math.toRadians(minecraft.thePlayer.rotationYaw);
        return new double[] {
                -Math.sin(yaw) * forward + Math.cos(yaw) * strafe,
                Math.cos(yaw) * forward + Math.sin(yaw) * strafe
        };
    }

    private void releaseSneak() {
        if (autoSneaking && minecraft.thePlayer != null && !isPhysicalSneakDown()) {
            KeyBinding.setKeyBindState(minecraft.gameSettings.keyBindSneak.getKeyCode(), false);
        }
        autoSneaking = false;
    }

    private boolean isPhysicalSneakDown() {
        int key = minecraft.gameSettings.keyBindSneak.getKeyCode();
        return key > Keyboard.KEY_NONE && Keyboard.isKeyDown(key);
    }

    private boolean isReplaceable(BlockPos pos) {
        return minecraft.theWorld.isAirBlock(pos) || minecraft.theWorld.getBlockState(pos).getBlock().isReplaceable(minecraft.theWorld, pos);
    }

    private boolean isSolid(BlockPos pos) {
        Block block = minecraft.theWorld.getBlockState(pos).getBlock();
        return block != null && !isReplaceable(pos) && block.getMaterial().blocksMovement();
    }

    private boolean isBlockStack(ItemStack stack) {
        return stack != null && stack.stackSize > 0 && stack.getItem() instanceof ItemBlock
                && ((ItemBlock) stack.getItem()).getBlock().getMaterial().blocksMovement();
    }

    private boolean withinReach(Vec3 hit) {
        Vec3 eyes = minecraft.thePlayer.getPositionEyes(1.0F);
        return eyes.squareDistanceTo(hit) <= placeRange.getDouble() * placeRange.getDouble();
    }

    private void reset() {
        releaseSneak();
        releaseSlot();
        target = null;
        nextPlace = 0L;
    }

    @Override
    protected void onDisable() { reset(); }

    public boolean hasSilentSlot() { return isEnabled() && selectMode.is("Silent") && spoofedSlot >= 0; }
    public int getSpoofedSlot() { return spoofedSlot; }
    public ColorSetting getSilentColor() { return silentColor; }
    public BlockPos getTarget() { return target; }

    /** Used by the transformed placement action to expose the silent stack. */
    public static int serverSlotHook(int vanilla) {
        Vibe vibe = Vibe.getInstance();
        if (vibe == null || vibe.getModuleManager() == null) return vanilla;
        ScaffoldModule scaffold = vibe.getModuleManager().getModule(ScaffoldModule.class);
        return scaffold != null && scaffold.hasSilentSlot() ? scaffold.spoofedSlot : vanilla;
    }

    public static int beginActionHook() {
        Vibe vibe = Vibe.getInstance();
        ScaffoldModule scaffold = vibe == null || vibe.getModuleManager() == null
                ? null : vibe.getModuleManager().getModule(ScaffoldModule.class);
        if (scaffold == null || !scaffold.hasSilentSlot() || scaffold.minecraft.thePlayer == null) return -1;
        int visible = scaffold.minecraft.thePlayer.inventory.currentItem;
        scaffold.minecraft.thePlayer.inventory.currentItem = scaffold.spoofedSlot;
        return visible;
    }

    public static void endActionHook(int visible) {
        Minecraft mc = Minecraft.getMinecraft();
        if (visible >= 0 && mc.thePlayer != null) mc.thePlayer.inventory.currentItem = visible;
    }

    private static final class Placement {
        private final BlockPos support;
        private final EnumFacing face;
        private final Vec3 hitVec;

        private Placement(BlockPos support, EnumFacing face, Vec3 hitVec) {
            this.support = support;
            this.face = face;
            this.hitVec = hitVec;
        }
    }
}

package dev.vibe.module.impl;

import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.setting.BooleanSetting;
import dev.vibe.setting.ColorSetting;
import dev.vibe.setting.NumberSetting;
import net.minecraft.block.BlockBed;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import org.lwjgl.input.Keyboard;

/** Mines the nearest visible enemy bed through the normal block controller. */
public final class BedAuraModule extends Module {
    private final NumberSetting range = addSetting(new NumberSetting("Range", 5.0D, 2.0D, 8.0D, 0.1D));
    private final NumberSetting fov = addSetting(new NumberSetting("FOV", 180.0D, 30.0D, 360.0D, 1.0D));
    private final NumberSetting scanRate = addSetting(new NumberSetting("Scan Rate (ms)", 250.0D, 50.0D, 2000.0D, 25.0D));
    private final BooleanSetting switchBack = addSetting(new BooleanSetting("Switch Back", true));
    private final BooleanSetting renderTarget = addSetting(new BooleanSetting("Render Target", true));
    private final ColorSetting targetColor = addSetting(new ColorSetting("Target Color", 0x78FF4F6D,
            () -> renderTarget.isEnabled()));
    private final Minecraft minecraft = Minecraft.getMinecraft();
    private BlockPos target;
    private long nextScan;
    private int previousSlot = -1;

    public BedAuraModule() {
        super("BedAura", "Automatically mines the nearest visible bed", Category.WORLD, Keyboard.KEY_NONE);
    }

    public void tick() {
        if (!isEnabled() || minecraft.thePlayer == null || minecraft.theWorld == null
                || minecraft.currentScreen != null || !minecraft.thePlayer.capabilities.allowEdit) {
            reset();
            return;
        }
        long now = System.currentTimeMillis();
        if (now >= nextScan || !isValidTarget(target)) {
            target = findTarget();
            nextScan = now + Math.max(50L, scanRate.getInt());
        }
        if (target == null) {
            reset();
            return;
        }
        equipTool();
        Vec3 hit = closestPoint(target);
        float[] rotations = rotations(hit);
        MoveFixModule moveFix = dev.vibe.Vibe.getInstance().getModuleManager().getModule(MoveFixModule.class);
        if (moveFix != null) moveFix.setFakeRotation(getId(), rotations[0], rotations[1]);
        minecraft.playerController.onPlayerDamageBlock(target, EnumFacing.UP);
    }

    private BlockPos findTarget() {
        BlockPos origin = new BlockPos(minecraft.thePlayer);
        int radius = (int) Math.ceil(range.getDouble());
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int x = origin.getX() - radius; x <= origin.getX() + radius; x++) {
            for (int y = Math.max(0, origin.getY() - radius); y <= origin.getY() + radius; y++) {
                for (int z = origin.getZ() - radius; z <= origin.getZ() + radius; z++) {
                    BlockPos candidate = new BlockPos(x, y, z);
                    if (!(minecraft.theWorld.getBlockState(candidate).getBlock() instanceof BlockBed)
                            || !isInRange(candidate) || !inFov(candidate) || !isVisible(candidate)) continue;
                    double distance = minecraft.thePlayer.getDistanceSq(candidate);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = candidate;
                    }
                }
            }
        }
        return best;
    }

    private boolean isValidTarget(BlockPos pos) {
        return pos != null && minecraft.theWorld.getBlockState(pos).getBlock() instanceof BlockBed
                && isInRange(pos) && isVisible(pos);
    }

    private boolean isInRange(BlockPos pos) {
        return minecraft.thePlayer.getDistanceSq(pos) <= range.getDouble() * range.getDouble();
    }

    private boolean inFov(BlockPos pos) {
        if (fov.getDouble() >= 360.0D) return true;
        Vec3 eye = minecraft.thePlayer.getPositionEyes(1.0F);
        Vec3 look = minecraft.thePlayer.getLook(1.0F);
        Vec3 to = closestPoint(pos).subtract(eye);
        double length = to.lengthVector();
        if (length < 1.0E-5D) return true;
        double dot = (look.xCoord * to.xCoord + look.yCoord * to.yCoord + look.zCoord * to.zCoord) / length;
        return Math.acos(MathHelper.clamp_double(dot, -1.0D, 1.0D)) <= Math.toRadians(fov.getDouble() * 0.5D);
    }

    private boolean isVisible(BlockPos pos) {
        Vec3 eye = minecraft.thePlayer.getPositionEyes(1.0F);
        Vec3 hit = closestPoint(pos);
        net.minecraft.util.MovingObjectPosition trace = minecraft.theWorld.rayTraceBlocks(eye, hit, false, true, false);
        return trace == null || (trace.getBlockPos() != null && pos.equals(trace.getBlockPos()));
    }

    private Vec3 closestPoint(BlockPos pos) {
        AxisAlignedBB box = minecraft.theWorld.getBlockState(pos).getBlock().getSelectedBoundingBox(minecraft.theWorld, pos);
        if (box == null) box = new AxisAlignedBB(pos, pos.add(1, 1, 1));
        Vec3 eye = minecraft.thePlayer.getPositionEyes(1.0F);
        return new Vec3(MathHelper.clamp_double(eye.xCoord, box.minX, box.maxX),
                MathHelper.clamp_double(eye.yCoord, box.minY, box.maxY),
                MathHelper.clamp_double(eye.zCoord, box.minZ, box.maxZ));
    }

    private float[] rotations(Vec3 point) {
        Vec3 eye = minecraft.thePlayer.getPositionEyes(1.0F);
        double dx = point.xCoord - eye.xCoord;
        double dy = point.yCoord - eye.yCoord;
        double dz = point.zCoord - eye.zCoord;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        return new float[]{(float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D),
                (float) (-Math.toDegrees(Math.atan2(dy, horizontal)))};
    }

    private void equipTool() {
        int best = -1;
        float strength = 1.0F;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = minecraft.thePlayer.inventory.getStackInSlot(slot);
            if (stack == null) continue;
            float candidate = stack.getStrVsBlock(minecraft.theWorld.getBlockState(target).getBlock());
            if (candidate > strength) { strength = candidate; best = slot; }
        }
        if (best >= 0 && best != minecraft.thePlayer.inventory.currentItem) {
            if (previousSlot < 0) previousSlot = minecraft.thePlayer.inventory.currentItem;
            minecraft.thePlayer.inventory.currentItem = best;
            minecraft.playerController.updateController();
        }
    }

    private void reset() {
        if (switchBack.isEnabled() && previousSlot >= 0 && minecraft.thePlayer != null) {
            minecraft.thePlayer.inventory.currentItem = previousSlot;
            minecraft.playerController.updateController();
        }
        MoveFixModule moveFix = dev.vibe.Vibe.getInstance().getModuleManager().getModule(MoveFixModule.class);
        if (moveFix != null) moveFix.clearFakeRotation(getId());
        target = null;
        previousSlot = -1;
    }

    @Override protected void onDisable() { reset(); }
    public BlockPos getTarget() { return target; }
    public ColorSetting getTargetColor() { return targetColor; }
    public boolean shouldRenderTarget() { return isEnabled() && renderTarget.isEnabled() && target != null; }
}

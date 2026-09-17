package dev.vibe.module.impl;

import dev.vibe.Vibe;
import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.setting.BooleanSetting;
import dev.vibe.setting.ColorSetting;
import dev.vibe.setting.ModeSetting;
import dev.vibe.setting.NumberSetting;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import org.lwjgl.input.Keyboard;

/** Selects a bed/defence at tick start and mines after vanilla sends its rotation. */
public final class BedAuraModule extends Module {
    private final ModeSetting mode = addSetting(new ModeSetting("Mode", "Raycast", "Raycast", "Vanilla", "Hypixel"));
    private final NumberSetting range = addSetting(new NumberSetting("Range", 5, 2, 8, .1));
    private final NumberSetting fov = addSetting(new NumberSetting("FOV", 180, 30, 360, 1));
    private final NumberSetting scanRate = addSetting(new NumberSetting("Scan Rate (ms)", 250, 50, 2000, 25));
    private final BooleanSetting switchBack = addSetting(new BooleanSetting("Switch Back", true));
    private final BooleanSetting renderTarget = addSetting(new BooleanSetting("Render Target", true));
    private final ColorSetting targetColor = addSetting(new ColorSetting("Target Color", 0x78FF4F6D, () -> renderTarget.isEnabled()));
    private final Minecraft minecraft = Minecraft.getMinecraft();
    private BlockPos bed, target;
    private MovingObjectPosition plannedHit;
    private long nextScan;
    private int previousSlot = -1;
    private boolean ready, resetting, mining;
    private net.minecraft.client.entity.EntityPlayerSP owner;

    public BedAuraModule() {
        super("BedAura", "Mines beds directly or clears their defences", Category.WORLD, Keyboard.KEY_NONE);
    }

    public void tick() {
        ready = false;
        if (owner != minecraft.thePlayer) { target = bed = null; previousSlot = -1; mining = false; nextScan = 0; owner = minecraft.thePlayer; }
        if (!isEnabled() || owner == null || minecraft.theWorld == null || minecraft.currentScreen != null
                || !owner.capabilities.allowEdit || owner.isUsingItem() || minecraft.gameSettings.keyBindAttack.isKeyDown()) {
            reset(); return;
        }
        if (!validBed(bed)) {
            stopMining(); bed = null;
            long now = System.currentTimeMillis();
            if (now >= nextScan) { bed = findBed(); nextScan = now + scanRate.getInt(); }
        }
        MovingObjectPosition hit = bed == null ? null : selectHit(bed);
        if (hit == null || !breakable(hit.getBlockPos())) { reset(); return; }
        if (!hit.getBlockPos().equals(target)) stopMining();
        target = hit.getBlockPos(); plannedHit = hit;
        equipTool();
        float[] angles = rotations(hit.hitVec);
        MoveFixModule moveFix = Vibe.getInstance().getModuleManager().getModule(MoveFixModule.class);
        if (moveFix == null) { reset(); return; }
        moveFix.setFakeRotation(getId(), angles[0], angles[1]);
        ready = true;
    }

    /** Called once after walking packets; swing and digging share the next tick boundary. */
    public void afterWalkingUpdate(Object entity) {
        if (!ready || entity != owner || owner != minecraft.thePlayer) return;
        ready = false;
        if (!isEnabled() || minecraft.currentScreen != null || !breakable(target)) { reset(); return; }
        MoveFixModule moveFix = Vibe.getInstance().getModuleManager().getModule(MoveFixModule.class);
        float[] angles = rotations(plannedHit.hitVec);
        if (moveFix == null || Math.abs(MathHelper.wrapAngleTo180_float(moveFix.getRotationYaw() - angles[0])) > .5F
                || Math.abs(moveFix.getRotationPitch() - angles[1]) > .5F) { stopMining(); return; }
        if (mode.is("Raycast")) {
            Vec3 eyes = owner.getPositionEyes(1);
            double yaw = Math.toRadians(moveFix.getRotationYaw()), pitch = Math.toRadians(moveFix.getRotationPitch());
            double reach = Math.min(range.getDouble(), minecraft.playerController.getBlockReachDistance());
            Vec3 direction = new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
            MovingObjectPosition actual = minecraft.theWorld.rayTraceBlocks(eyes,
                    eyes.addVector(direction.xCoord * reach, direction.yCoord * reach, direction.zCoord * reach), false, true, false);
            if (actual == null || !target.equals(actual.getBlockPos()) || !withinReach(actual.hitVec)) { stopMining(); return; }
            plannedHit = actual;
        }
        // swingItem supplies both the first-person animation and C0A animation packet.
        owner.swingItem();
        mining = minecraft.playerController.onPlayerDamageBlock(target, plannedHit.sideHit);
    }

    private BlockPos findBed() {
        BlockPos origin = new BlockPos(owner), best = null;
        int radius = (int) Math.ceil(range.getDouble());
        double distance = Double.MAX_VALUE;
        for (int x=-radius; x<=radius; x++) for (int y=-radius; y<=radius; y++) for (int z=-radius; z<=radius; z++) {
            BlockPos candidate = origin.add(x,y,z);
            if (!validBed(candidate) || !inFov(candidate) || selectHit(candidate) == null) continue;
            double value = owner.getPositionEyes(1).squareDistanceTo(center(candidate));
            if (value < distance) { distance = value; best = candidate; }
        }
        return best;
    }

    private boolean validBed(BlockPos pos) {
        return pos != null && minecraft.theWorld != null && minecraft.theWorld.getBlockState(pos).getBlock() instanceof BlockBed
                && owner.getPositionEyes(1).squareDistanceTo(center(pos)) <= range.getDouble() * range.getDouble();
    }

    private MovingObjectPosition selectHit(BlockPos bedPos) {
        if (mode.is("Raycast")) {
            MovingObjectPosition ray = minecraft.theWorld.rayTraceBlocks(owner.getPositionEyes(1), center(bedPos), false, true, false);
            return ray != null && ray.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                    && breakable(ray.getBlockPos()) && withinReach(ray.hitVec) ? ray : null;
        }
        if (mode.is("Vanilla")) return directHit(bedPos);
        // Hypixel: one exposed side of either half is sufficient. Otherwise remove
        // one neighbouring defence block, even when another layer obstructs it.
        BlockPos best = null; double distance = Double.MAX_VALUE;
        java.util.List<BlockPos> halves = new java.util.ArrayList<BlockPos>();
        halves.add(bedPos);
        net.minecraft.block.state.IBlockState state = minecraft.theWorld.getBlockState(bedPos);
        EnumFacing facing = state.getValue(BlockBed.FACING);
        BlockPos other = bedPos.offset(state.getValue(BlockBed.PART) == BlockBed.EnumPartType.FOOT ? facing : facing.getOpposite());
        if (minecraft.theWorld.getBlockState(other).getBlock() instanceof BlockBed) halves.add(other);
        for (BlockPos half : halves) for (EnumFacing side : EnumFacing.values()) {
            if (side == EnumFacing.DOWN) continue;
            BlockPos adjacent = half.offset(side);
            if (halves.contains(adjacent)) continue;
            Block block = minecraft.theWorld.getBlockState(adjacent).getBlock();
            if (block.getMaterial().isReplaceable()) return directHit(bedPos);
            if (!breakable(adjacent)) continue;
            double value = owner.getPositionEyes(1).squareDistanceTo(center(adjacent));
            if (value < distance && value <= range.getDouble() * range.getDouble()) { distance = value; best = adjacent; }
        }
        return best == null ? null : directHit(best);
    }

    private boolean breakable(BlockPos pos) {
        if (pos == null || minecraft.theWorld == null || minecraft.theWorld.isAirBlock(pos)) return false;
        Block block = minecraft.theWorld.getBlockState(pos).getBlock();
        return !block.getMaterial().isLiquid() && block.getBlockHardness(minecraft.theWorld, pos) >= 0;
    }
    private boolean withinReach(Vec3 point) {
        double reach = Math.min(range.getDouble(), minecraft.playerController.getBlockReachDistance());
        return point != null && owner.getPositionEyes(1).squareDistanceTo(point) <= reach * reach;
    }
    private AxisAlignedBB box(BlockPos pos) {
        Block block = minecraft.theWorld.getBlockState(pos).getBlock();
        block.setBlockBoundsBasedOnState(minecraft.theWorld, pos);
        AxisAlignedBB box = block.getSelectedBoundingBox(minecraft.theWorld, pos);
        return box == null ? new AxisAlignedBB(pos, pos.add(1,1,1)) : box;
    }
    private Vec3 center(BlockPos pos) {
        AxisAlignedBB box = box(pos);
        return new Vec3((box.minX+box.maxX)/2, (box.minY+box.maxY)/2, (box.minZ+box.maxZ)/2);
    }
    private MovingObjectPosition directHit(BlockPos pos) {
        MovingObjectPosition hit = box(pos).calculateIntercept(owner.getPositionEyes(1), center(pos));
        return hit == null ? null : new MovingObjectPosition(hit.hitVec, hit.sideHit, pos);
    }
    private boolean inFov(BlockPos pos) {
        if (fov.getDouble() >= 360) return true;
        Vec3 direction = center(pos).subtract(owner.getPositionEyes(1)).normalize();
        Vec3 look = owner.getLook(1);
        return Math.acos(MathHelper.clamp_double(look.dotProduct(direction), -1, 1)) <= Math.toRadians(fov.getDouble()/2);
    }
    private float[] rotations(Vec3 point) {
        Vec3 delta = point.subtract(owner.getPositionEyes(1));
        return new float[]{(float) (Math.toDegrees(Math.atan2(delta.zCoord,delta.xCoord))-90),
                (float) -Math.toDegrees(Math.atan2(delta.yCoord,Math.hypot(delta.xCoord,delta.zCoord)))};
    }
    private void equipTool() {
        int best = -1; float strength = 1;
        for (int slot=0; slot<9; slot++) {
            ItemStack stack = owner.inventory.getStackInSlot(slot);
            if (stack == null) continue;
            float value = stack.getStrVsBlock(minecraft.theWorld.getBlockState(target).getBlock());
            if (value > strength) { strength = value; best = slot; }
        }
        if (best >= 0 && best != owner.inventory.currentItem) {
            if (previousSlot < 0) previousSlot = owner.inventory.currentItem;
            owner.inventory.currentItem = best; minecraft.playerController.updateController();
        }
    }
    private void stopMining() {
        if (mining && minecraft.playerController != null && minecraft.thePlayer != null) {
            resetting = true;
            try { minecraft.playerController.resetBlockRemoving(); } finally { resetting = false; }
        }
        mining = false;
    }
    private void reset() {
        stopMining(); ready = false;
        if (switchBack.isEnabled() && previousSlot >= 0 && owner == minecraft.thePlayer && owner != null) {
            owner.inventory.currentItem = previousSlot; minecraft.playerController.updateController();
        }
        if (Vibe.getInstance() != null && Vibe.getInstance().getModuleManager() != null) {
            MoveFixModule moveFix = Vibe.getInstance().getModuleManager().getModule(MoveFixModule.class);
            if (moveFix != null) moveFix.clearFakeRotation(getId());
        }
        bed = target = null; plannedHit = null; previousSlot = -1;
    }
    /** Vanilla's idle attack-key path must not abort an owned mining action every tick. */
    public static boolean keepBreakingHook() {
        if (Vibe.getInstance() == null || Vibe.getInstance().getModuleManager() == null) return false;
        BedAuraModule module = Vibe.getInstance().getModuleManager().getModule(BedAuraModule.class);
        return module != null && module.isEnabled() && !module.resetting && (module.ready || module.mining)
                && module.owner == module.minecraft.thePlayer && module.minecraft.currentScreen == null;
    }
    @Override protected void onDisable() { reset(); nextScan = 0; }
    public ModeSetting getMode() { return mode; }
    public BlockPos getTarget() { return target; }
    public ColorSetting getTargetColor() { return targetColor; }
    public boolean shouldRenderTarget() { return isEnabled() && renderTarget.isEnabled() && target != null; }
}

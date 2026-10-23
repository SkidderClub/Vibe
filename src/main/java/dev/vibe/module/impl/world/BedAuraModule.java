package dev.vibe.module.impl.world;

import dev.vibe.Vibe;
import dev.vibe.input.VanillaClicks;
import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.module.impl.movement.MoveFixModule;
import dev.vibe.setting.BooleanSetting;
import dev.vibe.setting.ColorSetting;
import dev.vibe.setting.ModeSetting;
import dev.vibe.setting.NumberSetting;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import org.lwjgl.input.Keyboard;

/** Plans rotations at tick start; Minecraft's normal held-attack input owns all digging and swings. */
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
    private float plannedYaw, plannedPitch;
    private long nextScan;
    private int previousSlot = -1;
    private boolean ready, inputPrepared, inputOwned, holdingAttack;
    private MovingObjectPosition savedMouseOver;
    private Entity savedPointedEntity;
    private net.minecraft.client.entity.EntityPlayerSP owner;

    public BedAuraModule() {
        super("BedAura", "Mines beds directly or clears their defences", Category.WORLD, Keyboard.KEY_NONE);
    }

    public void tick() {
        restoreInput();
        inputPrepared = false;
        ready = false;
        if (owner != minecraft.thePlayer) { target = bed = null; previousSlot = -1; holdingAttack = false; nextScan = 0; owner = minecraft.thePlayer; }
        if (!isEnabled() || owner == null || minecraft.theWorld == null || minecraft.currentScreen != null
                || !owner.capabilities.allowEdit || owner.isUsingItem() || VanillaClicks.physicallyDown(minecraft.gameSettings.keyBindAttack)
                || ScaffoldModule.isActive()) {
            reset(); return;
        }
        if (!validBed(bed)) {
            bed = null;
            long now = System.currentTimeMillis();
            if (now >= nextScan) { bed = findBed(); nextScan = now + scanRate.getInt(); }
        }
        MovingObjectPosition hit = bed == null ? null : selectHit(bed);
        if (hit == null || !breakable(hit.getBlockPos())) { reset(); return; }
        target = hit.getBlockPos();
        float[] angles = rotations(hit.hitVec);
        MoveFixModule moveFix = Vibe.getInstance().getModuleManager().getModule(MoveFixModule.class);
        if (moveFix == null) { reset(); return; }
        // Like KillAura, select the rotation before MoveFix samples keyboard
        // input. Keep it through moveFlying/jump and the walking packet.
        moveFix.setFakeRotation(getId(), angles[0], angles[1]);
        plannedYaw = moveFix.getRotationYaw();
        plannedPitch = moveFix.getRotationPitch();
        ready = true;
    }

    /** Same pre-dispatch boundary as KillAura: supply a hit and held key, never send dig/animation packets. */
    private void prepareInput() {
        if (inputPrepared) return;
        inputPrepared = true;
        if (!ready || !isEnabled() || owner == null || owner != minecraft.thePlayer || !minecraft.inGameHasFocus
                || minecraft.currentScreen != null || minecraft.playerController == null || !breakable(target)
                || owner.isUsingItem() || minecraft.gameSettings.keyBindUseItem.isKeyDown()
                || VanillaClicks.physicallyDown(minecraft.gameSettings.keyBindAttack) || ScaffoldModule.isActive()) {
            holdingAttack = ready = false;
            return;
        }
        MoveFixModule moveFix = Vibe.getInstance().getModuleManager().getModule(MoveFixModule.class);
        if (moveFix == null || Math.abs(MathHelper.wrapAngleTo180_float(moveFix.getRotationYaw() - plannedYaw)) > .001F
                || Math.abs(moveFix.getRotationPitch() - plannedPitch) > .001F) { holdingAttack = ready = false; return; }
        // Input is handled before player movement. Raycast from this same
        // position and leave the chosen rotation untouched through physics/C03.
        Vec3 eyes = owner.getPositionEyes(1);
        double yaw = Math.toRadians(plannedYaw), pitch = Math.toRadians(plannedPitch);
        double reach = Math.min(range.getDouble(), minecraft.playerController.getBlockReachDistance());
        Vec3 direction = new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
        Vec3 end = eyes.addVector(direction.xCoord * reach, direction.yCoord * reach, direction.zCoord * reach);
        MovingObjectPosition actual = mode.is("Raycast")
                ? minecraft.theWorld.rayTraceBlocks(eyes, end, false, true, false)
                : box(target).calculateIntercept(eyes, end);
        if (actual == null || (mode.is("Raycast") && !target.equals(actual.getBlockPos()))
                || !withinReach(actual.hitVec)) { holdingAttack = ready = false; return; }
        equipTool();
        savedMouseOver = minecraft.objectMouseOver;
        savedPointedEntity = minecraft.pointedEntity;
        inputOwned = true;
        minecraft.objectMouseOver = new MovingObjectPosition(actual.hitVec, actual.sideHit, target);
        minecraft.pointedEntity = null;
        KeyBinding attack = minecraft.gameSettings.keyBindAttack;
        VanillaClicks.discardPresses(attack);
        // One physical-style press starts the action. Continuing/retargeting
        // holds LMB: vanilla owns START/ABORT/STOP, hit delay and conditional swings.
        if (!holdingAttack) VanillaClicks.pulseAttack(attack);
        else KeyBinding.setKeyBindState(attack.getKeyCode(), true);
        holdingAttack = true;
    }

    private void restoreInput() {
        if (!inputOwned) return;
        minecraft.objectMouseOver = savedMouseOver;
        minecraft.pointedEntity = savedPointedEntity;
        savedMouseOver = null; savedPointedEntity = null;
        if (minecraft.gameSettings != null) {
            VanillaClicks.discardPresses(minecraft.gameSettings.keyBindAttack);
            VanillaClicks.restore(minecraft.gameSettings.keyBindAttack);
        }
        inputOwned = false;
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
    private void reset() {
        restoreInput(); holdingAttack = ready = false;
        if (switchBack.isEnabled() && previousSlot >= 0 && owner == minecraft.thePlayer && owner != null) {
            // Vanilla's next input tick synchronizes the slot. Toggling after
            // movement must not inject a post-C03 held-item-change packet.
            owner.inventory.currentItem = previousSlot;
        }
        if (Vibe.getInstance() != null && Vibe.getInstance().getModuleManager() != null) {
            MoveFixModule moveFix = Vibe.getInstance().getModuleManager().getModule(MoveFixModule.class);
            if (moveFix != null) moveFix.clearFakeRotation(getId());
        }
        bed = target = null; previousSlot = -1;
    }
    private static BedAuraModule module() {
        if (Vibe.getInstance() == null || Vibe.getInstance().getModuleManager() == null) return null;
        return Vibe.getInstance().getModuleManager().getModule(BedAuraModule.class);
    }
    public static void prepareInputHook() { BedAuraModule module = module(); if (module != null) module.prepareInput(); }
    public static void finishInputHook() { BedAuraModule module = module(); if (module != null) module.restoreInput(); }
    public static boolean ownsMiningInput() { BedAuraModule module = module(); return module != null && module.isActivelyMining(); }
    @Override protected void onDisable() { reset(); nextScan = 0; }
    public ModeSetting getMode() { return mode; }
    public BlockPos getTarget() { return target; }
    public boolean isActivelyMining() { return isEnabled() && owner == minecraft.thePlayer && ready && target != null; }
    public float getAuraBreakProgress() {
        if (!isActivelyMining() || minecraft.playerController == null) return 0.0F;
        return dev.vibe.module.impl.visual.BreakProgressModule.controllerProgress(minecraft.playerController);
    }
    public ColorSetting getTargetColor() { return targetColor; }
    public boolean shouldRenderTarget() { return isEnabled() && renderTarget.isEnabled() && target != null; }
}

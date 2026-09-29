package dev.vibe.module.impl;

import dev.vibe.Vibe;
import dev.vibe.combat.RotationMath;
import dev.vibe.input.VanillaClicks;
import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.setting.BooleanSetting;
import dev.vibe.setting.ModeSetting;
import dev.vibe.setting.NumberSetting;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.block.BlockAir;
import net.minecraft.block.BlockCarpet;
import net.minecraft.block.BlockChest;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.BlockFurnace;
import net.minecraft.block.BlockLadder;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.BlockSkull;
import net.minecraft.block.BlockSnow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovementInput;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import org.lwjgl.input.Keyboard;

/**
 * Places blocks against the supporting face closest to the player's feet.
 *
 * <p>Every rotation is handed to {@link MoveFixModule}, which sends it,
 * corrects movement for it and adjusts the third-person model. A placement is
 * only attempted when a ray cast from exactly that server rotation hits the
 * chosen face, so the hit vector always matches what the server receives.</p>
 *
 * <p>Tick order: {@link #tickStart()} selects the block and rotation before
 * vanilla samples input, {@link #applyMoveInput} edits the corrected movement
 * keys, {@link #prepareInputHook()} places in vanilla's click pass and
 * {@link #beforeWalkingUpdate} runs the tower before the movement packet.</p>
 */
public final class ScaffoldModule extends Module {
    private static final float GOD_BRIDGE_STEP = 0.22F;

    private final ModeSetting mode = addSetting(new ModeSetting("Mode", "Normal", "Normal", "Telly"));
    private final ModeSetting rotations = addSetting(new ModeSetting("Rotations", "Intave",
            () -> mode.is("Normal"), "Intave", "Polar", "God Bridge"));
    private final BooleanSetting sideways = addSetting(new BooleanSetting("Sideways", false, () -> mode.is("Normal")));
    private final ModeSetting sprint = addSetting(new ModeSetting("Sprint", "Always", "Always", "Off", "Legit"));
    private final ModeSetting tower = addSetting(new ModeSetting("Tower", "None", "None", "NCP", "Timer", "Intave"));
    private final BooleanSetting keepY = addSetting(new BooleanSetting("Keep Y", true));
    private final BooleanSetting sneak = addSetting(new BooleanSetting("Sneak", false, () -> mode.is("Normal")));
    private final NumberSetting sneakDelay = addSetting(new NumberSetting("Sneak Delay (ms)", 400.0D, 100.0D, 2000.0D, 100.0D,
            () -> mode.is("Normal") && sneak.isEnabled()));
    private final BooleanSetting safeWalk = addSetting(new BooleanSetting("Safe Walk", true, () -> mode.is("Normal")));
    private final BooleanSetting movementFix = addSetting(new BooleanSetting("Move Fix", true, () -> mode.is("Normal")));
    private final BooleanSetting spoofSlot = addSetting(new BooleanSetting("Spoof Slot", true));
    private final BooleanSetting swing = addSetting(new BooleanSetting("Swing", false));
    private final BooleanSetting jump = addSetting(new BooleanSetting("Jump", false, () -> mode.is("Normal")));
    private final BooleanSetting dragClick = addSetting(new BooleanSetting("Drag Click", false));
    private final BooleanSetting renderCount = addSetting(new BooleanSetting("Render Count", false));
    private final NumberSetting smoothSpeed = addSetting(new NumberSetting("Smooth Speed", 30.0D, 10.0D, 180.0D, 10.0D));

    private final Minecraft minecraft = Minecraft.getMinecraft();
    private final Random random = new Random();
    private EntityPlayerSP owner;
    private int targetY;
    private int placed;
    private int offGroundTicks;
    private float scaffoldYaw;
    private float scaffoldPitch;
    private BlockPos blockPos;
    private EnumFacing facing;
    private AxisAlignedBB targetBox;
    private boolean polarSneak;
    private boolean polarState;
    private long timerStart;
    private int blockSlot = -1;
    /** Hotbar slot the server holds while Spoof Slot keeps the visible slot. */
    private int serverSlot = -1;
    /** Visible slot to restore once vanilla's click pass has finished. */
    private int swappedSlot = -1;
    /** Visible slot before Scaffold selected blocks without Spoof Slot. */
    private int restoreSlot = -1;
    private boolean rotating;
    private boolean wasSneaking;
    private boolean sneakOwned;
    private boolean sprintOwned;
    private boolean timerOwned;
    private static Field rightClickDelay;

    public ScaffoldModule() {
        super("Scaffold", "Places blocks beneath you while bridging and towering", Category.WORLD, Keyboard.KEY_NONE);
    }

    @Override
    protected void onEnable() {
        placed = 0;
        offGroundTicks = 0;
        polarSneak = polarState = false;
        blockPos = null;
        facing = null;
        targetBox = null;
        timerStart = System.currentTimeMillis();
        EntityPlayerSP player = minecraft.thePlayer;
        owner = player;
        if (player == null) return;
        targetY = MathHelper.floor_double(player.posY - 1.0D);
        MoveFixModule fix = moveFix();
        scaffoldYaw = mode.is("Normal") ? player.rotationYaw + 180.0F : player.rotationYaw;
        scaffoldPitch = fix == null ? player.rotationPitch : fix.getRotationPitch();
        applySprint(player);
    }

    @Override
    protected void onDisable() {
        releaseRotation();
        restoreSwap();
        EntityPlayerSP player = minecraft.thePlayer;
        if (player != null && player == owner && restoreSlot >= 0 && restoreSlot < 9) {
            player.inventory.currentItem = restoreSlot;
        }
        restoreSlot = -1;
        // The next syncCurrentPlayItem returns the server to the visible slot.
        serverSlot = blockSlot = -1;
        if (minecraft.gameSettings != null) {
            if (sneakOwned) VanillaClicks.restore(minecraft.gameSettings.keyBindSneak);
            if (sprintOwned) VanillaClicks.restore(minecraft.gameSettings.keyBindSprint);
        }
        sneakOwned = sprintOwned = false;
        setTowerTimer(false);
        polarSneak = polarState = false;
        blockPos = null;
        facing = null;
        targetBox = null;
    }

    /** Runs at client tick start: selects the block, its face and this tick's server rotation. */
    public void tickStart() {
        EntityPlayerSP player = minecraft.thePlayer;
        if (player != owner) {
            // A respawn or world change starts from the new player's state.
            releaseRotation();
            owner = player;
            serverSlot = blockSlot = swappedSlot = restoreSlot = -1;
            blockPos = null;
            facing = null;
            targetBox = null;
            placed = offGroundTicks = 0;
            if (player != null) targetY = MathHelper.floor_double(player.posY - 1.0D);
        }
        if (!isEnabled() || player == null || minecraft.theWorld == null || minecraft.playerController == null) return;
        MoveFixModule fix = moveFix();
        blockSlot = findBlockSlot(player.inventory, serverSlot >= 0 ? serverSlot : player.inventory.currentItem);
        serverSlot = spoofSlot.isEnabled() ? blockSlot : -1;
        if (blockSlot < 0 || fix == null) {
            releaseRotation();
            return;
        }
        processBlockData(player);
        updateRotations(player, fix);
    }

    /** Fallback for ticks where the renderer hook did not run. */
    public void tickEnd() {
        restoreSwap();
    }

    private void releaseRotation() {
        MoveFixModule fix = moveFix();
        if (fix != null && rotating) fix.clearFakeRotation(getId());
        rotating = false;
    }

    // ----------------------------------------------------------------------------------------- target

    private void processBlockData(EntityPlayerSP player) {
        boolean jumpKey = minecraft.gameSettings.keyBindJump.isKeyDown();
        if (!keepY.isEnabled() || (!mode.is("Telly") && (jumpEnabled() || rotations.is("Polar"))) || jumpKey) {
            targetY = MathHelper.floor_double(player.posY - 1.0D);
        }
        int currentY = targetY;
        if (!mode.is("Telly") && rotations.is("Polar") && !jumpKey && !player.onGround
                && isValidBlock(new BlockPos(player.posX, targetY + 1, player.posZ))) {
            currentY = targetY + 1;
        }
        blockPos = findSupport(player, player.posX, currentY, player.posZ);
        if (blockPos != null) facing = findFace(player, player.posX, currentY, player.posZ);
        targetBox = blockPos == null ? null : bounds(blockPos);
    }

    private AxisAlignedBB bounds(BlockPos pos) {
        Block block = minecraft.theWorld.getBlockState(pos).getBlock();
        block.setBlockBoundsBasedOnState(minecraft.theWorld, pos);
        return new AxisAlignedBB(pos.getX() + block.getBlockBoundsMinX(), pos.getY() + block.getBlockBoundsMinY(),
                pos.getZ() + block.getBlockBoundsMinZ(), pos.getX() + block.getBlockBoundsMaxX(),
                pos.getY() + block.getBlockBoundsMaxY(), pos.getZ() + block.getBlockBoundsMaxZ());
    }

    /** The existing block whose top is closest to the player's feet, searched 5 blocks around. */
    private BlockPos findSupport(EntityPlayerSP player, double posX, int posY, double posZ) {
        BlockPos origin = new BlockPos(posX, posY, posZ);
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int y = origin.getY() - 1; y <= origin.getY(); y++) {
            for (int x = origin.getX() - 5; x <= origin.getX() + 5; x++) {
                for (int z = origin.getZ() - 5; z <= origin.getZ() + 5; z++) {
                    BlockPos candidate = new BlockPos(x, y, z);
                    if (!isValidBlock(candidate)) continue;
                    Block block = minecraft.theWorld.getBlockState(candidate).getBlock();
                    double distance = distance(posX, player.posY, posZ,
                            MathHelper.clamp_double(posX, x, x + block.getBlockBoundsMaxX()),
                            MathHelper.clamp_double(posY + 1.0D, y, y + block.getBlockBoundsMaxY()),
                            MathHelper.clamp_double(posZ, z, z + block.getBlockBoundsMaxZ()));
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = candidate;
                    }
                }
            }
        }
        return best;
    }

    /** The free side of the support block nearest the player's feet. */
    private EnumFacing findFace(EntityPlayerSP player, double posX, int posY, double posZ) {
        BlockPos feet = new BlockPos(posX, posY + 1.0D, posZ);
        boolean holdHeight = keepY.isEnabled() && (mode.is("Telly") || (!jumpEnabled() && !rotations.is("Polar")))
                && !minecraft.gameSettings.keyBindJump.isKeyDown();
        EnumFacing best = null;
        double bestDistance = Double.MAX_VALUE;
        for (EnumFacing side : new EnumFacing[] {EnumFacing.UP, EnumFacing.EAST, EnumFacing.WEST, EnumFacing.SOUTH, EnumFacing.NORTH}) {
            BlockPos neighbour = blockPos.offset(side);
            if (isPosSolid(neighbour) || neighbour.equals(feet)) continue;
            // Placing on top is only for towering or jumping without Keep Y.
            if (side == EnumFacing.UP && (holdHeight || player.onGround)) continue;
            Block block = minecraft.theWorld.getBlockState(neighbour).getBlock();
            double distance = distance(posX, player.posY, posZ,
                    MathHelper.clamp_double(posX, neighbour.getX(), neighbour.getX() + block.getBlockBoundsMaxX()),
                    MathHelper.clamp_double(player.posY, neighbour.getY(), neighbour.getY() + block.getBlockBoundsMaxY()),
                    MathHelper.clamp_double(posZ, neighbour.getZ(), neighbour.getZ() + block.getBlockBoundsMaxZ()));
            if (distance < bestDistance) {
                bestDistance = distance;
                best = side;
            }
        }
        return best;
    }

    private boolean isPosSolid(BlockPos pos) {
        Block block = minecraft.theWorld.getBlockState(pos).getBlock();
        return (block.getMaterial().isSolid() || !block.isTranslucent() || block instanceof BlockLadder
                || block instanceof BlockCarpet || block instanceof BlockSnow || block instanceof BlockSkull)
                && !block.getMaterial().isLiquid() && !(block instanceof BlockContainer);
    }

    private boolean isValidBlock(BlockPos pos) {
        Block block = minecraft.theWorld.getBlockState(pos).getBlock();
        return !(block instanceof BlockLiquid) && !(block instanceof BlockAir)
                && !(block instanceof BlockChest) && !(block instanceof BlockFurnace);
    }

    // -------------------------------------------------------------------------------------- rotations

    private void updateRotations(EntityPlayerSP player, MoveFixModule fix) {
        float cameraYaw = player.rotationYaw;
        float smooth = smoothSpeed.getFloat();
        boolean moving = isMoving();
        boolean instant = false;
        float yaw;
        float pitch;
        float yawSpeed;
        float pitchSpeed;
        String correction;
        if (mode.is("Normal")) {
            if (moving && player.hurtTime == 0) {
                scaffoldYaw = movementYaw(cameraYaw - (sideways.isEnabled() && !goingDiagonally(cameraYaw) ? 135.0F : 180.0F));
                scaffoldPitch = 76.0F;
            } else {
                scaffoldYaw = fix.getRotationYaw();
            }
            if (rotations.is("Intave")) {
                if (hasTarget()) {
                    scaffoldPitch = yawBasedPitch(player, scaffoldYaw, scaffoldPitch, 84);
                    if (lookingAt(player, scaffoldYaw, scaffoldPitch, true)) {
                        instant = true;
                    } else if (!lookingAt(player, scaffoldYaw, scaffoldPitch, false)) {
                        instant = turnToFace(player, directionToBlock(player)[0]);
                    }
                }
                yaw = scaffoldYaw;
                pitch = scaffoldPitch;
                yawSpeed = instant ? 180.0F : smooth;
                pitchSpeed = instant ? 180.0F : smooth / 2.0F;
            } else {
                // Polar and God Bridge hold a snapped diagonal behind the camera.
                yaw = Math.round((cameraYaw - (goingDiagonally(cameraYaw) ? 180.0F : 135.0F)) / 45.0F) * 45.0F;
                if (hasTarget() && !lookingAt(player, yaw, scaffoldPitch, false)) {
                    scaffoldPitch = yawBasedPitch(player, yaw, scaffoldPitch, 80);
                }
                pitch = scaffoldPitch;
                yawSpeed = smooth;
                pitchSpeed = smooth / 2.0F;
            }
            correction = movementFix.isEnabled() ? enabledCorrection(fix) : "Off";
        } else {
            if (player.hurtTime == 0 && player.onGround) scaffoldYaw = movementYaw(cameraYaw);
            if (player.onGround && moving) {
                // Walk and sprint normally; the block is placed after the jump.
                scaffoldYaw = cameraYaw;
                yawSpeed = pitchSpeed = 180.0F;
            } else {
                if (hasTarget()) {
                    float[] toBlock = directionToBlock(player);
                    scaffoldYaw = toBlock[0];
                    scaffoldPitch = yawBasedPitch(player, scaffoldYaw, toBlock[1], 82);
                }
                yawSpeed = player.onGround ? 180.0F : offGroundTicks < 2 ? 120.0F : 40.0F;
                pitchSpeed = 90.0F;
            }
            yaw = scaffoldYaw;
            pitch = scaffoldPitch;
            correction = enabledCorrection(fix);
        }
        float[] next = smoothRotation(fix.getRotationYaw(), fix.getRotationPitch(), yaw, pitch, yawSpeed, pitchSpeed,
                sensitivity(), random);
        fix.setFakeRotation(getId(), next[0], next[1], correction);
        rotating = true;
    }

    /** MoveFix's configured correction; Silent when MoveFix itself does not correct movement. */
    private static String enabledCorrection(MoveFixModule fix) {
        return fix.getCorrectMovement().is("Off") ? "Silent" : fix.getCorrectMovement().getValue();
    }

    /**
     * One tick of mouse-like turning: limited per-axis speed with a little
     * noise, then snapped to the sensitivity's smallest mouse step.
     */
    static float[] smoothRotation(float lastYaw, float lastPitch, float targetYaw, float targetPitch,
            float yawSpeed, float pitchSpeed, float sensitivity, Random random) {
        float yawNoise = targetPitch != lastPitch ? (float) ((random.nextDouble() - random.nextDouble()) / 3.0D) : 0.0F;
        float pitchNoise = RotationMath.difference(targetYaw, lastYaw) != 0.0F
                ? (float) ((random.nextDouble() - random.nextDouble()) / 3.0D) : 0.0F;
        float yawLimit = Math.max(0.0F, yawSpeed + (float) ((random.nextDouble() - random.nextDouble()) * 3.0D));
        float pitchLimit = Math.max(0.0F, pitchSpeed + (float) ((random.nextDouble() - random.nextDouble()) * 3.0D));
        float deltaYaw = RotationMath.clamp(RotationMath.difference(targetYaw + yawNoise, lastYaw), -yawLimit, yawLimit);
        float deltaPitch = RotationMath.clamp(RotationMath.clamp(targetPitch + pitchNoise, -90.0F, 90.0F) - lastPitch,
                -pitchLimit, pitchLimit);
        float factor = sensitivity * 0.6F + 0.2F;
        float step = factor * factor * factor * 1.2F;
        deltaYaw -= deltaYaw % step;
        deltaPitch -= deltaPitch % step;
        return new float[] {lastYaw + deltaYaw, RotationMath.clamp(lastPitch + deltaPitch, -90.0F, 90.0F)};
    }

    /**
     * Turns from the bridging yaw toward the face in small steps until some
     * pitch reaches it. The first reachable yaw lies on the edge of the face,
     * where mouse-step rounding would miss, so a few further reachable steps
     * are collected and the middle one is used.
     */
    private boolean turnToFace(EntityPlayerSP player, float yawToBlock) {
        float yaw = scaffoldYaw;
        float pitch = scaffoldPitch;
        List<float[]> reachable = new ArrayList<float[]>();
        for (int step = 0; step <= 100; step++) {
            float turn = 1.8F + random.nextFloat() / 10.0F;
            float delta = RotationMath.clamp(RotationMath.difference(yawToBlock, yaw), -turn, turn);
            yaw += delta;
            pitch = yawBasedPitch(player, yaw, pitch, 84);
            if (lookingAt(player, yaw, pitch, true)) {
                reachable.add(new float[] {yaw, pitch});
                if (reachable.size() == 5) break;
            } else if (!reachable.isEmpty()) {
                break;
            }
            if (delta == 0.0F) break;
        }
        if (reachable.isEmpty()) {
            scaffoldYaw = yaw;
            scaffoldPitch = pitch;
            return false;
        }
        float[] middle = reachable.get(reachable.size() / 2);
        scaffoldYaw = middle[0];
        scaffoldPitch = centredPitch(player, middle[0], 84, middle[1]);
        return true;
    }

    /**
     * Pitch between 70 degrees and the limit whose ray hits the chosen face,
     * or the last pitch when it still does. The middle of the first hitting
     * range is used so noise and mouse-step rounding stay on the face.
     */
    private float yawBasedPitch(EntityPlayerSP player, float yaw, float lastPitch, int maxPitch) {
        return lookingAt(player, yaw, lastPitch, true) ? lastPitch : centredPitch(player, yaw, maxPitch, lastPitch);
    }

    private float centredPitch(EntityPlayerSP player, float yaw, int maxPitch, float fallback) {
        int first = -1;
        int last = -1;
        for (int tenth = 700; tenth <= maxPitch * 10; tenth++) {
            if (lookingAt(player, yaw, tenth / 10.0F, true)) {
                if (first < 0) first = tenth;
                last = tenth;
            } else if (first >= 0) {
                break;
            }
        }
        return first < 0 ? fallback : (first + last) / 20.0F;
    }

    private boolean lookingAt(EntityPlayerSP player, float yaw, float pitch, boolean strict) {
        if (!hasTarget() || targetBox == null) return false;
        Vec3 eyes = player.getPositionEyes(1.0F);
        Vec3 look = lookVector(yaw, pitch);
        double reach = minecraft.playerController.getBlockReachDistance();
        // The ray must enter the target through the face before a world trace
        // can confirm nothing obstructs it; most scanned pitches stop here.
        EnumFacing entry = entryFace(eyes, look, reach, targetBox);
        if (entry == null || (strict && entry != facing)) return false;
        MovingObjectPosition hit = rayTrace(eyes, look, reach);
        return hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                && blockPos.equals(hit.getBlockPos()) && (!strict || hit.sideHit == facing);
    }

    private MovingObjectPosition rayTrace(Vec3 eyes, Vec3 look, double reach) {
        return minecraft.theWorld.rayTraceBlocks(eyes,
                eyes.addVector(look.xCoord * reach, look.yCoord * reach, look.zCoord * reach), false, false, true);
    }

    /** The block ray from the rotation MoveFix sends this tick. */
    private MovingObjectPosition serverRayTrace(EntityPlayerSP player, MoveFixModule fix) {
        return rayTrace(player.getPositionEyes(1.0F), lookVector(fix.getRotationYaw(), fix.getRotationPitch()),
                minecraft.playerController.getBlockReachDistance());
    }

    /**
     * The face through which a unit-length ray enters {@code box} within
     * {@code length}, or null when it misses or starts inside (slab test).
     */
    static EnumFacing entryFace(Vec3 origin, Vec3 direction, double length, AxisAlignedBB box) {
        double[] range = {0.0D, length};
        EnumFacing[] entry = {null};
        return slab(origin.xCoord, direction.xCoord, box.minX, box.maxX, EnumFacing.WEST, EnumFacing.EAST, range, entry)
                && slab(origin.yCoord, direction.yCoord, box.minY, box.maxY, EnumFacing.DOWN, EnumFacing.UP, range, entry)
                && slab(origin.zCoord, direction.zCoord, box.minZ, box.maxZ, EnumFacing.NORTH, EnumFacing.SOUTH, range, entry)
                ? entry[0] : null;
    }

    private static boolean slab(double start, double direction, double min, double max,
            EnumFacing minFace, EnumFacing maxFace, double[] range, EnumFacing[] entry) {
        if (Math.abs(direction) < 1.0E-9D) return start >= min && start <= max;
        double first = (min - start) / direction;
        double second = (max - start) / direction;
        double enter = Math.min(first, second);
        if (enter > range[0]) {
            range[0] = enter;
            entry[0] = direction > 0.0D ? minFace : maxFace;
        }
        range[1] = Math.min(range[1], Math.max(first, second));
        return range[0] <= range[1];
    }

    /** Vanilla's getVectorForRotation, including its sine table. */
    static Vec3 lookVector(float yaw, float pitch) {
        float yawCos = MathHelper.cos(-yaw * 0.017453292F - (float) Math.PI);
        float yawSin = MathHelper.sin(-yaw * 0.017453292F - (float) Math.PI);
        float pitchCos = -MathHelper.cos(-pitch * 0.017453292F);
        float pitchSin = MathHelper.sin(-pitch * 0.017453292F);
        return new Vec3(yawSin * pitchCos, pitchSin, yawCos * pitchCos);
    }

    /** Rotation toward the centre of the chosen face, aimed from 1.2 blocks above the feet. */
    private float[] directionToBlock(EntityPlayerSP player) {
        double x = blockPos.getX() + 0.5D + facing.getFrontOffsetX() * 0.5D;
        double y = blockPos.getY() + 0.5D + facing.getFrontOffsetY() * 0.5D;
        double z = blockPos.getZ() + 0.5D + facing.getFrontOffsetZ() * 0.5D;
        double deltaX = x - player.posX;
        double deltaY = y - player.posY - 1.2D;
        double deltaZ = z - player.posZ;
        double horizontal = MathHelper.sqrt_double(deltaX * deltaX + deltaZ * deltaZ);
        return new float[] {(float) (Math.atan2(deltaZ, deltaX) * 180.0D / Math.PI) - 90.0F,
                (float) -(Math.atan2(deltaY, horizontal) * 180.0D / Math.PI)};
    }

    /** Yaw of the pressed movement keys relative to {@code yaw}. */
    private float movementYaw(float yaw) {
        GameKeys keys = new GameKeys(minecraft);
        return movementYaw(yaw, keys.forward, keys.back, keys.left, keys.right);
    }

    static float movementYaw(float yaw, boolean forwardKey, boolean backKey, boolean leftKey, boolean rightKey) {
        float result = yaw;
        if (!forwardKey && backKey) result += 180.0F;
        float forward = 1.0F;
        if (!forwardKey && backKey) forward = -0.5F;
        else if (forwardKey && !backKey) forward = 0.5F;
        if (leftKey && !rightKey) result -= 90.0F * forward;
        if (!leftKey && rightKey) result += 90.0F * forward;
        return result;
    }

    static boolean goingDiagonally(float yaw) {
        float wrapped = (yaw % 360.0F + 360.0F) % 360.0F;
        for (float diagonal : new float[] {45.0F, 135.0F, 225.0F, 315.0F}) {
            if (Math.abs(wrapped - diagonal) < 10.0F || Math.abs(wrapped - (diagonal + 360.0F)) < 10.0F) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------------------------ move input

    /** Edits the finished (and MoveFix-corrected) movement input for this tick. */
    public void applyMoveInput(MovementInput input) {
        EntityPlayerSP player = minecraft.thePlayer;
        if (!isEnabled() || input == null || player == null || player != owner || minecraft.theWorld == null) return;
        boolean keyForward = input.moveForward > 0.0F;
        boolean keyBack = input.moveForward < 0.0F;
        boolean keyLeft = input.moveStrafe > 0.0F;
        boolean keyRight = input.moveStrafe < 0.0F;
        boolean keyJump = input.jump;
        boolean keySneak = input.sneak;
        boolean originalLeft = keyLeft;
        boolean normal = mode.is("Normal");
        boolean moving = isMoving();
        float cameraYaw = player.rotationYaw;
        boolean diagonal = goingDiagonally(cameraYaw);
        boolean jumpKey = minecraft.gameSettings.keyBindJump.isKeyDown();

        if (!moving || keyJump || keySneak || (rotations.is("Polar") && diagonal)) placed = 0;
        if (normal && rotations.is("Polar") && wasSneaking && !keySneak) {
            player.motionX = 0.0D;
            player.motionZ = 0.0D;
        }
        applySprint(player);
        if (moving && player.onGround && !jumpKey) {
            if (jumpEnabled() || mode.is("Telly")) {
                keyJump = true;
            } else if (normal && rotations.is("Polar") && placed >= 7) {
                keyJump = true;
                placed = 0;
            }
        }
        if (normal && sneak.isEnabled() && System.currentTimeMillis() - timerStart >= sneakDelay.getInt()) {
            keySneak = true;
            timerStart = System.currentTimeMillis();
        }
        if (normal && safeWalk.isEnabled() && player.onGround && minecraft.theWorld.getCollidingBoundingBoxes(player,
                player.getEntityBoundingBox().addCoord(player.motionX, player.motionY, player.motionZ)
                        .expand(-0.175D, 0.0D, -0.175D)).isEmpty()) {
            keySneak = true;
        }
        if (normal && (rotations.is("Polar") || rotations.is("God Bridge"))) {
            if (diagonal || jumpKey) {
                if (sneakOwned) setSneakKey(false);
            } else {
                double movingYaw = Math.toRadians(Math.round((cameraYaw - 135.0F) / 45.0F) * 45.0F);
                boolean rightSide = Math.floor(player.posX + Math.cos(movingYaw) * GOD_BRIDGE_STEP) != Math.floor(player.posX)
                        || Math.floor(player.posZ + Math.sin(movingYaw) * GOD_BRIDGE_STEP) != Math.floor(player.posZ);
                keyLeft = !rightSide && keyBack;
                if (!polarSneak && !rightSide) {
                    setSneakKey(true);
                    polarState = true;
                }
                if (polarState && rightSide) {
                    if (System.currentTimeMillis() - timerStart < 2000L) {
                        setSneakKey(true);
                    } else {
                        setSneakKey(false);
                        polarSneak = true;
                        polarState = false;
                        timerStart = System.currentTimeMillis();
                    }
                } else if (!rightSide) {
                    timerStart = System.currentTimeMillis();
                    polarSneak = false;
                }
            }
        } else if (sneakOwned) {
            setSneakKey(false);
        }
        wasSneaking = keySneak;
        if (keyLeft != originalLeft || keyJump != input.jump || keySneak != input.sneak) {
            float scale = keySneak ? 0.3F : 1.0F;
            input.moveForward = ((keyForward ? 1.0F : 0.0F) - (keyBack ? 1.0F : 0.0F)) * scale;
            input.moveStrafe = ((keyLeft ? 1.0F : 0.0F) - (keyRight ? 1.0F : 0.0F)) * scale;
            input.jump = keyJump;
            input.sneak = keySneak;
        }
    }

    private void applySprint(EntityPlayerSP player) {
        if (minecraft.gameSettings == null) return;
        if (sprint.is("Legit")) {
            MoveFixModule fix = moveFix();
            float serverYaw = fix == null ? player.rotationYaw : fix.getRotationYaw();
            boolean allowed = isMoving() && minecraft.gameSettings.keyBindForward.isKeyDown()
                    && Math.abs(RotationMath.difference(player.rotationYaw, serverYaw)) < 66.5F;
            setSprintKey(allowed);
            if (!allowed) player.setSprinting(false);
        } else if (sprint.is("Off")) {
            setSprintKey(false);
            player.setSprinting(false);
        } else if (isMoving()) {
            setSprintKey(true);
        }
    }

    private void setSprintKey(boolean down) {
        KeyBinding.setKeyBindState(minecraft.gameSettings.keyBindSprint.getKeyCode(), down);
        sprintOwned = true;
    }

    /** A synthetic sneak press never hides the player's own physical sneak key. */
    private void setSneakKey(boolean down) {
        KeyBinding binding = minecraft.gameSettings.keyBindSneak;
        KeyBinding.setKeyBindState(binding.getKeyCode(), down || VanillaClicks.physicallyDown(binding));
        sneakOwned = down;
    }

    // ----------------------------------------------------------------------------------------- placing

    /** Injected before vanilla handles attack/use clicks for this tick. */
    private void prepareInput() {
        EntityPlayerSP player = minecraft.thePlayer;
        MoveFixModule fix = moveFix();
        if (!isEnabled() || player == null || player != owner || fix == null || minecraft.theWorld == null
                || minecraft.playerController == null || minecraft.currentScreen != null || blockSlot < 0) {
            return;
        }
        if (serverSlot >= 0 && swappedSlot < 0) {
            // The server already holds the blocks; vanilla's clicks in this
            // pass use the same stack, and the visible slot returns before
            // the renderer samples the held item.
            swappedSlot = player.inventory.currentItem;
            player.inventory.currentItem = serverSlot;
        }
        setRightClickDelay(0);
        boolean wasPlaced = place(player, fix);
        if (dragClick.isEnabled() && !wasPlaced && blockPos != null && player.onGround
                && !minecraft.gameSettings.keyBindSneak.isKeyDown() && random.nextDouble() > 0.5D) {
            MovingObjectPosition hit = serverRayTrace(player, fix);
            if (hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK && blockPos.equals(hit.getBlockPos())) {
                rightClick(player, hit);
            }
        }
    }

    private boolean place(EntityPlayerSP player, MoveFixModule fix) {
        if (blockPos == null || facing == null) return false;
        if (mode.is("Normal") && (rotations.is("Polar") || rotations.is("God Bridge"))
                && minecraft.gameSettings.keyBindSneak.isKeyDown()) {
            return false;
        }
        if (serverSlot < 0 && player.inventory.currentItem != blockSlot) {
            if (restoreSlot < 0) restoreSlot = player.inventory.currentItem;
            player.inventory.currentItem = blockSlot;
        }
        MovingObjectPosition hit = serverRayTrace(player, fix);
        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK || !blockPos.equals(hit.getBlockPos())) {
            return false;
        }
        if (hit.sideHit == EnumFacing.UP && facing != EnumFacing.UP) return false;
        ItemStack held = player.getHeldItem();
        if (interactCancelled(player, hit)
                || !minecraft.playerController.onPlayerRightClick(player, minecraft.theWorld, held, hit.getBlockPos(), hit.sideHit, hit.hitVec)) {
            clearEmptyStack(player, held);
            return false;
        }
        clearEmptyStack(player, held);
        placed++;
        if (swing.isEnabled()) {
            player.swingItem();
        } else if (minecraft.getNetHandler() != null) {
            minecraft.getNetHandler().addToSendQueue(new C0APacketAnimation());
        }
        return true;
    }

    /** Vanilla rightClickMouse on a block hit: place, otherwise use the item. */
    private void rightClick(EntityPlayerSP player, MovingObjectPosition hit) {
        ItemStack held = player.getHeldItem();
        if (!interactCancelled(player, hit)
                && minecraft.playerController.onPlayerRightClick(player, minecraft.theWorld, held, hit.getBlockPos(), hit.sideHit, hit.hitVec)) {
            player.swingItem();
            clearEmptyStack(player, held);
            return;
        }
        clearEmptyStack(player, held);
        ItemStack current = player.inventory.getCurrentItem();
        if (current != null) minecraft.playerController.sendUseItem(player, minecraft.theWorld, current);
    }

    /** Forge posts this from rightClickMouse, not from the controller; listeners may cancel it. */
    private boolean interactCancelled(EntityPlayerSP player, MovingObjectPosition hit) {
        return ForgeEventFactory.onPlayerInteract(player, PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK, minecraft.theWorld,
                hit.getBlockPos(), hit.sideHit, hit.hitVec).isCanceled();
    }

    private static void clearEmptyStack(EntityPlayerSP player, ItemStack held) {
        if (held != null && held.stackSize == 0 && player.inventory.getCurrentItem() == held) {
            player.inventory.mainInventory[player.inventory.currentItem] = null;
        }
    }

    private void restoreSwap() {
        if (swappedSlot >= 0 && minecraft.thePlayer != null && minecraft.thePlayer == owner) {
            minecraft.thePlayer.inventory.currentItem = swappedSlot;
        }
        swappedSlot = -1;
    }

    private void setRightClickDelay(int delay) {
        try {
            if (rightClickDelay == null) {
                for (String name : new String[] {"rightClickDelayTimer", "field_71467_ac"}) {
                    try {
                        Field field = Minecraft.class.getDeclaredField(name);
                        field.setAccessible(true);
                        rightClickDelay = field;
                        break;
                    } catch (NoSuchFieldException ignored) {
                        // Try the other mapping.
                    }
                }
            }
            if (rightClickDelay != null) rightClickDelay.setInt(minecraft, delay);
        } catch (IllegalAccessException ignored) {
            // Vanilla keeps its own delay; Scaffold places directly anyway.
        }
    }

    // ------------------------------------------------------------------------------------------- tower

    /** Runs before the walking packet, where the tower may still adjust this tick's position. */
    public void beforeWalkingUpdate(Object entity) {
        EntityPlayerSP player = minecraft.thePlayer;
        if (!isEnabled() || player == null || entity != player || player != owner) return;
        offGroundTicks = player.onGround ? 0 : offGroundTicks + 1;
        boolean jumping = minecraft.gameSettings.keyBindJump.isKeyDown();
        if (tower.is("NCP") && jumping) {
            double fraction = player.posY % 1.0D;
            if (fraction <= 0.00153598D) {
                player.setPosition(player.posX, Math.floor(player.posY), player.posZ);
                player.motionY = 0.41998D;
            } else if (fraction < 0.1D && player.onGround) {
                player.setPosition(player.posX, Math.floor(player.posY), player.posZ);
            }
        }
        setTowerTimer(tower.is("Timer") && jumping);
    }

    private void setTowerTimer(boolean fast) {
        if (fast) {
            CombatTimerAccess.setSpeed(1.25F);
            timerOwned = true;
        } else if (timerOwned) {
            CombatTimerAccess.setSpeed(1.0F);
            timerOwned = false;
        }
    }

    private float jumpMotion(float motion) {
        return isEnabled() && tower.is("Intave") && minecraft.gameSettings.keyBindJump.isKeyDown() ? 0.41F : motion;
    }

    // ----------------------------------------------------------------------------------------- blocks

    /** Prefers the slot already in use, then the largest stack of placeable blocks in the hotbar. */
    static int findBlockSlot(InventoryPlayer inventory, int preferred) {
        if (preferred >= 0 && preferred < 9) {
            ItemStack stack = inventory.mainInventory[preferred];
            if (isPlaceable(stack) && stack.stackSize > 1) return preferred;
        }
        int best = -1;
        int largest = 0;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = inventory.mainInventory[slot];
            if (!isPlaceable(stack) || stack.stackSize <= largest) continue;
            largest = stack.stackSize;
            best = slot;
        }
        return best;
    }

    static boolean isPlaceable(ItemStack stack) {
        return stack != null && stack.stackSize > 0 && stack.getItem() instanceof ItemBlock
                && !InvalidBlocks.BLOCKS.contains(((ItemBlock) stack.getItem()).getBlock());
    }

    /** Placeable blocks across the hotbar and main inventory. */
    public int getBlockCount() {
        EntityPlayerSP player = minecraft.thePlayer;
        if (player == null) return 0;
        int count = 0;
        for (ItemStack stack : player.inventory.mainInventory) {
            if (isPlaceable(stack)) count += stack.stackSize;
        }
        return count;
    }

    /** The stack Scaffold places from, for the block counter. */
    public ItemStack getBlockStack() {
        EntityPlayerSP player = minecraft.thePlayer;
        if (player == null) return null;
        int slot = blockSlot >= 0 ? blockSlot : findBlockSlot(player.inventory, player.inventory.currentItem);
        return slot < 0 ? null : player.inventory.mainInventory[slot];
    }

    public boolean shouldRenderCount() {
        return isEnabled() && renderCount.isEnabled() && minecraft.thePlayer != null;
    }

    /** True while Scaffold supplies the server rotation. */
    public boolean ownsRotation() {
        return isEnabled() && rotating;
    }

    public boolean hasSilentSlot() {
        return isEnabled() && serverSlot >= 0 && owner != null && owner == minecraft.thePlayer;
    }

    public int getServerSlot() {
        return serverSlot;
    }

    public BlockPos getTargetBlock() {
        return blockPos;
    }

    public EnumFacing getTargetFace() {
        return facing;
    }

    public ModeSetting getMode() {
        return mode;
    }

    public ModeSetting getRotations() {
        return rotations;
    }

    public ModeSetting getTower() {
        return tower;
    }

    private boolean hasTarget() {
        return blockPos != null && facing != null;
    }

    private boolean jumpEnabled() {
        return mode.is("Normal") && jump.isEnabled();
    }

    private boolean isMoving() {
        GameKeys keys = new GameKeys(minecraft);
        return keys.forward || keys.back || keys.left || keys.right;
    }

    private float sensitivity() {
        return minecraft.gameSettings == null ? 0.5F : minecraft.gameSettings.mouseSensitivity;
    }

    private static double distance(double x, double y, double z, double otherX, double otherY, double otherZ) {
        double deltaX = otherX - x;
        double deltaY = otherY - y;
        double deltaZ = otherZ - z;
        return Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);
    }

    private static MoveFixModule moveFix() {
        Vibe vibe = Vibe.getInstance();
        return vibe == null || vibe.getModuleManager() == null ? null : vibe.getModuleManager().getModule(MoveFixModule.class);
    }

    private static ScaffoldModule module() {
        Vibe vibe = Vibe.getInstance();
        return vibe == null || vibe.getModuleManager() == null ? null : vibe.getModuleManager().getModule(ScaffoldModule.class);
    }

    /** Scaffold owns the server rotation and slot, so rotation-driven combat helpers pause. */
    public static boolean isActive() {
        ScaffoldModule module = module();
        return module != null && module.isEnabled();
    }

    // ------------------------------------------------------------------------------------------- hooks

    public static void prepareInputHook() {
        ScaffoldModule module = module();
        if (module != null) module.prepareInput();
    }

    /** Injected into runTick immediately before the renderer's per-tick update. */
    public static void finishInputHook() {
        ScaffoldModule module = module();
        if (module != null) module.restoreSwap();
    }

    /** Injected after EntityLivingBase.getJumpUpwardsMotion() in jump(). */
    public static float jumpMotionHook(float motion, Object entity) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || entity == null || entity != mc.thePlayer) return motion;
        ScaffoldModule module = module();
        return module == null ? motion : module.jumpMotion(motion);
    }

    /** Server slot for PlayerControllerMP.syncCurrentPlayItem while Spoof Slot is active. */
    public static int serverSlotHook(int vanilla) {
        ScaffoldModule module = module();
        return module != null && module.hasSilentSlot() ? module.serverSlot : vanilla;
    }

    /** Wrapped block/attack actions run with the stack the server holds. */
    public static int beginActionHook() {
        ScaffoldModule module = module();
        if (module == null || !module.hasSilentSlot()) return -1;
        int visible = module.owner.inventory.currentItem;
        module.owner.inventory.currentItem = module.serverSlot;
        return visible;
    }

    private static final class GameKeys {
        final boolean forward;
        final boolean back;
        final boolean left;
        final boolean right;

        GameKeys(Minecraft minecraft) {
            forward = VanillaClicks.physicallyDown(minecraft.gameSettings.keyBindForward);
            back = VanillaClicks.physicallyDown(minecraft.gameSettings.keyBindBack);
            left = VanillaClicks.physicallyDown(minecraft.gameSettings.keyBindLeft);
            right = VanillaClicks.physicallyDown(minecraft.gameSettings.keyBindRight);
        }
    }

    /** Loaded on first use so module construction never touches the block registry. */
    private static final class InvalidBlocks {
        static final Set<Block> BLOCKS = new HashSet<Block>(Arrays.asList(Blocks.enchanting_table, Blocks.carpet,
                Blocks.glass_pane, Blocks.ladder, Blocks.web, Blocks.stained_glass_pane, Blocks.iron_bars, Blocks.air,
                Blocks.water, Blocks.flowing_water, Blocks.lava, Blocks.flowing_lava, Blocks.soul_sand, Blocks.ice,
                Blocks.sand, Blocks.snow_layer, Blocks.chest, Blocks.ender_chest, Blocks.trapped_chest, Blocks.torch,
                Blocks.anvil, Blocks.noteblock, Blocks.jukebox, Blocks.wooden_pressure_plate, Blocks.stone_pressure_plate,
                Blocks.light_weighted_pressure_plate, Blocks.heavy_weighted_pressure_plate, Blocks.stone_button,
                Blocks.wooden_button, Blocks.tnt, Blocks.lever, Blocks.crafting_table, Blocks.furnace, Blocks.stone_slab,
                Blocks.wooden_slab, Blocks.stone_slab2, Blocks.brown_mushroom, Blocks.red_mushroom, Blocks.gold_block,
                Blocks.red_flower, Blocks.yellow_flower, Blocks.flower_pot));
    }
}

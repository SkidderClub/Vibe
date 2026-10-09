package dev.vibe.module.impl.world;

import dev.vibe.Vibe;
import dev.vibe.combat.AuraRotation;
import dev.vibe.combat.CombatTimerAccess;
import dev.vibe.combat.RotationMath;
import dev.vibe.input.VanillaClicks;
import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.module.impl.movement.MoveFixModule;
import dev.vibe.setting.BooleanSetting;
import dev.vibe.setting.ModeSetting;
import dev.vibe.setting.NumberSetting;
import dev.vibe.setting.RangeSetting;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashSet;
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
import net.minecraft.block.state.IBlockState;
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
    /** Pitch range searched for a ray that enters the chosen face. */
    private static final float MIN_PITCH = 55.0F;
    private static final float MAX_PITCH = 90.0F;
    /** Pitch held while the face is not yet in view, so the next hit is a short turn away. */
    private static final float PRE_AIM_MIN = 75.0F;
    private static final float PRE_AIM_MAX = 85.0F;
    /** Normal rotations never turn further than this from the bridging yaw, so they never look ahead. */
    private static final int MAX_TURN = 90;
    private static final int YAW_STEP = 2;
    /** Lateral distance from the block centre at which Sideways and GodBridge change the side they look past. */
    private static final double SIDE_SWITCH = 0.1D;
    /** A diagonal sneak held for the corner ends after this long, even when the corner was never reached. */
    private static final long CORNER_TIMEOUT = 1500L;
    private static final float TELLY_TURN_MIN = 140.0F;
    private static final float TELLY_TURN_MAX = 180.0F;
    private static final float TELLY_PRE_AIM = 80.0F;

    private final ModeSetting mode = addSetting(new ModeSetting("Mode", "Normal", "Normal", "Telly"));
    private final ModeSetting rotations = addSetting(new ModeSetting("Rotations", "Normal",
            () -> mode.is("Normal"), "Normal", "GodBridge", "Hypixel"));
    // The Hypixel rotation profile is RavenBS's long-Telly/Telly-B branch.
    // Its omitted Raven settings deliberately remain Vibe's existing scaffold
    // features rather than creating parallel controls.
    final ModeSetting hypixelTelly = addSetting(new ModeSetting("Telly", "Long",
            () -> mode.is("Normal") && rotations.is("Hypixel"), "Disabled", "Long"));
    final ModeSetting hypixelMultiPlace = addSetting(new ModeSetting("Multi-place", "Disabled",
            () -> mode.is("Normal") && rotations.is("Hypixel"), "Disabled", "2", "3"));
    final NumberSetting hypixelClickSpeed = addSetting(new NumberSetting("Click speed", 20.0D, 0.0D, 20.0D, 0.5D,
            () -> mode.is("Normal") && rotations.is("Hypixel")));
    final NumberSetting hypixelIceThreshold = addSetting(new NumberSetting("Ice threshold", -0.6D, -0.6D, 0.6D, 0.01D,
            () -> mode.is("Normal") && rotations.is("Hypixel")));
    final BooleanSetting hypixelTellyOnJump = addSetting(new BooleanSetting("Telly on jump", true,
            () -> mode.is("Normal") && rotations.is("Hypixel")));
    final ModeSetting hypixelKeepMode = addSetting(new ModeSetting("Keep Y Mode", "Telly B",
            () -> hypixelRotations(), "Disabled", "Telly B"));
    final BooleanSetting hypixelDisableJumpPotion = addSetting(new BooleanSetting("Disable on jump potion", false,
            () -> hypixelRotations()));
    final BooleanSetting hypixelKeepRmb = addSetting(new BooleanSetting("Keep Y on RMB", false,
            () -> hypixelRotations()));
    private final ModeSetting rotationMode = addSetting(new ModeSetting("Rotation Mode", "Normal",
            () -> mode.is("Normal") && !hypixelRotations(), "Normal", "Acceleration"));
    private final RangeSetting rotationSpeed = addSetting(new RangeSetting("Rotation Speed", 45.0D, 60.0D, 1.0D, 180.0D, 0.5D,
            () -> mode.is("Normal") && !hypixelRotations() && rotationMode.is("Normal")));
    private final RangeSetting rotationAcceleration = addSetting(new RangeSetting("Rotation Acceleration", 5.0D, 10.0D,
            0.25D, 40.0D, 0.25D, () -> mode.is("Normal") && !hypixelRotations() && rotationMode.is("Acceleration")));
    private final RangeSetting tellyTicks = addSetting(new RangeSetting("Telly Ticks", 2.0D, 3.0D, 0.0D, 8.0D, 1.0D,
            () -> mode.is("Telly")));
    private final BooleanSetting sideways = addSetting(new BooleanSetting("Sideways", false,
            () -> mode.is("Normal") && rotations.is("Normal")));
    private final ModeSetting sprint = addSetting(new ModeSetting("Sprint", "Always", "Always", "Off", "Legit"));
    private final ModeSetting tower = addSetting(new ModeSetting("Tower", "None", "None", "NCP", "Timer", "Intave"));
    private final BooleanSetting keepY = addSetting(new BooleanSetting("Keep Y", true, () -> !hypixelRotations()));
    private final BooleanSetting sneak = addSetting(new BooleanSetting("Sneak", false, () -> mode.is("Normal")));
    private final NumberSetting blockEndDistance = addSetting(new NumberSetting("Block End Distance", 0.1D, 0.0D, 0.6D, 0.01D,
            () -> mode.is("Normal") && sneak.isEnabled()));
    private final BooleanSetting randomizeUnsneak = addSetting(new BooleanSetting("Randomize Unsneak", true,
            () -> mode.is("Normal") && sneak.isEnabled()));
    private final RangeSetting unsneakRange = addSetting(new RangeSetting("Unsneak Range", 50.0D, 100.0D, 0.0D, 500.0D, 5.0D,
            () -> mode.is("Normal") && sneak.isEnabled() && randomizeUnsneak.isEnabled()));
    private final NumberSetting unsneakDelay = addSetting(new NumberSetting("Unsneak Delay", 50.0D, 0.0D, 500.0D, 5.0D,
            () -> mode.is("Normal") && sneak.isEnabled() && !randomizeUnsneak.isEnabled()));
    private final BooleanSetting preventDoubleSneak = addSetting(new BooleanSetting("Prevent Double Sneaking", true,
            () -> mode.is("Normal") && sneak.isEnabled()));
    private final BooleanSetting safeWalk = addSetting(new BooleanSetting("Safe Walk", true, () -> mode.is("Normal")));
    private final BooleanSetting movementFix = addSetting(new BooleanSetting("Move Fix", true, () -> mode.is("Normal") && !hypixelRotations()));
    private final BooleanSetting spoofSlot = addSetting(new BooleanSetting("Spoof Slot", true));
    private final BooleanSetting swing = addSetting(new BooleanSetting("Swing", false));
    private final BooleanSetting jump = addSetting(new BooleanSetting("Jump", false, () -> mode.is("Normal") && !hypixelRotations()));
    private final BooleanSetting dragClick = addSetting(new BooleanSetting("Drag Click", false));
    private final BooleanSetting renderCount = addSetting(new BooleanSetting("Render Count", false));

    private final Minecraft minecraft = Minecraft.getMinecraft();
    private final Random random = new Random();
    private final AuraRotation rotation = new AuraRotation();
    private EntityPlayerSP owner;
    private int targetY;
    private int offGroundTicks;
    private BlockPos blockPos;
    private EnumFacing facing;
    private AxisAlignedBB targetBox;
    /** Direction of travel relative to the camera, kept while the movement keys are released. */
    private float travelOffset;
    /** +1 looks back-left past the block, -1 back-right. */
    private int lookSide = 1;
    private boolean tellyTurned;
    private boolean tellyPlacing;
    private int tellyDelay;
    private final HypixelScaffold hypixel = new HypixelScaffold(this);
    private boolean wasHypixel;
    private boolean edgeSneaking;
    private long unsneakAt;
    private boolean cornerHold;
    private int cornerX;
    private int cornerZ;
    private long cornerSince;
    private int blockSlot = -1;
    /** Hotbar slot the server holds while Spoof Slot keeps the visible slot. */
    private int serverSlot = -1;
    /** Visible slot to restore once vanilla's click pass has finished. */
    private int swappedSlot = -1;
    /** Visible slot before Scaffold selected blocks without Spoof Slot. */
    private int restoreSlot = -1;
    private boolean rotating;
    private boolean sprintOwned;
    private boolean timerOwned;
    private static Field rightClickDelay;

    public ScaffoldModule() {
        super("Scaffold", "Places blocks beneath you while bridging and towering", Category.WORLD, Keyboard.KEY_NONE);
    }

    @Override
    protected void onEnable() {
        hypixel.reset();
        wasHypixel = hypixelRotations();
        offGroundTicks = 0;
        blockPos = null;
        facing = null;
        targetBox = null;
        travelOffset = 0.0F;
        lookSide = 1;
        tellyTurned = tellyPlacing = false;
        resetEdgeSneak();
        EntityPlayerSP player = minecraft.thePlayer;
        owner = player;
        if (player == null) return;
        targetY = MathHelper.floor_double(player.posY - 1.0D);
        applySprint(player);
    }

    @Override
    protected void onDisable() {
        releaseRotation();
        hypixel.reset();
        restoreSwap();
        EntityPlayerSP player = minecraft.thePlayer;
        if (player != null && player == owner && restoreSlot >= 0 && restoreSlot < 9) {
            player.inventory.currentItem = restoreSlot;
        }
        restoreSlot = -1;
        // The next syncCurrentPlayItem returns the server to the visible slot.
        serverSlot = blockSlot = -1;
        if (minecraft.gameSettings != null && sprintOwned) VanillaClicks.restore(minecraft.gameSettings.keyBindSprint);
        sprintOwned = false;
        setTowerTimer(false);
        tellyTurned = tellyPlacing = false;
        resetEdgeSneak();
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
            offGroundTicks = 0;
            tellyTurned = tellyPlacing = false;
            hypixel.reset();
            resetEdgeSneak();
            if (player != null) targetY = MathHelper.floor_double(player.posY - 1.0D);
        }
        if (!isEnabled() || player == null || minecraft.theWorld == null || minecraft.playerController == null) return;
        MoveFixModule fix = moveFix();
        if (wasHypixel != hypixelRotations()) {
            releaseRotation();
            hypixel.reset();
            wasHypixel = hypixelRotations();
        }
        blockSlot = findBlockSlot(player.inventory, serverSlot >= 0 ? serverSlot : player.inventory.currentItem);
        if (hypixelRotations()) {
            if (blockSlot < 0 || !hypixel.accepts(player.inventory.getStackInSlot(blockSlot))) {
                blockSlot = -1;
                int largest = 0;
                for (int slot = 0; slot < 9; slot++) {
                    ItemStack stack = player.inventory.getStackInSlot(slot);
                    if (hypixel.accepts(stack) && stack.stackSize > largest) {
                        blockSlot = slot;
                        largest = stack.stackSize;
                    }
                }
            }
            int preferred = hypixel.preferredIceSlot(player);
            if (preferred >= 0) blockSlot = preferred;
        }
        serverSlot = spoofSlot.isEnabled() ? blockSlot : -1;
        if (blockSlot < 0 || fix == null) {
            releaseRotation();
            return;
        }
        if (hypixelRotations()) return;
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
        if (!keepY.isEnabled() || jumpEnabled() || (minecraft.gameSettings.keyBindJump.isKeyDown())) {
            targetY = MathHelper.floor_double(player.posY - 1.0D);
        }
        blockPos = findSupport(player, player.posX, targetY, player.posZ);
        facing = blockPos == null ? null : findFace(player, player.posX, targetY, player.posZ);
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
        boolean holdHeight = keepY.isEnabled() && (mode.is("Telly") || !jumpEnabled())
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

    /** True when a block's collision box at {@code pos} lies under the point (x, z). */
    private boolean supports(BlockPos pos, double x, double z) {
        IBlockState state = minecraft.theWorld.getBlockState(pos);
        AxisAlignedBB box = state.getBlock().getCollisionBoundingBox(minecraft.theWorld, pos, state);
        return box != null && x >= box.minX && x <= box.maxX && z >= box.minZ && z <= box.maxZ;
    }

    // -------------------------------------------------------------------------------------- rotations

    private void updateRotations(EntityPlayerSP player, MoveFixModule fix) {
        float currentYaw = fix.getRotationYaw();
        float currentPitch = fix.getRotationPitch();
        // Continue from what MoveFix sends, keeping the acceleration momentum while nothing else moved it.
        if (!rotating || Math.abs(RotationMath.difference(rotation.getYaw(), currentYaw)) > 0.01F
                || Math.abs(rotation.getPitch() - currentPitch) > 0.01F) {
            rotation.reset(currentYaw, currentPitch);
        }
        float[] target;
        float speed;
        boolean acceleration = false;
        String correction;
        if (mode.is("Telly")) {
            target = tellyRotation(player, currentPitch);
            speed = TELLY_TURN_MIN + random.nextFloat() * (TELLY_TURN_MAX - TELLY_TURN_MIN);
            correction = enabledCorrection(fix);
        } else {
            target = rotations.is("GodBridge") ? godBridgeRotation(player, currentYaw, currentPitch)
                    : normalRotation(player, currentYaw, currentPitch);
            acceleration = rotationMode.is("Acceleration");
            speed = (float) sample(acceleration ? rotationAcceleration : rotationSpeed);
            correction = movementFix.isEnabled() ? enabledCorrection(fix) : "Off";
        }
        rotation.advance(target[0], target[1], speed, acceleration, sensitivity());
        fix.setFakeRotation(getId(), rotation.getYaw(), rotation.getPitch(), correction);
        rotating = true;
    }

    /** MoveFix's configured correction; Silent when MoveFix itself does not correct movement. */
    private static String enabledCorrection(MoveFixModule fix) {
        return fix.getCorrectMovement().is("Off") ? "Silent" : fix.getCorrectMovement().getValue();
    }

    /**
     * Looks straight back along the direction of travel, or with Sideways 45
     * degrees past whichever side of the block the player stands on. Diagonal
     * bridges always look straight back.
     */
    private float[] normalRotation(EntityPlayerSP player, float currentYaw, float currentPitch) {
        // Knockback keeps the yaw so the movement correction stays stable.
        if (player.hurtTime > 0) return aim(player, new float[] {currentYaw}, currentPitch, true);
        float travel = travelYaw(player);
        float back = travel + 180.0F;
        if (!sideways.isEnabled() || bridgingDiagonally(travel)) return aim(player, new float[] {back}, currentPitch, true);
        int side = lookSide(player, travel);
        float[] yaws = {back + side * 45.0F, back - side * 45.0F};
        float[] result = aim(player, yaws, currentPitch, true);
        if (result[0] == yaws[1]) lookSide = -side;
        return result;
    }

    /** Holds a yaw snapped to 45 degrees behind the player; only the pitch follows the face. */
    private float[] godBridgeRotation(EntityPlayerSP player, float currentYaw, float currentPitch) {
        if (player.hurtTime > 0) return aim(player, new float[] {currentYaw}, currentPitch, false);
        float travel = travelYaw(player);
        float back = travel + 180.0F;
        if (bridgingDiagonally(travel)) {
            return aim(player, new float[] {snap(back), snap(back + 45.0F), snap(back - 45.0F)}, currentPitch, false);
        }
        int side = lookSide(player, travel);
        float[] yaws = {snap(back + side * 45.0F), snap(back - side * 45.0F)};
        float[] result = aim(player, yaws, currentPitch, false);
        if (result[0] == yaws[1]) lookSide = -side;
        return result;
    }

    /**
     * Faces forward while running and for the first Telly Ticks of a jump,
     * then turns around and places until the rest of the jump is bridged.
     * A jump over existing blocks never turns around.
     */
    private float[] tellyRotation(EntityPlayerSP player, float currentPitch) {
        float camera = player.rotationYaw;
        if (player.onGround) {
            tellyTurned = tellyPlacing = false;
            tellyDelay = tellyTicks.getMinInt() + random.nextInt(tellyTicks.getMaxInt() - tellyTicks.getMinInt() + 1);
            return new float[] {camera, currentPitch};
        }
        boolean gap = gapUntilLanding(player);
        if (!tellyTurned && gap && (!isMoving() || offGroundTicks >= tellyDelay)) tellyTurned = true;
        tellyPlacing = tellyTurned && gap;
        if (!tellyPlacing) return new float[] {camera, currentPitch};
        Vec3 eyes = player.getPositionEyes(1.0F);
        if (hasTarget() && faceVisible(eyes)) return rotationToFace(eyes);
        return new float[] {movementYaw(camera) + 180.0F, TELLY_PRE_AIM};
    }

    /**
     * The first candidate yaw with a pitch whose ray hits the face. With
     * {@code search} the yaw may then turn up to 90 degrees from the first
     * candidate, never further, so the player never looks ahead. Without a
     * hit the first candidate is held at a pitch close to the next hit.
     */
    private float[] aim(EntityPlayerSP player, float[] yaws, float currentPitch, boolean search) {
        if (hasTarget() && faceVisible(player.getPositionEyes(1.0F))) {
            for (float yaw : yaws) {
                float pitch = hitPitch(player, yaw, currentPitch);
                if (!Float.isNaN(pitch)) return new float[] {yaw, pitch};
            }
            if (search) {
                float[] turned = nearestHit(player, yaws[0], currentPitch);
                if (turned != null) return turned;
            }
        }
        return new float[] {yaws[0], RotationMath.clamp(currentPitch, PRE_AIM_MIN, PRE_AIM_MAX)};
    }

    /**
     * Turns away from {@code baseYaw} in small steps until some pitch reaches
     * the face. The first reachable yaw lies on the edge of the face, where
     * mouse-step rounding would miss, so it continues while the face stays
     * reachable for two more steps.
     */
    private float[] nearestHit(EntityPlayerSP player, float baseYaw, float currentPitch) {
        for (int offset = YAW_STEP; offset <= MAX_TURN; offset += YAW_STEP) {
            for (int sign = -1; sign <= 1; sign += 2) {
                float pitch = hitPitch(player, baseYaw + sign * offset, currentPitch);
                if (Float.isNaN(pitch)) continue;
                int reached = offset;
                for (int extra = 1; extra <= 2 && reached + YAW_STEP <= MAX_TURN; extra++) {
                    float further = hitPitch(player, baseYaw + sign * (reached + YAW_STEP), pitch);
                    if (Float.isNaN(further)) break;
                    reached += YAW_STEP;
                    pitch = further;
                }
                return new float[] {baseYaw + sign * reached, pitch};
            }
        }
        return null;
    }

    /**
     * The current pitch while its ray still hits the face at {@code yaw},
     * otherwise the middle of the pitch range that hits it, so mouse-step
     * rounding stays on the face; NaN when no pitch reaches it.
     */
    private float hitPitch(EntityPlayerSP player, float yaw, float currentPitch) {
        if (currentPitch >= MIN_PITCH && lookingAt(player, yaw, currentPitch, true)) return currentPitch;
        Vec3 eyes = player.getPositionEyes(1.0F);
        double reach = minecraft.playerController.getBlockReachDistance();
        int first = -1;
        int last = -1;
        // Quarter-degree slab tests find the range; only its middle needs a world ray trace.
        for (int quarter = (int) (MIN_PITCH * 4.0F); quarter <= (int) (MAX_PITCH * 4.0F); quarter++) {
            if (entryFace(eyes, lookVector(yaw, quarter / 4.0F), reach, targetBox) == facing) {
                if (first < 0) first = quarter;
                last = quarter;
            } else if (first >= 0) {
                break;
            }
        }
        if (first < 0) return Float.NaN;
        float middle = (first + last) / 8.0F;
        return lookingAt(player, yaw, middle, true) ? middle : Float.NaN;
    }

    /** No ray can enter a face while the eyes are behind its plane. */
    private boolean faceVisible(Vec3 eyes) {
        switch (facing) {
            case EAST: return eyes.xCoord > targetBox.maxX;
            case WEST: return eyes.xCoord < targetBox.minX;
            case SOUTH: return eyes.zCoord > targetBox.maxZ;
            case NORTH: return eyes.zCoord < targetBox.minZ;
            case UP: return eyes.yCoord > targetBox.maxY;
            default: return eyes.yCoord < targetBox.minY;
        }
    }

    /** Rotation from the eyes toward the centre of the chosen face; it enters through that face when visible. */
    private float[] rotationToFace(Vec3 eyes) {
        double x = (targetBox.minX + targetBox.maxX) / 2.0D;
        double y = (targetBox.minY + targetBox.maxY) / 2.0D;
        double z = (targetBox.minZ + targetBox.maxZ) / 2.0D;
        switch (facing) {
            case EAST: x = targetBox.maxX; break;
            case WEST: x = targetBox.minX; break;
            case SOUTH: z = targetBox.maxZ; break;
            case NORTH: z = targetBox.minZ; break;
            case UP: y = targetBox.maxY; break;
            default: y = targetBox.minY; break;
        }
        double deltaX = x - eyes.xCoord;
        double deltaY = y - eyes.yCoord;
        double deltaZ = z - eyes.zCoord;
        double horizontal = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        return new float[] {(float) (Math.toDegrees(Math.atan2(deltaZ, deltaX)) - 90.0D),
                (float) -Math.toDegrees(Math.atan2(deltaY, horizontal))};
    }

    /**
     * Looking 45 degrees to one side only reaches the face from the other half
     * of the block: +1 (back-left) while the player stands right of the block
     * centre, -1 (back-right) while left of it, unchanged near the centre.
     */
    private int lookSide(EntityPlayerSP player, float travelYaw) {
        if (!hasTarget()) return lookSide;
        double radians = Math.toRadians(travelYaw);
        double lateral = (player.posX - (blockPos.getX() + 0.5D)) * -Math.cos(radians)
                + (player.posZ - (blockPos.getZ() + 0.5D)) * -Math.sin(radians);
        if (lateral > SIDE_SWITCH) lookSide = 1;
        else if (lateral < -SIDE_SWITCH) lookSide = -1;
        return lookSide;
    }

    /** Direction of travel from the movement keys; while they are released, the last one relative to the camera. */
    private float travelYaw(EntityPlayerSP player) {
        if (isMoving()) travelOffset = RotationMath.difference(movementYaw(player.rotationYaw), player.rotationYaw);
        return player.rotationYaw + travelOffset;
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

    /** Within 10 degrees of a diagonal. */
    static boolean goingDiagonally(float yaw) {
        float wrapped = (yaw % 360.0F + 360.0F) % 360.0F;
        for (float diagonal : new float[] {45.0F, 135.0F, 225.0F, 315.0F}) {
            if (Math.abs(wrapped - diagonal) < 10.0F || Math.abs(wrapped - (diagonal + 360.0F)) < 10.0F) return true;
        }
        return false;
    }

    /** Closer to a diagonal than to an axis, so the bridge becomes a staircase. */
    static boolean bridgingDiagonally(float yaw) {
        float withinQuarter = (yaw % 90.0F + 90.0F) % 90.0F;
        return Math.abs(withinQuarter - 45.0F) < 22.5F;
    }

    private static float snap(float yaw) {
        return Math.round(yaw / 45.0F) * 45.0F;
    }

    // ----------------------------------------------------------------------------------------- telly

    /** True when a column under the rest of this jump, up to the landing, has no block at the bridge height. */
    private boolean gapUntilLanding(EntityPlayerSP player) {
        double x = player.posX;
        double y = player.posY;
        double z = player.posZ;
        double motionY = player.motionY;
        double floor = targetY + 1.0D;
        for (int tick = 0; tick < 40; tick++) {
            if (!supports(new BlockPos(x, targetY, z), x, z)) return true;
            if (tick > 0 && y <= floor) return false;
            x += player.motionX;
            z += player.motionZ;
            y += motionY;
            motionY = (motionY - 0.08D) * 0.98D;
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
        boolean normal = mode.is("Normal");
        boolean moving = isMoving();
        boolean jumpKey = minecraft.gameSettings.keyBindJump.isKeyDown();

        applySprint(player);
        if (!hypixelRotations() && moving && player.onGround && !jumpKey && (jumpEnabled() || mode.is("Telly"))) keyJump = true;
        if (normal && edgeSneak(player, moving, keyJump)) keySneak = true;
        else if (!normal) resetEdgeSneak();
        if (normal && safeWalk.isEnabled() && player.onGround && minecraft.theWorld.getCollidingBoundingBoxes(player,
                player.getEntityBoundingBox().addCoord(player.motionX, player.motionY, player.motionZ)
                        .expand(-0.175D, 0.0D, -0.175D)).isEmpty()) {
            keySneak = true;
        }
        if (keyJump != input.jump || keySneak != input.sneak) {
            float scale = keySneak ? 0.3F : 1.0F;
            input.moveForward = ((keyForward ? 1.0F : 0.0F) - (keyBack ? 1.0F : 0.0F)) * scale;
            input.moveStrafe = ((keyLeft ? 1.0F : 0.0F) - (keyRight ? 1.0F : 0.0F)) * scale;
            input.jump = keyJump;
            input.sneak = keySneak;
        }
        if (hypixelRotations()) hypixel.afterInput();
    }

    /**
     * Sneaks only at an unsupported block end and releases after the unsneak
     * delay once a block supports the way ahead. With Prevent Double Sneaking
     * a diagonal walk keeps sneaking from the first edge until the player has
     * crossed into the block at the corner, instead of sneaking at each edge.
     */
    private boolean edgeSneak(EntityPlayerSP player, boolean moving, boolean jumping) {
        if (!sneak.isEnabled() || !moving || jumping || !player.onGround) {
            resetEdgeSneak();
            return false;
        }
        long now = System.currentTimeMillis();
        float travel = movementYaw(player.rotationYaw);
        boolean diagonal = goingDiagonally(travel);
        int column = MathHelper.floor_double(player.posX);
        int row = MathHelper.floor_double(player.posZ);
        if (atBlockEnd(player, travel)) {
            if (!edgeSneaking && preventDoubleSneak.isEnabled() && diagonal) {
                cornerHold = true;
                cornerX = column;
                cornerZ = row;
                cornerSince = now;
            }
            edgeSneaking = true;
            unsneakAt = 0L;
            return true;
        }
        if (!edgeSneaking) return false;
        if (cornerHold) {
            boolean pastCorner = column != cornerX && row != cornerZ;
            if (!pastCorner && diagonal && preventDoubleSneak.isEnabled() && now - cornerSince < CORNER_TIMEOUT) return true;
            cornerHold = false;
        }
        if (unsneakAt == 0L) unsneakAt = now + nextUnsneakDelay();
        if (now < unsneakAt) return true;
        resetEdgeSneak();
        return false;
    }

    /** True when the point Block End Distance ahead of next tick's position has nothing under it. */
    private boolean atBlockEnd(EntityPlayerSP player, float travelYaw) {
        double radians = Math.toRadians(travelYaw);
        double distance = blockEndDistance.getDouble();
        double x = player.posX + player.motionX - Math.sin(radians) * distance;
        double z = player.posZ + player.motionZ + Math.cos(radians) * distance;
        return !supports(new BlockPos(x, player.posY - 0.01D, z), x, z);
    }

    private long nextUnsneakDelay() {
        if (!randomizeUnsneak.isEnabled()) return unsneakDelay.getInt();
        int min = unsneakRange.getMinInt();
        int max = unsneakRange.getMaxInt();
        return min + (max == min ? 0 : random.nextInt(max - min + 1));
    }

    private void resetEdgeSneak() {
        edgeSneaking = cornerHold = false;
        unsneakAt = 0L;
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
        if (hypixelRotations()) return;
        boolean wasPlaced = place(player, fix);
        if (dragClick.isEnabled() && !wasPlaced && blockPos != null && player.onGround && !mode.is("Telly")
                && !minecraft.gameSettings.keyBindSneak.isKeyDown() && random.nextDouble() > 0.5D) {
            MovingObjectPosition hit = serverRayTrace(player, fix);
            // A drag click on the top would build a block behind the player.
            if (hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK && blockPos.equals(hit.getBlockPos())
                    && (hit.sideHit != EnumFacing.UP || facing == EnumFacing.UP)) {
                rightClick(player, hit);
            }
        }
    }

    private boolean place(EntityPlayerSP player, MoveFixModule fix) {
        if (blockPos == null || facing == null) return false;
        // Telly only places once it has turned around in the air.
        if (mode.is("Telly") && !tellyPlacing) return false;
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

    /** The RavenBS long-Telly/Telly-B profile is a rotation branch of Normal Scaffold. */

    /** Raven's PrePlayerInput runs before MoveFix transforms the keys. */
    public void beforeMoveInput(MovementInput input) {
        if (isEnabled() && hypixelRotations() && minecraft.thePlayer == owner) hypixel.beforeInput(input);
    }

    void sendHypixelRotations(float yaw, float pitch) {
        MoveFixModule fix = moveFix();
        if (fix != null) {
            fix.setFakeRotation(getId(), yaw, pitch, enabledCorrection(fix));
            rotating = true;
        }
    }
    Float hypixelServerYaw() { MoveFixModule fix = moveFix(); return fix == null ? null : fix.getRotationYaw(); }
    boolean hypixelMovementFix() { return moveFix() != null && rotating; }
    boolean hypixelSwing() { return swing.isEnabled(); }

    /** Matches Raven's EntityPlayerSP.onUpdate HEAD event, after the click pass. */
    public static void hypixelUpdateHook() {
        ScaffoldModule module = module();
        if (module == null || !module.isEnabled() || !module.hypixelRotations()) return;
        EntityPlayerSP player = module.minecraft.thePlayer;
        if (player == null || player != module.owner || module.minecraft.theWorld == null
                || module.minecraft.playerController == null || module.blockSlot < 0
                || !module.minecraft.theWorld.isBlockLoaded(new BlockPos(player.posX, 0.0D, player.posZ))) return;
        int visibleSlot = player.inventory.currentItem;
        if (!module.spoofSlot.isEnabled() && visibleSlot != module.blockSlot && module.restoreSlot < 0) module.restoreSlot = visibleSlot;
        player.inventory.currentItem = module.blockSlot;
        try { module.hypixel.update(); }
        finally { if (module.spoofSlot.isEnabled()) player.inventory.currentItem = visibleSlot; }
    }
    public static boolean hypixelPacketHook(Object packet) {
        ScaffoldModule module = module();
        return module == null || !module.isEnabled() || !module.hypixelRotations()
                || !(packet instanceof net.minecraft.network.Packet)
                || module.hypixel.permitsPacket((net.minecraft.network.Packet<?>) packet);
    }
    public void onHypixelMouse(net.minecraftforge.client.event.MouseEvent event) {
        if (isEnabled() && hypixelRotations() && !hypixel.permitsMouse(event.button)) event.setCanceled(true);
    }

    private boolean hypixelRotations() {
        return mode.is("Normal") && rotations.is("Hypixel");
    }

    private boolean isMoving() {
        GameKeys keys = new GameKeys(minecraft);
        return keys.forward || keys.back || keys.left || keys.right;
    }

    private float sensitivity() {
        return minecraft.gameSettings == null ? 0.5F : minecraft.gameSettings.mouseSensitivity;
    }

    private double sample(RangeSetting setting) {
        return setting.getMin() + random.nextDouble() * (setting.getMax() - setting.getMin());
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
            forward = down(minecraft.gameSettings.keyBindForward);
            back = down(minecraft.gameSettings.keyBindBack);
            left = down(minecraft.gameSettings.keyBindLeft);
            right = down(minecraft.gameSettings.keyBindRight);
        }

        /** The physical key; without a native keyboard (unit tests) the binding's own state. */
        private static boolean down(KeyBinding binding) {
            return Keyboard.isCreated() ? VanillaClicks.physicallyDown(binding) : binding.isKeyDown();
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

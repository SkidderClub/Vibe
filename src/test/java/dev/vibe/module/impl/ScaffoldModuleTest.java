package dev.vibe.module.impl;

import dev.vibe.Vibe;
import dev.vibe.module.Module;
import dev.vibe.module.ModuleManager;
import dev.vibe.setting.Setting;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/** Drives Scaffold against a small fake world without a game window. */
public class ScaffoldModuleTest {
    private Minecraft previousMinecraft;
    private Vibe previousVibe;
    private Minecraft minecraft;
    private Player player;
    private FakeWorld world;
    private Controller controller;
    private MoveFixModule moveFix;
    private ScaffoldModule scaffold;
    private ItemStack sword;
    private ItemStack stone;

    @Before public void setUp() throws Exception {
        Field registered = net.minecraft.init.Bootstrap.class.getDeclaredField("alreadyRegistered");
        registered.setAccessible(true);
        if (!registered.getBoolean(null)) {
            // Forge's statistics bootstrap needs its launch class loader; blocks/items suffice here.
            registered.setBoolean(null, true);
            net.minecraft.block.Block.registerBlocks();
            net.minecraft.item.Item.registerItems();
        }
        previousMinecraft = Minecraft.getMinecraft();
        previousVibe = Vibe.getInstance();
        minecraft = allocate(Minecraft.class);
        set(Minecraft.class, null, "theMinecraft", minecraft);
        minecraft.gameSettings = new GameSettings();
        minecraft.gameSettings.mouseSensitivity = 0.5F;
        world = allocate(FakeWorld.class);
        world.blocks = new HashMap<BlockPos, IBlockState>();
        minecraft.theWorld = world;
        controller = allocate(Controller.class);
        controller.placements = new ArrayList<Object[]>();
        minecraft.playerController = controller;
        player = allocate(Player.class);
        player.inventory = new InventoryPlayer(player);
        sword = new ItemStack(Items.iron_sword);
        stone = new ItemStack(Blocks.stone, 64);
        player.inventory.mainInventory[0] = sword;
        player.inventory.mainInventory[3] = stone;
        player.inventory.currentItem = 0;
        minecraft.thePlayer = player;

        Vibe vibe = new Vibe();
        set(Vibe.class, null, "instance", vibe);
        ModuleManager manager = allocate(ModuleManager.class);
        set(Vibe.class, vibe, "moduleManager", manager);
        moveFix = new MoveFixModule();
        scaffold = new ScaffoldModule();
        set(ModuleManager.class, manager, "modules", new ArrayList<Module>(Arrays.asList(moveFix, scaffold)));
    }

    @After public void tearDown() throws Exception {
        KeyBinding.unPressAllKeys();
        set(Minecraft.class, null, "theMinecraft", previousMinecraft);
        set(Vibe.class, null, "instance", previousVibe);
    }

    @Test public void bridgeEdgeRotatesThroughMoveFixAndPlacesAgainstTheSupportFace() throws Exception {
        world.blocks.put(new BlockPos(0, 63, 0), Blocks.stone.getDefaultState());
        // Walked east past the edge of the only block, camera still facing east.
        standAt(1.2D, 64.0D, 0.5D, -90.0F, 20.0F);
        scaffold.setEnabled(true);
        scaffold.tickStart();

        assertEquals(new BlockPos(0, 63, 0), scaffold.getTargetBlock());
        assertEquals(EnumFacing.EAST, scaffold.getTargetFace());
        assertTrue("Scaffold must provide the server rotation", scaffold.ownsRotation());
        assertEquals("Silent", moveFix.getEffectiveCorrection());
        MovingObjectPosition hit = serverRay();
        assertNotNull(hit);
        assertEquals(new BlockPos(0, 63, 0), hit.getBlockPos());
        assertEquals("The fake rotation itself must reach the chosen face", EnumFacing.EAST, hit.sideHit);
        assertEquals(-90.0F, player.rotationYaw, 0.0F);

        // Spoof Slot: the server holds the blocks while the hotbar stays on the sword.
        assertEquals(3, AutoToolModule.serverSlotHook(0));
        ScaffoldModule.prepareInputHook();
        assertEquals(1, controller.placements.size());
        Object[] placement = controller.placements.get(0);
        assertSame(stone, placement[0]);
        assertEquals(new BlockPos(0, 63, 0), placement[1]);
        assertEquals(EnumFacing.EAST, placement[2]);
        assertEquals("Vanilla's click pass uses the block slot too", 3, player.inventory.currentItem);
        ScaffoldModule.finishInputHook();
        assertEquals(0, player.inventory.currentItem);
        assertSame(sword, player.getHeldItem());

        scaffold.setEnabled(false);
        assertEquals(0, AutoToolModule.serverSlotHook(0));
        assertFalse(scaffold.ownsRotation());
        moveFix.setFakeRotation("other", 0.0F, 0.0F);
        moveFix.clearFakeRotation(scaffold.getId());
        assertEquals("Only the producer that owns the rotation can clear it", 0.0F,
                net.minecraft.util.MathHelper.wrapAngleTo180_float(moveFix.getRotationYaw()), 0.001F);
    }

    @Test public void placementWaitsUntilTheServerRotationActuallyHitsTheFace() throws Exception {
        world.blocks.put(new BlockPos(0, 63, 0), Blocks.stone.getDefaultState());
        // Still above the block: the east face is behind the eyes' plane.
        standAt(0.6D, 64.0D, 0.5D, -90.0F, 20.0F);
        scaffold.setEnabled(true);
        scaffold.tickStart();
        assertEquals(EnumFacing.EAST, scaffold.getTargetFace());
        ScaffoldModule.prepareInputHook();
        assertTrue("No placement may be sent with a ray that misses the face", controller.placements.isEmpty());
        ScaffoldModule.finishInputHook();
        assertEquals(0, player.inventory.currentItem);
    }

    @Test public void withoutSpoofSlotTheBlocksAreSelectedAndRestoredOnDisable() throws Exception {
        world.blocks.put(new BlockPos(0, 63, 0), Blocks.stone.getDefaultState());
        setting("Spoof Slot").setValue(false);
        standAt(1.2D, 64.0D, 0.5D, -90.0F, 20.0F);
        scaffold.setEnabled(true);
        scaffold.tickStart();
        assertEquals(0, AutoToolModule.serverSlotHook(0));
        ScaffoldModule.prepareInputHook();
        ScaffoldModule.finishInputHook();
        assertEquals(1, controller.placements.size());
        assertEquals(3, player.inventory.currentItem);
        scaffold.setEnabled(false);
        assertEquals(0, player.inventory.currentItem);
    }

    @Test public void noBlocksReleasesTheRotation() throws Exception {
        world.blocks.put(new BlockPos(0, 63, 0), Blocks.stone.getDefaultState());
        standAt(1.2D, 64.0D, 0.5D, -90.0F, 20.0F);
        scaffold.setEnabled(true);
        scaffold.tickStart();
        assertTrue(scaffold.ownsRotation());
        player.inventory.mainInventory[3] = null;
        scaffold.tickStart();
        assertFalse(scaffold.ownsRotation());
        assertEquals(0, AutoToolModule.serverSlotHook(0));
        ScaffoldModule.prepareInputHook();
        assertTrue(controller.placements.isEmpty());
    }

    @Test public void moveFixSettingPinsTheCorrectionForTheScaffoldRotation() throws Exception {
        world.blocks.put(new BlockPos(0, 63, 0), Blocks.stone.getDefaultState());
        standAt(1.2D, 64.0D, 0.5D, -90.0F, 20.0F);
        moveFix.getCorrectMovement().setValue("Prevent Backwards Sprinting");
        scaffold.setEnabled(true);
        scaffold.tickStart();
        assertEquals("Prevent Backwards Sprinting", moveFix.getEffectiveCorrection());
        setting("Move Fix").setValue(false);
        scaffold.tickStart();
        assertEquals("Off", moveFix.getEffectiveCorrection());
        scaffold.getMode().setValue("Telly");
        scaffold.tickStart();
        assertEquals("Telly always corrects movement", "Prevent Backwards Sprinting", moveFix.getEffectiveCorrection());
    }

    @Test public void correctionOverrideLastsThroughTheRotateBackOnly() throws Exception {
        standAt(0.5D, 64.0D, 0.5D, 0.0F, 0.0F);
        moveFix.getCorrectMovement().setValue("Off");
        moveFix.setFakeRotation("scaffold", 120.0F, 60.0F, "silent");
        assertEquals("Silent", moveFix.getEffectiveCorrection());
        moveFix.clearFakeRotation("scaffold");
        assertEquals("Returning to the camera keeps the producer's correction", "Silent", moveFix.getEffectiveCorrection());
        for (int tick = 0; tick < 100; tick++) moveFix.tick();
        assertEquals("Off", moveFix.getEffectiveCorrection());
        moveFix.setFakeRotation("scaffold", 120.0F, 60.0F, "Direct");
        moveFix.setFakeRotation("aura", 10.0F, 0.0F);
        assertEquals("A new producer without an override follows the setting", "Off", moveFix.getEffectiveCorrection());
        moveFix.setFakeRotation("scaffold", 120.0F, 60.0F, "Unknown");
        assertEquals("Off", moveFix.getEffectiveCorrection());
    }

    @Test public void intaveTowerLowersOnlyTheLocalPlayersJump() throws Exception {
        standAt(0.5D, 64.0D, 0.5D, 0.0F, 0.0F);
        scaffold.getTower().setValue("Intave");
        scaffold.setEnabled(true);
        KeyBinding.setKeyBindState(minecraft.gameSettings.keyBindJump.getKeyCode(), true);
        assertEquals(0.41F, ScaffoldModule.jumpMotionHook(0.42F, player), 0.0F);
        assertEquals(0.42F, ScaffoldModule.jumpMotionHook(0.42F, new Object()), 0.0F);
        KeyBinding.setKeyBindState(minecraft.gameSettings.keyBindJump.getKeyCode(), false);
        assertEquals(0.42F, ScaffoldModule.jumpMotionHook(0.42F, player), 0.0F);
        scaffold.getTower().setValue("NCP");
        KeyBinding.setKeyBindState(minecraft.gameSettings.keyBindJump.getKeyCode(), true);
        assertEquals(0.42F, ScaffoldModule.jumpMotionHook(0.42F, player), 0.0F);
    }

    @Test public void blockSlotPrefersTheSlotInUseThenTheLargestPlaceableStack() throws Exception {
        InventoryPlayer inventory = player.inventory;
        inventory.mainInventory[1] = new ItemStack(Blocks.wool, 32);
        inventory.mainInventory[2] = new ItemStack(Blocks.sand, 64);
        inventory.mainInventory[4] = new ItemStack(Blocks.planks, 64);
        assertEquals("Sand falls and is never used; the first largest stack wins", 3, ScaffoldModule.findBlockSlot(inventory, 0));
        assertEquals(1, ScaffoldModule.findBlockSlot(inventory, 1));
        inventory.mainInventory[1].stackSize = 1;
        assertEquals("A last block in the current slot is not preferred", 3, ScaffoldModule.findBlockSlot(inventory, 1));
        assertFalse(ScaffoldModule.isPlaceable(new ItemStack(Blocks.chest)));
        assertTrue(ScaffoldModule.isPlaceable(new ItemStack(Blocks.wool)));
        assertEquals(1 + 64 + 64, scaffold.getBlockCount());
    }

    @Test public void smoothRotationLimitsSpeedSnapsToMouseStepsAndTakesTheShortWay() {
        Random random = new Random(7L);
        float factor = 0.5F * 0.6F + 0.2F;
        float step = factor * factor * factor * 1.2F;
        for (int i = 0; i < 200; i++) {
            float[] next = ScaffoldModule.smoothRotation(170.0F, 10.0F, -170.0F, 80.0F, 30.0F, 15.0F, 0.5F, random);
            float yawDelta = next[0] - 170.0F;
            float pitchDelta = next[1] - 10.0F;
            assertTrue("Wraps through 180 instead of turning 340 degrees", yawDelta > 0.0F && yawDelta <= 20.34F);
            assertTrue(pitchDelta > 0.0F && pitchDelta <= 18.0F);
            assertEquals(0.0F, remainder(yawDelta, step), 0.0005F);
            assertEquals(0.0F, remainder(pitchDelta, step), 0.0005F);
        }
        float[] held = ScaffoldModule.smoothRotation(30.0F, 60.0F, 30.0F, 60.0F, 180.0F, 180.0F, 0.5F, random);
        assertEquals(30.0F, held[0], 0.0F);
        assertEquals(60.0F, held[1], 0.0F);
        float clamped = ScaffoldModule.smoothRotation(0.0F, 89.0F, 0.0F, 140.0F, 0.0F, 180.0F, 0.5F, random)[1];
        assertTrue("Pitch never passes straight down", clamped > 89.0F && clamped <= 90.0F);
    }

    @Test public void movementYawAndDiagonalsFollowTheKeys() {
        assertEquals(10.0F, ScaffoldModule.movementYaw(10.0F, true, false, false, false), 0.0F);
        assertEquals(190.0F, ScaffoldModule.movementYaw(10.0F, false, true, false, false), 0.0F);
        assertEquals(-35.0F, ScaffoldModule.movementYaw(10.0F, true, false, true, false), 0.0F);
        assertEquals(235.0F, ScaffoldModule.movementYaw(10.0F, false, true, true, false), 0.0F);
        assertEquals(100.0F, ScaffoldModule.movementYaw(10.0F, false, false, false, true), 0.0F);
        assertTrue(ScaffoldModule.goingDiagonally(-45.0F));
        assertTrue(ScaffoldModule.goingDiagonally(496.0F));
        assertFalse(ScaffoldModule.goingDiagonally(90.0F));
        assertFalse(ScaffoldModule.goingDiagonally(30.0F));
    }

    @Test public void entryFaceReportsTheSideARayEntersThrough() {
        AxisAlignedBB box = new AxisAlignedBB(0, 63, 0, 1, 64, 1);
        Vec3 eyes = new Vec3(1.2D, 65.62D, 0.5D);
        assertEquals(EnumFacing.EAST, ScaffoldModule.entryFace(eyes, ScaffoldModule.lookVector(90.0F, 83.5F), 4.5D, box));
        assertEquals(EnumFacing.UP, ScaffoldModule.entryFace(eyes, ScaffoldModule.lookVector(90.0F, 70.0F), 4.5D, box));
        assertNull(ScaffoldModule.entryFace(eyes, ScaffoldModule.lookVector(-90.0F, 80.0F), 4.5D, box));
        assertNull("Too short to reach", ScaffoldModule.entryFace(eyes, ScaffoldModule.lookVector(90.0F, 83.5F), 1.0D, box));
        assertNull("Starting inside the box has no entry face",
                ScaffoldModule.entryFace(new Vec3(0.5D, 63.5D, 0.5D), ScaffoldModule.lookVector(0.0F, 0.0F), 4.5D, box));
    }

    private MovingObjectPosition serverRay() {
        Vec3 eyes = player.getPositionEyes(1.0F);
        Vec3 look = ScaffoldModule.lookVector(moveFix.getRotationYaw(), moveFix.getRotationPitch());
        return world.rayTraceBlocks(eyes, eyes.addVector(look.xCoord * 4.5D, look.yCoord * 4.5D, look.zCoord * 4.5D),
                false, false, true);
    }

    private void standAt(double x, double y, double z, float yaw, float pitch) {
        player.posX = x;
        player.posY = y;
        player.posZ = z;
        player.onGround = true;
        player.rotationYaw = player.prevRotationYaw = yaw;
        player.rotationPitch = player.prevRotationPitch = pitch;
        player.setEntityBoundingBox(new AxisAlignedBB(x - 0.3D, y, z - 0.3D, x + 0.3D, y + 1.8D, z + 0.3D));
    }

    @SuppressWarnings("unchecked")
    private Setting<Boolean> setting(String name) {
        for (Setting<?> setting : scaffold.getSettings()) {
            if (setting.getRawName().equals(name)) return (Setting<Boolean>) setting;
        }
        throw new AssertionError("Missing setting " + name);
    }

    private static float remainder(float value, float step) {
        float rest = Math.abs(value % step);
        return Math.min(rest, step - rest);
    }

    private static void set(Class<?> type, Object object, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(object, value);
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        Class<?> unsafe = Class.forName("sun.misc.Unsafe");
        Field field = unsafe.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(unsafe.getMethod("allocateInstance", Class.class).invoke(field.get(null), type));
    }

    public static final class Player extends EntityPlayerSP {
        private Player() { super(null, null, null, null); }
        @Override public Vec3 getPositionEyes(float partialTicks) { return new Vec3(posX, posY + 1.62D, posZ); }
        @Override public ItemStack getHeldItem() { return inventory.getCurrentItem(); }
    }

    public static final class Controller extends PlayerControllerMP {
        List<Object[]> placements;
        private Controller() { super(null, null); }
        @Override public float getBlockReachDistance() { return 4.5F; }
        @Override public boolean onPlayerRightClick(EntityPlayerSP player, WorldClient world, ItemStack stack,
                BlockPos pos, EnumFacing side, Vec3 hit) {
            placements.add(new Object[] {stack, pos, side, hit, player.inventory.currentItem});
            return true;
        }
        @Override public boolean sendUseItem(EntityPlayer player, World world, ItemStack stack) { return false; }
    }

    public static final class FakeWorld extends WorldClient {
        Map<BlockPos, IBlockState> blocks;
        private FakeWorld() { super(null, null, 0, null, null); }
        @Override public IBlockState getBlockState(BlockPos pos) {
            IBlockState state = blocks.get(pos);
            return state == null ? Blocks.air.getDefaultState() : state;
        }
        @Override public boolean isAirBlock(BlockPos pos) { return getBlockState(pos).getBlock() == Blocks.air; }
    }
}

package dev.vibe.module.impl.world;

import dev.vibe.Vibe;
import dev.vibe.combat.RotationMath;
import dev.vibe.module.Module;
import dev.vibe.module.ModuleManager;
import dev.vibe.module.impl.movement.MoveFixModule;
import dev.vibe.setting.ModeSetting;
import dev.vibe.setting.NumberSetting;
import dev.vibe.setting.RangeSetting;
import dev.vibe.setting.Setting;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovementInput;
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
        // Most tests check where the rotation ends up; the speed tests set their own limits.
        range("Rotation Speed").setRange(180.0D, 180.0D);
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
        assertEquals("Waits behind the player instead of turning toward the hidden face", 0.0F,
                RotationMath.difference(moveFix.getRotationYaw(), 90.0F), 0.2F);
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

    @Test public void rotationsIncludeTheSourcePortedHypixelProfile() {
        assertEquals(Arrays.asList("Normal", "GodBridge", "Hypixel"), scaffold.getRotations().getModes());
        assertEquals("Normal", scaffold.getRotations().getValue());
    }

    @Test public void hypixelPresetMatchesScreenshotAndOnlyExposesLongAndTellyB() {
        scaffold.getRotations().setValue("Hypixel");
        assertEquals(Arrays.asList("Disabled", "Long"), scaffold.hypixelTelly.getModes());
        assertEquals("Long", scaffold.hypixelTelly.getValue());
        assertEquals(Arrays.asList("Disabled", "Telly B"), scaffold.hypixelKeepMode.getModes());
        assertEquals("Telly B", scaffold.hypixelKeepMode.getValue());
        assertEquals(Arrays.asList("Disabled", "2", "3"), scaffold.hypixelMultiPlace.getModes());
        assertEquals("Disabled", scaffold.hypixelMultiPlace.getValue());
        assertEquals(20.0D, scaffold.hypixelClickSpeed.getDouble(), 0.0D);
        assertEquals(-0.6D, scaffold.hypixelIceThreshold.getDouble(), 0.0D);
        assertTrue(scaffold.hypixelTellyOnJump.isEnabled());
        assertFalse(scaffold.hypixelKeepRmb.isEnabled());
        assertFalse(scaffold.hypixelDisableJumpPotion.isEnabled());
    }

    @Test public void hypixelPacketsOnlyAllowOwnedPlacementAndNeverSuppressNonDigPackets() throws Exception {
        HypixelScaffold profile = new HypixelScaffold(scaffold);
        net.minecraft.network.Packet<?> use = new net.minecraft.network.play.client.C08PacketPlayerBlockPlacement(stone);
        net.minecraft.network.Packet<?> dig = new net.minecraft.network.play.client.C07PacketPlayerDigging(
                net.minecraft.network.play.client.C07PacketPlayerDigging.Action.START_DESTROY_BLOCK, new BlockPos(0,63,0), EnumFacing.UP);
        assertTrue(profile.permitsPacket(use));
        set(HypixelScaffold.class, profile, "rotationSentThisTick", true);
        assertFalse(profile.permitsPacket(use));
        player.inventory.currentItem = 3;
        assertFalse(profile.permitsPacket(dig));
        assertTrue(profile.permitsPacket(new net.minecraft.network.play.client.C0APacketAnimation()));
        assertTrue(profile.permitsPacket(new net.minecraft.network.play.client.C07PacketPlayerDigging(
                net.minecraft.network.play.client.C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN)));
        set(HypixelScaffold.class, profile, "sendingPlacement", true);
        assertTrue(profile.permitsPacket(use));
    }

    @Test public void hypixelHookUsesRavensUpdateTimingQuantizationAndMoveFix() throws Exception {
        world.blocks.put(new BlockPos(0,63,0), Blocks.stone.getDefaultState());
        standAt(0.5D, 64.0D, 0.5D, -90.0F, 20.0F);
        player.movementInput = new MovementInput();
        player.movementInput.moveForward = 1.0F;
        keys(true, false, false, false);
        scaffold.getRotations().setValue("Hypixel");
        // Isolate the flat-ground rotation from the screenshot's automatic Keep-Y jump.
        scaffold.hypixelKeepMode.setValue("Disabled");
        scaffold.hypixelTelly.setValue("Disabled");
        scaffold.setEnabled(true);
        scaffold.tickStart();
        assertFalse("The port waits for onUpdate HEAD, after vanilla's click pass", scaffold.ownsRotation());
        ScaffoldModule.hypixelUpdateHook();
        assertTrue(scaffold.ownsRotation());
        assertEquals("Silent", moveFix.getEffectiveCorrection());
        assertTrue(Float.isFinite(moveFix.getRotationYaw()));
        assertTrue(Float.isFinite(moveFix.getRotationPitch()));
        assertEquals(Math.round(moveFix.getRotationYaw() / 0.0234375F),
                moveFix.getRotationYaw() / 0.0234375F, 0.001D);
        assertEquals(Math.round(moveFix.getRotationPitch() / 0.0234375F),
                moveFix.getRotationPitch() / 0.0234375F, 0.001D);
        assertSame(sword, player.getHeldItem());
    }

    @Test public void hypixelAcceptsRavensIceStacksEvenWhenNormalScaffoldExcludesThem() {
        player.inventory.mainInventory[3] = new ItemStack(Blocks.ice,64);
        scaffold.getRotations().setValue("Hypixel");
        scaffold.setEnabled(true);
        scaffold.tickStart();
        assertEquals(3, scaffold.getServerSlot());
        assertSame(player.inventory.mainInventory[3], scaffold.getBlockStack());
    }

    @Test public void hypixelLongTellyQueueIsTheRavenThreeBlockAlternatingRow() throws Exception {
        HypixelScaffold profile = new HypixelScaffold(scaffold);
        set(HypixelScaffold.class, profile, "hasLongTellyOrigin", true);
        set(HypixelScaffold.class, profile, "longTellyOriginX", 10);
        set(HypixelScaffold.class, profile, "longTellyOriginZ", 20);
        set(HypixelScaffold.class, profile, "longTellyForwardX", 1);
        set(HypixelScaffold.class, profile, "longTellyForwardZ", 0);
        set(HypixelScaffold.class, profile, "longTellyLateralX", 0);
        set(HypixelScaffold.class, profile, "longTellyLateralZ", 1);
        set(HypixelScaffold.class, profile, "longTellyRowY", 63);
        set(HypixelScaffold.class, profile, "lastGroundX", 10);
        set(HypixelScaffold.class, profile, "lastGroundZ", 20);
        set(HypixelScaffold.class, profile, "ticksSinceGrounded", 0);
        set(HypixelScaffold.class, profile, "longTellyNeedsSideUpdate", false);
        set(HypixelScaffold.class, profile, "longTellySprintJump", false);
        standAt(10.5D, 64.0D, 20.5D, -90.0F, 20.0F);
        java.lang.reflect.Method build = HypixelScaffold.class.getDeclaredMethod("buildLongTellyQueue", Vec3.class);
        build.setAccessible(true);
        build.invoke(profile, new Vec3(0.3D, 0.42D, 0.0D));
        Field count = HypixelScaffold.class.getDeclaredField("queuedBlockCount");
        Field queue = HypixelScaffold.class.getDeclaredField("queuedBlocks");
        count.setAccessible(true); queue.setAccessible(true);
        assertEquals(3, count.getInt(profile));
        BlockPos[] positions = (BlockPos[]) queue.get(profile);
        assertEquals(new BlockPos(10,64,20), positions[0]);
        assertEquals(new BlockPos(11,64,20), positions[1]);
        assertEquals(new BlockPos(12,64,20), positions[2]);
    }

    @Test public void rotationSpeedLimitsEveryTickAndAccelerationRampsUp() throws Exception {
        world.blocks.put(new BlockPos(0, 63, 0), Blocks.stone.getDefaultState());
        standAt(1.2D, 64.0D, 0.5D, -90.0F, 20.0F);
        range("Rotation Speed").setRange(20.0D, 20.0D);
        scaffold.setEnabled(true);
        scaffold.tickStart();
        float turned = Math.abs(RotationMath.difference(moveFix.getRotationYaw(), -90.0F));
        assertTrue("Turned " + turned, turned > 19.8F && turned <= 20.0F);
        ScaffoldModule.prepareInputHook();
        ScaffoldModule.finishInputHook();
        assertTrue(controller.placements.isEmpty());
        for (int tick = 0; tick < 10; tick++) scaffold.tickStart();
        ScaffoldModule.prepareInputHook();
        ScaffoldModule.finishInputHook();
        assertEquals("Placed once the limited turn reached the face", 1, controller.placements.size());

        scaffold.setEnabled(false);
        for (int tick = 0; tick < 100; tick++) moveFix.tick();
        assertEquals("Back at the camera", -90.0F, moveFix.getRotationYaw(), 0.0F);
        ((ModeSetting) find("Rotation Mode")).setValue("Acceleration");
        range("Rotation Acceleration").setRange(2.0D, 2.0D);
        scaffold.setEnabled(true);
        scaffold.tickStart();
        float first = Math.abs(RotationMath.difference(moveFix.getRotationYaw(), -90.0F));
        scaffold.tickStart();
        float second = Math.abs(RotationMath.difference(moveFix.getRotationYaw(), -90.0F)) - first;
        assertTrue("First step " + first, first > 0.0F && first <= 2.0F);
        assertTrue("Second step " + second, second > first && second <= 4.0F);
    }

    @Test public void jumpingNeverTurnsTheNormalRotationAhead() throws Exception {
        world.blocks.put(new BlockPos(-1, 63, 0), Blocks.stone.getDefaultState());
        world.blocks.put(new BlockPos(0, 63, 0), Blocks.stone.getDefaultState());
        keys(true, false, false, false);
        standAt(0.5D, 64.0D, 0.5D, -90.0F, 20.0F);
        scaffold.setEnabled(true);
        double x = 0.5D;
        double y = 64.0D;
        double motionY = 0.42D;
        for (int tick = 0; tick < 12; tick++) {
            scaffold.tickStart();
            float fromBehind = RotationMath.difference(moveFix.getRotationYaw(), 90.0F);
            assertTrue("Tick " + tick + " looked " + fromBehind + " degrees from behind", Math.abs(fromBehind) <= 90.0F);
            x += 0.1D;
            y += motionY;
            motionY = (motionY - 0.08D) * 0.98D;
            moveTo(x, Math.max(64.0D, y), 0.5D, y <= 64.0D);
        }
    }

    @Test public void sidewaysLooksPastWhicheverSideOfTheBlockThePlayerStandsOn() throws Exception {
        world.blocks.put(new BlockPos(0, 63, 0), Blocks.stone.getDefaultState());
        setting("Sideways").setValue(true);
        keys(true, false, false, false);
        // Walking east on the right (south) half: look back-left.
        standAt(1.15D, 64.0D, 0.85D, -90.0F, 20.0F);
        scaffold.setEnabled(true);
        assertPlacesFrom(135.0F);
        // Left (north) half: back-left would miss the face, so look back-right.
        moveTo(1.15D, 64.0D, 0.15D, true);
        assertPlacesFrom(45.0F);
        // Diagonal bridges look straight back.
        player.rotationYaw = -45.0F;
        moveTo(1.2D, 64.0D, 0.9D, true);
        assertPlacesFrom(135.0F);
    }

    @Test public void godBridgeHoldsASnappedDiagonalAndPlaces() throws Exception {
        world.blocks.put(new BlockPos(0, 63, 0), Blocks.stone.getDefaultState());
        scaffold.getRotations().setValue("GodBridge");
        keys(true, false, false, false);
        standAt(1.15D, 64.0D, 0.2D, -80.0F, 20.0F);
        scaffold.setEnabled(true);
        assertPlacesFrom(45.0F);
        moveTo(1.15D, 64.0D, 0.8D, true);
        assertPlacesFrom(135.0F);
        KeyBinding.setKeyBindState(minecraft.gameSettings.keyBindSneak.getKeyCode(), true);
        assertPlacesFrom(135.0F);
    }

    @Test public void sneakOnlyAtTheBlockEndAndUnsneaksAfterTheDelay() throws Exception {
        world.blocks.put(new BlockPos(0, 63, 0), Blocks.stone.getDefaultState());
        setting("Sneak").setValue(true);
        setting("Safe Walk").setValue(false);
        setting("Randomize Unsneak").setValue(false);
        number("Unsneak Delay").setValue(0.0D);
        keys(true, false, false, false);
        standAt(0.4D, 64.0D, 0.5D, -90.0F, 20.0F);
        scaffold.setEnabled(true);
        player.motionX = 0.1D;
        assertFalse("Walking over the block never sneaks", moveInput(false).sneak);
        moveTo(0.85D, 64.0D, 0.5D, true);
        MovementInput atEnd = moveInput(false);
        assertTrue(atEnd.sneak);
        assertEquals(0.3F, atEnd.moveForward, 0.0F);
        assertFalse("A jump never sneaks", moveInput(true).sneak);
        assertTrue(moveInput(false).sneak);
        world.blocks.put(new BlockPos(1, 63, 0), Blocks.stone.getDefaultState());
        assertFalse("Released once a block supports the way ahead", moveInput(false).sneak);

        number("Unsneak Delay").setValue(500.0D);
        world.blocks.remove(new BlockPos(1, 63, 0));
        assertTrue(moveInput(false).sneak);
        world.blocks.put(new BlockPos(1, 63, 0), Blocks.stone.getDefaultState());
        assertTrue("Still sneaking during the unsneak delay", moveInput(false).sneak);
    }

    @Test public void preventDoubleSneakingKeepsSneakingUntilTheCorner() throws Exception {
        setting("Sneak").setValue(true);
        setting("Safe Walk").setValue(false);
        setting("Randomize Unsneak").setValue(false);
        number("Unsneak Delay").setValue(0.0D);
        keys(true, false, false, false);
        for (boolean prevent : new boolean[] {true, false}) {
            setting("Prevent Double Sneaking").setValue(prevent);
            world.blocks.clear();
            world.blocks.put(new BlockPos(0, 63, 0), Blocks.stone.getDefaultState());
            // Walking south-east toward the corner of the block.
            standAt(0.9D, 64.0D, 0.85D, -45.0F, 20.0F);
            scaffold.setEnabled(true);
            player.motionX = player.motionZ = 0.05D;
            assertTrue(moveInput(false).sneak);
            // Past the east edge, but not yet past the south one.
            world.blocks.put(new BlockPos(1, 63, 0), Blocks.stone.getDefaultState());
            moveTo(1.3D, 64.0D, 0.5D, true);
            assertEquals(prevent, moveInput(false).sneak);
            if (prevent) {
                world.blocks.put(new BlockPos(1, 63, 1), Blocks.stone.getDefaultState());
                moveTo(1.5D, 64.0D, 1.2D, true);
                assertFalse("Unsneaks at the corner", moveInput(false).sneak);
            }
            scaffold.setEnabled(false);
        }
    }

    @Test public void tellyNeverTurnsAroundForAJumpThatLandsOnBlocks() throws Exception {
        for (int x = -3; x <= 8; x++) world.blocks.put(new BlockPos(x, 63, 0), Blocks.stone.getDefaultState());
        scaffold.getMode().setValue("Telly");
        range("Telly Ticks").setRange(0.0D, 0.0D);
        keys(true, false, false, false);
        standAt(0.6D, 64.0D, 0.5D, -90.0F, 30.0F);
        scaffold.setEnabled(true);
        jump(new Runnable() {
            @Override public void run() {
                assertEquals(-90.0F, moveFix.getRotationYaw(), 0.2F);
            }
        });
        assertTrue(controller.placements.isEmpty());
    }

    @Test public void tellyTurnsAroundAfterItsTicksAndBridgesSeveralBlocksPerJump() throws Exception {
        for (int x = -3; x <= 0; x++) world.blocks.put(new BlockPos(x, 63, 0), Blocks.stone.getDefaultState());
        controller.build = true;
        scaffold.getMode().setValue("Telly");
        range("Telly Ticks").setRange(2.0D, 2.0D);
        keys(true, false, false, false);
        standAt(0.6D, 64.0D, 0.5D, -90.0F, 30.0F);
        scaffold.setEnabled(true);
        final boolean[] turned = new boolean[1];
        final int[] tick = new int[1];
        jump(new Runnable() {
            @Override public void run() {
                boolean back = Math.abs(RotationMath.difference(moveFix.getRotationYaw(), -90.0F)) > 90.0F;
                if (tick[0]++ < 2) assertFalse("Forward on the ground and for the first Telly Ticks", back);
                turned[0] |= back;
            }
        });
        assertTrue(turned[0]);
        assertTrue("Placed " + controller.placements.size(), controller.placements.size() >= 3);
        for (int x = 1; x <= 3; x++) assertTrue("Column " + x, world.blocks.containsKey(new BlockPos(x, 63, 0)));
        // Landed: forward again for the next run-up.
        tick();
        tick();
        assertEquals(-90.0F, RotationMath.difference(moveFix.getRotationYaw(), 0.0F), 0.2F);
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
        assertTrue(ScaffoldModule.bridgingDiagonally(30.0F));
        assertTrue(ScaffoldModule.bridgingDiagonally(-45.0F));
        assertFalse(ScaffoldModule.bridgingDiagonally(-80.0F));
        assertFalse(ScaffoldModule.bridgingDiagonally(200.0F));
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

    /** A few ticks at the current position must settle on {@code yaw} and place against the east face. */
    private void assertPlacesFrom(float yaw) {
        int before = controller.placements.size();
        for (int i = 0; i < 3; i++) scaffold.tickStart();
        assertEquals("Server yaw " + moveFix.getRotationYaw(), 0.0F, RotationMath.difference(moveFix.getRotationYaw(), yaw), 0.2F);
        ScaffoldModule.prepareInputHook();
        ScaffoldModule.finishInputHook();
        assertEquals(before + 1, controller.placements.size());
        assertEquals(EnumFacing.EAST, controller.placements.get(before)[2]);
    }

    /** A sprint jump east from the current position, one tick at a time, with a check after each rotation. */
    private void jump(Runnable afterRotation) {
        double x = player.posX;
        double y = player.posY;
        double motionY = 0.42D;
        for (int tick = 0; tick < 12; tick++) {
            tick();
            afterRotation.run();
            x += 0.3D;
            y += motionY;
            motionY = (motionY - 0.08D) * 0.98D;
            moveTo(x, Math.max(64.0D, y), 0.5D, y <= 64.0D);
            player.motionX = 0.3D;
            player.motionY = y <= 64.0D ? -0.0784D : motionY;
            scaffold.beforeWalkingUpdate(player);
        }
    }

    private void tick() {
        scaffold.tickStart();
        ScaffoldModule.prepareInputHook();
        ScaffoldModule.finishInputHook();
    }

    private MovementInput moveInput(boolean jumping) {
        MovementInput input = new MovementInput();
        input.moveForward = 1.0F;
        input.jump = jumping;
        scaffold.applyMoveInput(input);
        return input;
    }

    private void keys(boolean forward, boolean back, boolean left, boolean right) {
        KeyBinding.setKeyBindState(minecraft.gameSettings.keyBindForward.getKeyCode(), forward);
        KeyBinding.setKeyBindState(minecraft.gameSettings.keyBindBack.getKeyCode(), back);
        KeyBinding.setKeyBindState(minecraft.gameSettings.keyBindLeft.getKeyCode(), left);
        KeyBinding.setKeyBindState(minecraft.gameSettings.keyBindRight.getKeyCode(), right);
    }

    private MovingObjectPosition serverRay() {
        Vec3 eyes = player.getPositionEyes(1.0F);
        Vec3 look = ScaffoldModule.lookVector(moveFix.getRotationYaw(), moveFix.getRotationPitch());
        return world.rayTraceBlocks(eyes, eyes.addVector(look.xCoord * 4.5D, look.yCoord * 4.5D, look.zCoord * 4.5D),
                false, false, true);
    }

    private void standAt(double x, double y, double z, float yaw, float pitch) {
        moveTo(x, y, z, true);
        player.rotationYaw = player.prevRotationYaw = yaw;
        player.rotationPitch = player.prevRotationPitch = pitch;
        player.motionX = player.motionZ = 0.0D;
        player.motionY = -0.0784D;
    }

    private void moveTo(double x, double y, double z, boolean onGround) {
        player.posX = x;
        player.posY = y;
        player.posZ = z;
        player.onGround = onGround;
        player.setEntityBoundingBox(new AxisAlignedBB(x - 0.3D, y, z - 0.3D, x + 0.3D, y + 1.8D, z + 0.3D));
    }

    private Setting<?> find(String name) {
        for (Setting<?> setting : scaffold.getSettings()) {
            if (setting.getRawName().equals(name)) return setting;
        }
        throw new AssertionError("Missing setting " + name);
    }

    @SuppressWarnings("unchecked")
    private Setting<Boolean> setting(String name) {
        return (Setting<Boolean>) find(name);
    }

    private NumberSetting number(String name) {
        return (NumberSetting) find(name);
    }

    private RangeSetting range(String name) {
        return (RangeSetting) find(name);
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
        /** Puts each placed block into the fake world. */
        boolean build;
        private Controller() { super(null, null); }
        @Override public float getBlockReachDistance() { return 4.5F; }
        @Override public boolean onPlayerRightClick(EntityPlayerSP player, WorldClient world, ItemStack stack,
                BlockPos pos, EnumFacing side, Vec3 hit) {
            placements.add(new Object[] {stack, pos, side, hit, player.inventory.currentItem});
            if (build) ((FakeWorld) world).blocks.put(pos.offset(side), Blocks.stone.getDefaultState());
            return true;
        }
        @Override public boolean sendUseItem(EntityPlayer player, World world, ItemStack stack) { return false; }
    }

    public static final class FakeWorld extends WorldClient {
        Map<BlockPos, IBlockState> blocks;
        private FakeWorld() { super(null, null, 0, null, null); }
        @Override public boolean isBlockLoaded(BlockPos pos) { return true; }
        @Override public IBlockState getBlockState(BlockPos pos) {
            IBlockState state = blocks.get(pos);
            return state == null ? Blocks.air.getDefaultState() : state;
        }
        @Override public boolean isAirBlock(BlockPos pos) { return getBlockState(pos).getBlock() == Blocks.air; }
        @Override public List<AxisAlignedBB> getCollidingBoundingBoxes(Entity entity, AxisAlignedBB box) {
            List<AxisAlignedBB> boxes = new ArrayList<AxisAlignedBB>();
            for (Map.Entry<BlockPos, IBlockState> entry : blocks.entrySet()) {
                AxisAlignedBB block = entry.getValue().getBlock().getCollisionBoundingBox(this, entry.getKey(), entry.getValue());
                if (block != null && block.intersectsWith(box)) boxes.add(block);
            }
            return boxes;
        }
    }
}

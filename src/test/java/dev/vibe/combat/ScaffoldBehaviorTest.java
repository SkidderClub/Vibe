package dev.vibe.combat;

import dev.vibe.Vibe;
import dev.vibe.module.ModuleManager;
import dev.vibe.module.Module;
import dev.vibe.module.impl.MoveFixModule;
import dev.vibe.module.impl.ScaffoldModule;
import dev.vibe.module.impl.scaffold.InventoryUtil;
import dev.vibe.module.impl.scaffold.MoveInputEvent;
import dev.vibe.module.impl.scaffold.MoveUtil;
import dev.vibe.module.impl.scaffold.RotationUtil;
import dev.vibe.setting.*;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.ContainerPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import org.junit.*;
import static org.junit.Assert.*;

public class ScaffoldBehaviorTest {
    private static Minecraft mc, previous;
    private static Vibe vibe, previousVibe;
    private static Object previousScaffoldClient;
    private ScaffoldModule scaffold;
    private MoveFixModule moveFix;
    private Player player;
    private World world;
    private Controller controller;

    @BeforeClass public static void setupClient() throws Exception {
        previous = Minecraft.getMinecraft(); previousVibe = Vibe.getInstance();
        Field bootstrap = field(net.minecraft.init.Bootstrap.class, "alreadyRegistered");
        if (!bootstrap.getBoolean(null)) { bootstrap.setBoolean(null, true); net.minecraft.block.Block.registerBlocks(); net.minecraft.item.Item.registerItems(); }
        mc = allocate(Minecraft.class); field(Minecraft.class, "theMinecraft").set(null, mc);
        vibe = allocate(Vibe.class); field(Vibe.class, "instance").set(null, vibe);
        mc.gameSettings = allocate(GameSettings.class);
        mc.gameSettings.keyBindForward = new KeyBinding("test-forward", 17, "test");
        mc.gameSettings.keyBindBack = new KeyBinding("test-back", 31, "test");
        mc.gameSettings.keyBindLeft = new KeyBinding("test-left", 30, "test");
        mc.gameSettings.keyBindRight = new KeyBinding("test-right", 32, "test");
        mc.gameSettings.keyBindJump = new KeyBinding("test-jump", 57, "test");
        mc.gameSettings.keyBindSneak = new KeyBinding("test-sneak", 42, "test");
        mc.gameSettings.keyBindSprint = new KeyBinding("test-sprint", 29, "test");
        mc.gameSettings.mouseSensitivity = 0.5F;
        previousScaffoldClient = field(ScaffoldModule.class, "mc").get(null);
        field(ScaffoldModule.class, "mc").set(null, mc);
    }

    @AfterClass public static void restoreClient() throws Exception {
        field(Minecraft.class, "theMinecraft").set(null, previous);
        field(Vibe.class, "instance").set(null, previousVibe);
        field(ScaffoldModule.class, "mc").set(null, previousScaffoldClient);
    }

    @Before public void setupWorld() throws Exception {
        KeyBinding.unPressAllKeys();
        player = allocate(Player.class); player.width = .6F; player.height = 1.8F;
        player.inventory = new InventoryPlayer(player);
        player.inventoryContainer = new ContainerPlayer(player.inventory, false, player);
        player.movementInput = new MovementInput();
        player.setPosition(.5, 1, .5); mc.thePlayer = player;
        world = allocate(World.class); world.blocks = new HashMap<>(); mc.theWorld = world;
        controller = allocate(Controller.class); mc.playerController = controller;
        mc.objectMouseOver = null;
        moveFix = new MoveFixModule(); scaffold = new ScaffoldModule();
        ModuleManager manager = allocate(ModuleManager.class);
        field(ModuleManager.class, "modules").set(manager, new ArrayList<Module>(Arrays.asList(moveFix, scaffold)));
        field(Vibe.class, "moduleManager").set(vibe, manager);
        field(Module.class, "enabled").setBoolean(scaffold, true);
        field(ScaffoldModule.class, "sessionPlayer").set(scaffold, player);
        field(ScaffoldModule.class, "posY").setInt(scaffold, 0);
        field(ScaffoldModule.class, "itemBefore").setInt(scaffold, 0);
        ((BooleanSetting) setting("Swing")).setEnabled(true);
    }

    @Test public void choosesTheHeldStackThenLargestEligibleHotbarStack() {
        player.inventory.mainInventory[0] = new ItemStack(Blocks.stone, 2);
        player.inventory.mainInventory[1] = new ItemStack(Blocks.wool, 64);
        player.inventory.mainInventory[2] = new ItemStack(Blocks.gold_block, 64);
        player.inventory.mainInventory[10] = new ItemStack(Blocks.stone, 64);
        assertEquals(0, InventoryUtil.getBlockSlot());
        player.inventory.mainInventory[0].stackSize = 1;
        assertEquals(1, InventoryUtil.getBlockSlot());
        assertEquals(129, InventoryUtil.getBlockCount());
        player.inventory.mainInventory[1] = null;
        assertEquals(0, InventoryUtil.getBlockSlot());
        player.inventory.mainInventory[0] = null;
        assertEquals(-1, InventoryUtil.getBlockSlot());
        assertFalse(scaffold.place());
        assertEquals(0, player.inventory.currentItem);
    }

    @Test public void picksSupportFaceRequiresMouseOverAndRestoresSpoofedSlot() throws Exception {
        BlockPos support = new BlockPos(0, 0, 0);
        world.blocks.put(support, Blocks.stone.getDefaultState());
        player.setPosition(1.2, 1, .5); player.onGround = true;
        player.inventory.mainInventory[2] = new ItemStack(Blocks.stone, 16);
        scaffold.processBlockData(player.posX, player.posZ);
        assertEquals(support, field(ScaffoldModule.class, "blockPos").get(scaffold));
        assertEquals(EnumFacing.EAST, field(ScaffoldModule.class, "facing").get(scaffold));
        assertFalse(scaffold.place());
        assertEquals(0, controller.placements);
        player.inventory.currentItem = 0;
        mc.objectMouseOver = new MovingObjectPosition(new Vec3(1, .5, .5), EnumFacing.EAST, support);
        scaffold.onClick();
        assertEquals(1, controller.placements);
        assertEquals(2, player.inventory.currentItem);
        assertEquals(1, player.swings);
        assertEquals(1, field(ScaffoldModule.class, "placed").getInt(scaffold));
        scaffold.onPostMotion();
        assertEquals(0, player.inventory.currentItem);
        ((BooleanSetting) setting("Spoof Slot")).setEnabled(false);
        scaffold.onClick(); scaffold.onPostMotion();
        assertEquals(2, player.inventory.currentItem);
    }

    @Test public void preservesOriginalRaycastAndPitchSearchQuirks() {
        BlockPos pos = new BlockPos(1, 0, 0);
        mc.objectMouseOver = new MovingObjectPosition(new Vec3(1, .5, .5), EnumFacing.WEST, pos);
        assertTrue(RotationUtil.lookingAtBlock(pos, 0, -90, EnumFacing.WEST, true));
        assertTrue(RotationUtil.lookingAtBlock(pos, 150, 76, EnumFacing.EAST, false));
        assertFalse(RotationUtil.lookingAtBlock(pos, 150, 76, EnumFacing.EAST, true));
        assertEquals(76, RotationUtil.getYawBasedPitch(pos, EnumFacing.EAST, 150, 76, 84), 0);
    }

    @Test public void moveFixUsesSourceKeyCorrectionOnceAcrossAllVibePolicies() throws Exception {
        ((BooleanSetting) setting("Safe Walk")).setEnabled(false);
        MoveInputEvent keys = new MoveInputEvent(1, 0, false, true, .3, true, false, false, false, false, true);
        for (float yaw : new float[] {0, 45, 90, 135, 180, -135, -90, -45}) {
            keys.setKeyForward(true); keys.setKeyBack(false); keys.setKeyLeft(false); keys.setKeyRight(false);
            MoveUtil.correctMovement(keys, yaw);
            double angle = Math.atan2(keys.getStrafe(), keys.getForward());
            assertEquals(0, Math.sin(Math.toRadians(yaw) - angle), 0.00001);
            assertEquals(1, Math.cos(Math.toRadians(yaw) - angle), 0.00001);
        }
        for (String policy : moveFix.getMode().getModes()) {
            moveFix.getMode().setValue(policy);
            moveFix.setScaffoldRotation(180, 76, true);
            assertEquals(0, MathHelper.wrapAngleTo180_float(invokeYaw() - 180), 0);
            moveFix.setScaffoldRotation(180, 76, false);
            assertEquals(0, invokeYaw(), 0);
            assertEquals(policy, moveFix.getMode().getValue());
        }
        moveFix.setFakeRotation("other", 90, 30);
        moveFix.finishScaffoldRotation();
        assertTrue(moveFix.isRotationOwner("other"));
    }

    @Test public void intaveJumpAndNcpTowerKeepOriginalPacketPosition() throws Exception {
        ((ModeSetting) setting("Tower")).setValue("Intave");
        KeyBinding.setKeyBindState(57, true);
        assertEquals(.41F, ScaffoldModule.jumpMotionHook(.42F, player), 0);
        assertEquals(.42F, ScaffoldModule.jumpMotionHook(.42F, new Object()), 0);
        ((ModeSetting) setting("Tower")).setValue("NCP");
        player.setPosition(.5, 2.001, .5);
        ScaffoldModule.beforeMotionHook(player);
        assertEquals(2.001, player.posY, .000001);
        assertEquals(.41998, player.motionY, .000001);
        ScaffoldModule.afterMotionHook(player);
        assertEquals(2, player.posY, 0);
    }

    @Test public void realMoveFixInputWrapperDoesNotDoubleCorrectOrDoubleSlowSneaking() throws Exception {
        Class<?> keyboard = org.lwjgl.input.Keyboard.class;
        Field created = field(keyboard, "created");
        boolean previouslyCreated = created.getBoolean(null);
        java.nio.ByteBuffer keyBuffer = (java.nio.ByteBuffer) field(keyboard, "keyDownBuffer").get(null);
        byte previousForward = keyBuffer.get(17);
        try {
            created.setBoolean(null, true); keyBuffer.put(17, (byte) 1);
            KeyBinding.setKeyBindState(17, true); KeyBinding.setKeyBindState(42, true);
            ((BooleanSetting) setting("Safe Walk")).setEnabled(false);
            dev.vibe.module.impl.scaffold.RotationComponent rotations =
                    (dev.vibe.module.impl.scaffold.RotationComponent) field(ScaffoldModule.class, "rotationComponent").get(scaffold);
            rotations.setRotations(180, 76, 180, 180, true);
            moveFix.getMode().setValue("Silent");
            moveFix.installInputHook();
            player.movementInput.updatePlayerMoveState();
            assertEquals(-.3F, player.movementInput.moveForward, .00001F);
            assertEquals(0, player.movementInput.moveStrafe, 0);
            assertTrue(player.movementInput.sneak);
            assertTrue(moveFix.isRotationOwner("scaffold"));
            assertEquals("Silent", moveFix.getMode().getValue());
        } finally { created.setBoolean(null, previouslyCreated); keyBuffer.put(17, previousForward); }
    }

    private float invokeYaw() throws Exception {
        Method method = MoveFixModule.class.getDeclaredMethod("movementYaw", Object.class, float.class);
        method.setAccessible(true); return (float) method.invoke(moveFix, player, 0F);
    }
    private Setting<?> setting(String name) {
        for (Setting<?> setting : scaffold.getSettings()) if (setting.getName().equals(name)) return setting;
        throw new AssertionError(name);
    }
    private static Field field(Class<?> type, String name) throws Exception { Field f = type.getDeclaredField(name); f.setAccessible(true); return f; }
    private static <T> T allocate(Class<T> type) throws Exception { Class<?> u = Class.forName("sun.misc.Unsafe"); return type.cast(u.getMethod("allocateInstance", Class.class).invoke(field(u, "theUnsafe").get(null), type)); }
    public static final class Player extends EntityPlayerSP {
        int swings;
        private Player() { super(null, null, null, null); }
        @Override public void swingItem() { swings++; }
        @Override public void addChatMessage(net.minecraft.util.IChatComponent message) { }
        @Override public boolean isSneaking() { return false; }
    }
    public static final class Controller extends PlayerControllerMP {
        int placements;
        private Controller() { super(null, null); }
        @Override public boolean onPlayerRightClick(EntityPlayerSP player, WorldClient world, ItemStack item, BlockPos pos, EnumFacing face, Vec3 hit) { placements++; return true; }
    }
    public static final class World extends WorldClient {
        Map<BlockPos, IBlockState> blocks;
        private World() { super(null, null, 0, null, null); }
        @Override public IBlockState getBlockState(BlockPos pos) { return blocks.getOrDefault(pos, Blocks.air.getDefaultState()); }
    }
}

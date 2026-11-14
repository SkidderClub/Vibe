package dev.vibe.movement;

import dev.vibe.Vibe;
import dev.vibe.combat.SilentMurderPacketsTest;
import dev.vibe.core.RewoundTransformer;
import dev.vibe.module.Module;
import dev.vibe.module.ModuleManager;
import dev.vibe.module.impl.combat.KillAuraModule;
import dev.vibe.module.impl.combat.TargetsModule;
import dev.vibe.module.impl.movement.*;
import dev.vibe.path.PathTestFixture;
import dev.vibe.setting.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.*;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.*;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.potion.*;
import net.minecraft.util.*;
import org.junit.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import static org.junit.Assert.*;

/** Native packet writers and reference values from the uploaded Exire REWOUND/NCP/Vanilla branches. */
public class RewoundModesTest implements Opcodes {
    @Test public void disabledHooksPreserveEveryNativeWalkingVariantByteForByte() throws Exception {
        assertEquals(walkingTrace(false), walkingTrace(true));
    }

    private List<String> walkingTrace(boolean hooked) throws Exception {
        List<String> trace = new ArrayList<>();
        try (Fixture f = new Fixture(hooked)) {
            Vibe.getInstance().getModuleManager().registerDynamic(new dev.vibe.module.impl.movement.StepModule());
            Vibe.getInstance().getModuleManager().registerDynamic(new dev.vibe.module.impl.movement.AntiVoidModule());
            Vibe.getInstance().getModuleManager().registerDynamic(new dev.vibe.module.impl.combat.CriticalsModule());
            for (int i = 0; i < 64; i++) {
                if (i % 4 == 0) f.position(64 + i * .01);
                if (i % 3 == 0) f.player.rotationYaw += 17.35F;
                f.player.onGround = i % 2 == 0;
                f.player.fallDistance = i;
                f.queue.packets.clear(); RewoundHooks.preUpdate(f.player); f.player.onUpdateWalkingPlayer();
                for (Packet<?> packet : f.queue.packets) {
                    PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());
                    try {
                        packet.writePacketData(buffer); StringBuilder value = new StringBuilder(packet.getClass().getSimpleName());
                        while (buffer.isReadable()) value.append(String.format(Locale.ROOT, "%02x", buffer.readUnsignedByte()));
                        trace.add(value.toString());
                    } finally { buffer.release(); }
                }
                assertEquals(i, f.player.fallDistance, 0);
            }
        }
        return trace;
    }

    @Test public void nativeMoveOverrideUsesTheImportedVectorAndLeavesDisabledMotionUntouched() throws Exception {
        try (Fixture f = new Fixture()) {
            f.player.moveEntity(.125, -.08, .25);
            assertArrayEquals(new double[] {.125, -.08, .25}, f.player.lastMove, 0);
            select(f.speed, "Mode", "Rewound"); f.speed.setEnabled(true);
            RewoundSpeed state = (RewoundSpeed) field(SpeedModule.class, "rewound").get(f.speed);
            field(RewoundSpeed.class, "lastSetback").setLong(state, System.nanoTime() / 1000000L - 1000);
            f.player.moveEntity(.125, -.08, .25);
            assertEquals(.407, f.player.lastMove[1], 0);
            assertEquals(.612521, f.player.lastMove[2], 1E-12);
            f.speed.setEnabled(false);
            f.player.moveEntity(.125, -.08, .25);
            assertArrayEquals(new double[] {.125, -.08, .25}, f.player.lastMove, 0);
        }
    }

    @Test public void referenceHopUsesTheOriginalStageSpeedsAndJumpBoost() throws Exception {
        try (Fixture f = new Fixture()) {
            long[] now = {0}; RewoundSpeed speed = new RewoundSpeed(() -> now[0]); now[0] = 1000;
            double[] move = speed.move(f.player, 0, 0, 0, false, "Off");
            assertEquals(.407, move[1], 0); assertEquals(.612521, move[2], 1E-12);
            assertEquals(1, f.player.jumps);
            assertEquals("Requested strafe must not overwrite vanilla motion", .017, f.player.motionZ, 0);
            f.player.onGround = false; f.position(64.407);
            double[] reference = {.407316, .400036, .396396, .392756, .389116, .385476};
            for (double expected : reference) assertEquals(expected, speed.move(f.player, 0, .2, 0, false, "Off")[2], 1E-12);
            speed.reset(f.player); f.position(64); f.player.onGround = true;
            f.player.effects.put(Potion.jump.id, new PotionEffect(Potion.jump.id, 100, 1));
            assertEquals(.607, speed.move(f.player, 0, 0, 0, false, "Off")[1], 1E-12);
            f.player.effects.put(Potion.moveSpeed.id, new PotionEffect(Potion.moveSpeed.id, 100, 0));
            speed.reset(f.player);
            assertEquals((.64 + 1.028 * .134 + .0331) * .91, speed.move(f.player, 0, 0, 0, false, "Off")[2], 1E-12);
        }
    }

    @Test public void referenceCorrectionsRespectCooldownAndRelativeRotationFlags() throws Exception {
        try (Fixture f = new Fixture()) {
            long[] now = {0}; RewoundSpeed speed = new RewoundSpeed(() -> now[0]); now[0] = 1000;
            f.player.rotationYaw = 83; f.player.rotationPitch = -17;
            S08PacketPlayerPosLook absolute = correction(Collections.<S08PacketPlayerPosLook.EnumFlags>emptySet());
            speed.correction(f.player, absolute);
            assertEquals(83, absolute.getYaw(), 0); assertEquals(-17, absolute.getPitch(), 0);
            assertEquals("The setback restarts at the unboosted ground speed", .2873,
                    Math.hypot(speed.move(f.player, 0, 0, 0, false, "Off")[0], speed.move(f.player, 0, 0, 0, false, "Off")[2]), 1E-12);
            S08PacketPlayerPosLook soon = correction(Collections.<S08PacketPlayerPosLook.EnumFlags>emptySet());
            now[0] += 100; speed.correction(f.player, soon);
            assertEquals(12, soon.getYaw(), 0); assertEquals(9, soon.getPitch(), 0);
            now[0] += 301;
            S08PacketPlayerPosLook relative = correction(EnumSet.of(S08PacketPlayerPosLook.EnumFlags.Y_ROT, S08PacketPlayerPosLook.EnumFlags.X_ROT));
            speed.correction(f.player, relative);
            assertEquals(0, relative.getYaw(), 0); assertEquals(0, relative.getPitch(), 0);
            assertEquals(EnumSet.of(S08PacketPlayerPosLook.EnumFlags.Y_ROT, S08PacketPlayerPosLook.EnumFlags.X_ROT), relative.func_179834_f());
            assertEquals(2, relative.getX(), 0); assertEquals(64, relative.getY(), 0);
        }
    }

    @Test public void noInputDoesNotInventMovementAndDisabledModesHaveNoHooks() throws Exception {
        try (Fixture f = new Fixture()) {
            assertNull(RewoundHooks.move(f.player, .1, -.08, .2));
            f.player.fallDistance = 4;
            RewoundHooks.beforeWalking(f.player);
            assertFalse(RewoundHooks.ground(f.player, false));
            RewoundHooks.afterWalking(f.player);
            assertEquals(4, f.player.fallDistance, 0); assertTrue(f.queue.packets.isEmpty());
            select(f.speed, "Mode", "Rewound"); f.speed.setEnabled(true);
            f.player.movementInput.moveForward = f.player.moveForward = 0;
            double[] move = RewoundHooks.move(f.player, .1, -.08, .2);
            assertEquals(.1, move[0], 0); assertEquals(-.08, move[1], 0); assertEquals(.2, move[2], 0);
            assertEquals(1.2F, dev.vibe.combat.CombatTimerAccess.getSpeed(), 0);
            select(f.speed, "Mode", "Legit");
            assertNull(RewoundHooks.move(f.player, .1, -.08, .2));
            assertEquals(1F, dev.vibe.combat.CombatTimerAccess.getSpeed(), 0);
            select(f.speed, "Mode", "Rewound"); RewoundHooks.move(f.player, 0, 0, 0);
            f.mc.thePlayer = null; f.speed.maintainRewound();
            assertEquals("Timer must also reset after leaving the world", 1F, dev.vibe.combat.CombatTimerAccess.getSpeed(), 0);
        }
    }

    @Test public void inputCompensationAndJumpOwnershipEndAsSoonAsTheModesAreDisabled() throws Exception {
        try (Fixture f = new Fixture()) {
            f.player.movementInput = new MovementInput() {
                @Override public void updatePlayerMoveState() { moveForward = 1; moveStrafe = -.3F; jump = true; sneak = true; }
            };
            f.slow.installInputHook(); select(f.slow, "Mode", "Rewound"); f.slow.setEnabled(true);
            f.player.using = f.player.blocking = true;
            f.player.movementInput.updatePlayerMoveState();
            assertEquals(1, f.player.movementInput.moveForward * .2F, 0);
            assertEquals(-.3F, f.player.movementInput.moveStrafe * .2F, 1E-7F);
            assertTrue(f.player.movementInput.jump); assertTrue(f.player.movementInput.sneak);
            select(f.speed, "Mode", "Rewound"); f.speed.setEnabled(true);
            f.player.movementInput.updatePlayerMoveState(); assertFalse(f.player.movementInput.jump);
            f.speed.setEnabled(false); f.slow.setEnabled(false);
            f.player.movementInput.updatePlayerMoveState();
            assertEquals(1, f.player.movementInput.moveForward, 0); assertEquals(-.3F, f.player.movementInput.moveStrafe, 0);
            assertTrue(f.player.movementInput.jump); assertTrue(f.player.movementInput.sneak);
        }
    }

    @Test public void failedWalkingClearsFallAndReblockScopes() throws Exception {
        for (boolean beforeMovement : new boolean[] {false, true}) try (Fixture f = new Fixture()) {
            select(f.slow, "Mode", "Rewound"); f.slow.setEnabled(true); f.player.blocking = true;
            select(f.fall, "Mode", "Rewound"); f.fall.setEnabled(true); f.player.fallDistance = 4;
            f.queue.failMovement = !beforeMovement; f.queue.failRelease = beforeMovement;
            try { f.player.onUpdateWalkingPlayer(); fail("Expected the transport fixture failure"); }
            catch (IllegalStateException expected) { assertEquals("fixture transport", expected.getMessage()); }
            f.slow.afterWalking(f.player);
            if (beforeMovement) assertTrue(f.queue.packets.isEmpty());
            else assertSequence(f.queue.packets, C07PacketPlayerDigging.class);
            assertFalse(RewoundHooks.ground(f.player, false));
        }
    }

    @Test public void modeNamesAndOptionsRoundTripThroughTheRealConfigSystem() throws Exception {
        try (Fixture f = new Fixture()) {
            select(f.aura, "Autoblock Mode", "Rewound");
            for (Module m : new Module[] {f.speed, f.slow, f.fall}) select(m, "Mode", "Rewound");
            select(f.speed, "Fast fall", "Snap");
            File directory = java.nio.file.Files.createTempDirectory(java.nio.file.Paths.get("build/exire-port"), "config-").toFile();
            dev.vibe.config.VibeConfig config = new dev.vibe.config.VibeConfig(directory);
            ModuleManager modules = Vibe.getInstance().getModuleManager();
            assertTrue(config.save("rewound", modules));
            select(f.aura, "Autoblock Mode", "None");
            select(f.speed, "Mode", "Legit"); select(f.slow, "Mode", "Vanilla"); select(f.fall, "Mode", "No Ground");
            select(f.speed, "Fast fall", "Off");
            assertTrue(config.load("rewound", modules));
            for (Module m : new Module[] {f.aura, f.speed, f.slow, f.fall}) {
                ModeSetting mode = (ModeSetting) m.getSettings().stream().filter(s -> s.getRawName().equals(m == f.aura ? "Autoblock Mode" : "Mode")).findFirst().get();
                assertEquals("Rewound", mode.getValue());
                assertFalse("Loading this disabled preset must not enable its modules", m.isEnabled());
            }
            assertEquals("Snap", ((ModeSetting) f.speed.getSettings().stream().filter(s -> s.getRawName().equals("Fast fall")).findFirst().get()).getValue());
        }
    }

    @Test public void fastFallAndCollisionAndHalfBlockStepUseSourceTiming() throws Exception {
        try (Fixture f = new Fixture()) {
            long[] now = {0}; RewoundSpeed speed = new RewoundSpeed(() -> now[0]); now[0] = 1000;
            speed.move(f.player, 0, 0, 0, true, "Snap");
            assertEquals(1.2F, dev.vibe.combat.CombatTimerAccess.getSpeed(), 0);
            f.position(64.5); f.player.onGround = false;
            for (int i = 1; i < 5; i++) assertEquals(.2, speed.move(f.player, 0, .2, 0, true, "Snap")[1], 0);
            assertEquals(-.18517061160392762, speed.move(f.player, 0, .2, 0, true, "Snap")[1], 0);
            speed.afterMove(f.player, new double[] {0, -.08, 0, 64});
            f.position(64); f.player.onGround = true;
            assertEquals("A half-block step suppresses the next hop", -.08, speed.move(f.player, 0, -.08, 0, true, "Off")[1], 0);
            f.player.isCollidedHorizontally = true;
            speed.move(f.player, 0, 0, 0, true, "Off");
            assertEquals(1F, dev.vibe.combat.CombatTimerAccess.getSpeed(), 0);
            speed.releaseTimer();
        }
    }

    @Test public void nativeWalkingSpoofsOnlyTheSelectedFallingUpdateWithoutChangingPhysics() throws Exception {
        try (Fixture f = new Fixture()) {
            select(f.fall, "Mode", "Rewound"); f.fall.setEnabled(true);
            for (float distance : new float[] {0, 2, 2.01F, 0, 3.5F}) {
                f.player.fallDistance = distance; f.player.onGround = false; f.queue.packets.clear();
                f.player.onUpdateWalkingPlayer();
                assertEquals(distance > 2, ((C03PacketPlayer) f.queue.packets.get(0)).isOnGround());
                assertFalse("A packet ground spoof must never change physical ground", f.player.onGround);
                assertEquals(distance > 2 ? 0 : distance, f.player.fallDistance, 0);
                assertFalse("Ground spoof must not leak to another packet", RewoundHooks.ground(f.player, false));
            }
            f.fall.setEnabled(false); f.player.fallDistance = 4; f.queue.packets.clear();
            f.player.onUpdateWalkingPlayer();
            assertFalse(((C03PacketPlayer) f.queue.packets.get(0)).isOnGround());
            assertEquals(4, f.player.fallDistance, 0);
        }
    }

    @Test public void nativeWalkingNoSlowPacketsEncloseMovementAndRespectItemFilters() throws Exception {
        try (Fixture f = new Fixture()) {
            select(f.slow, "Mode", "Rewound"); f.slow.setEnabled(true);
            f.player.using = f.player.blocking = true;
            f.player.onUpdateWalkingPlayer();
            assertSequence(f.queue.packets, C07PacketPlayerDigging.class, C03PacketPlayer.class, C08PacketPlayerBlockPlacement.class);
            C07PacketPlayerDigging release = (C07PacketPlayerDigging) f.queue.packets.get(0);
            assertEquals(C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, release.getStatus());
            assertEquals(BlockPos.ORIGIN, release.getPosition()); assertEquals(EnumFacing.DOWN, release.getFacing());
            C08PacketPlayerBlockPlacement block = (C08PacketPlayerBlockPlacement) f.queue.packets.get(2);
            assertEquals(new BlockPos(-1, -1, -1), block.getPosition()); assertEquals(255, block.getPlacedBlockDirection());
            assertTrue(ItemStack.areItemStacksEqual(f.player.getHeldItem(), block.getStack()));
            f.slow.getItems().toggle("Swords"); f.queue.packets.clear();
            f.player.onUpdateWalkingPlayer(); assertSequence(f.queue.packets, C03PacketPlayer.class);
            f.player.inventory.mainInventory[0] = new ItemStack(Items.bread); f.player.blocking = false; f.queue.packets.clear();
            assertTrue(f.slow.applies()); f.player.onUpdateWalkingPlayer(); assertSequence(f.queue.packets, C03PacketPlayer.class);
            f.player.inventory.mainInventory[0] = new ItemStack(Items.potionitem, 1, 16384);
            assertFalse("Splash potions are excluded by Exire", f.slow.applies());
        }
    }

    @Test public void noSlowDoesNotReblockAChangedSlotOrAfterDisable() throws Exception {
        try (Fixture f = new Fixture()) {
            select(f.slow, "Mode", "Rewound"); f.slow.setEnabled(true); f.player.blocking = true;
            f.slow.beforeWalking(f.player); f.player.inventory.currentItem = 1; f.slow.afterWalking(f.player);
            assertSequence(f.queue.packets, C07PacketPlayerDigging.class);
            f.player.inventory.currentItem = 0; f.queue.packets.clear();
            f.slow.beforeWalking(f.player); f.slow.setEnabled(false); f.slow.afterWalking(f.player);
            assertSequence(f.queue.packets, C07PacketPlayerDigging.class);
        }
    }

    @Test public void auraPostHitBlocksAndNextNoSlowUpdateWrapsItsServerSideBlock() throws Exception {
        try (Fixture f = new Fixture()) {
            select(f.aura, "Autoblock Mode", "Rewound"); f.aura.setEnabled(true);
            Player target = PathTestFixture.allocate(Player.class); target.effects = new HashMap<>(); target.worldObj = f.world;
            health(target);
            target.setEntityBoundingBox(new AxisAlignedBB(-.3, 64, 1.7, .3, 65.8, 2.3)); target.posY = 64; target.posZ = 2; target.setEntityId(42);
            PathTestFixture.set(KillAuraModule.class, f.aura, "target", target);
            f.mc.playerController.updateController(); f.queue.packets.clear();
            f.player.onUpdateWalkingPlayer();
            assertSequence(f.queue.packets, C03PacketPlayer.class, C0APacketAnimation.class, C02PacketUseEntity.class, C08PacketPlayerBlockPlacement.class);
            assertTrue(f.aura.isRewoundBlocking()); assertFalse(f.player.isUsingItem());
            select(f.slow, "Mode", "Rewound"); f.slow.setEnabled(true); f.queue.packets.clear();
            f.player.onUpdateWalkingPlayer();
            assertTrue(f.queue.packets.get(0) instanceof C07PacketPlayerDigging);
            assertTrue(f.queue.packets.get(1) instanceof C03PacketPlayer);
            assertTrue(f.queue.packets.get(2) instanceof C08PacketPlayerBlockPlacement);
            f.queue.packets.clear(); f.aura.setEnabled(false);
            assertSequence(f.queue.packets, C07PacketPlayerDigging.class);
            assertFalse(f.aura.isRewoundBlocking());
        }
    }

    private static S08PacketPlayerPosLook correction(Set<S08PacketPlayerPosLook.EnumFlags> flags) { return new S08PacketPlayerPosLook(2, 64, 3, 12, 9, flags); }
    private static void select(Module module, String name, String value) {
        for (Setting<?> setting : module.getSettings()) if (setting.getRawName().equals(name)) { ((ModeSetting) setting).setValue(value); return; }
        throw new AssertionError(name);
    }
    private static void assertSequence(List<Packet<?>> packets, Class<?>... types) {
        assertEquals(packets.toString(), types.length, packets.size());
        for (int i = 0; i < types.length; i++) assertTrue("Packet " + i + ": " + packets, types[i].isInstance(packets.get(i)));
    }

    static final class Fixture implements AutoCloseable {
        final Minecraft previous = Minecraft.getMinecraft(); final Vibe previousVibe = Vibe.getInstance();
        final Minecraft mc; final FixtureWorld world; final Player player; final Connection queue;
        final SpeedModule speed; final NoFallModule fall; final NoSlowModule slow; final KillAuraModule aura;
        Fixture() throws Exception { this(true); }
        Fixture(boolean hooked) throws Exception {
            dev.vibe.camera.DroneCollisionsTest.registerBlocks();
            mc = PathTestFixture.allocate(Minecraft.class); PathTestFixture.set(Minecraft.class, null, "theMinecraft", mc);
            mc.gameSettings = new GameSettings();
            net.minecraft.util.Timer timer = PathTestFixture.allocate(net.minecraft.util.Timer.class); timer.timerSpeed = 1;
            PathTestFixture.set(Minecraft.class, mc, "timer", timer);
            world = PathTestFixture.allocate(FixtureWorld.class); mc.theWorld = world;
            world.loaded = true; world.underBlock = net.minecraft.init.Blocks.stone.getDefaultState(); world.obstacles = new ArrayList<>();
            player = nativePlayer(hooked); mc.thePlayer = player; player.worldObj = world; player.effects = new HashMap<>();
            health(player);
            player.movementInput = new MovementInput(); player.movementInput.moveForward = player.moveForward = 1;
            player.inventory = new InventoryPlayer(player); player.inventory.mainInventory[0] = new ItemStack(Items.iron_sword);
            player.capabilities = new PlayerCapabilities(); player.width = .6F; player.height = 1.8F; player.onGround = player.isCollidedVertically = true;
            player.motionZ = .017; player.setEntityId(1); position(64);
            PathTestFixture.set(Minecraft.class, mc, "renderViewEntity", player);
            queue = PathTestFixture.allocate(Connection.class); queue.packets = new ArrayList<>();
            PathTestFixture.set(EntityPlayerSP.class, player, "sendQueue", queue);
            for (Field field : player.getClass().getDeclaredFields()) {
                if (field.getName().equals("sendQueue")) { field.setAccessible(true); field.set(player, queue); }
                if (field.getName().equals("mc")) { field.setAccessible(true); field.set(player, mc); }
            }
            PathTestFixture.set(EntityPlayerSP.class, player, "mc", mc);
            mc.playerController = SilentMurderPacketsTest.nativeController(mc, queue);
            Vibe vibe = new Vibe(); PathTestFixture.set(Vibe.class, null, "instance", vibe);
            ModuleManager modules = PathTestFixture.allocate(ModuleManager.class); PathTestFixture.set(ModuleManager.class, modules, "modules", new ArrayList<Module>());
            PathTestFixture.set(Vibe.class, vibe, "moduleManager", modules);
            speed = new SpeedModule(); fall = new NoFallModule(); slow = new NoSlowModule(); aura = new KillAuraModule();
            modules.registerDynamic(speed); modules.registerDynamic(fall); modules.registerDynamic(slow); modules.registerDynamic(aura); modules.registerDynamic(new TargetsModule());
        }
        void position(double y) { player.posY = y; player.setEntityBoundingBox(new AxisAlignedBB(-.3, y, -.3, .3, y + 1.8, .3)); }
        @Override public void close() throws Exception {
            speed.setEnabled(false); aura.setEnabled(false);
            PathTestFixture.set(Minecraft.class, null, "theMinecraft", previous); PathTestFixture.set(Vibe.class, null, "instance", previousVibe);
        }
    }

    /** Keep the actual transformed walking bytecode, inheriting deterministic test-only entity responses. */
    private static Player nativePlayer(boolean hooked) throws Exception {
        String original = "net/minecraft/client/entity/EntityPlayerSP", name = "dev/vibe/movement/NativeRewoundPlayer";
        byte[] bytes;
        try (InputStream in = EntityPlayerSP.class.getResourceAsStream("EntityPlayerSP.class"); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; for (int n; (n = in.read(buffer)) != -1;) out.write(buffer, 0, n); bytes = out.toByteArray();
        }
        ClassNode node = new ClassNode(); new ClassReader(hooked ? new RewoundTransformer().transform(original.replace('/', '.'), original.replace('/', '.'), bytes) : bytes).accept(node, 0);
        node.name = name; node.superName = org.objectweb.asm.Type.getInternalName(Player.class);
        for (Iterator<?> it = node.methods.iterator(); it.hasNext();) {
            MethodNode method = (MethodNode) it.next();
            if (!method.name.equals("onUpdateWalkingPlayer") && !method.name.equals("vibe$rewound$onUpdateWalkingPlayer") && !method.name.equals("isCurrentViewEntity") && !method.name.equals("moveEntity")) it.remove();
        }
        for (Object value : node.methods) {
            MethodNode method = (MethodNode) value;
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (instruction instanceof FieldInsnNode && ((FieldInsnNode) instruction).owner.equals(original)) ((FieldInsnNode) instruction).owner = name;
                if (instruction instanceof MethodInsnNode && ((MethodInsnNode) instruction).owner.equals(original)) ((MethodInsnNode) instruction).owner = name;
                if (method.name.equals("moveEntity") && instruction instanceof MethodInsnNode && instruction.getOpcode() == INVOKESPECIAL) ((MethodInsnNode) instruction).owner = node.superName;
                if (instruction instanceof FrameNode) {
                    FrameNode frame = (FrameNode) instruction;
                    if (frame.local != null) Collections.replaceAll(frame.local, original, name);
                    if (frame.stack != null) Collections.replaceAll(frame.stack, original, name);
                }
            }
        }
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer); final byte[] result = writer.toByteArray();
        Class<?> type = new ClassLoader(RewoundModesTest.class.getClassLoader()) { Class<?> define() { return defineClass(name.replace('/', '.'), result, 0, result.length); } }.define();
        return (Player) PathTestFixture.allocate(type);
    }

    public static class Player extends EntityPlayerSP {
        Map<Integer, PotionEffect> effects; boolean using, blocking; int jumps; double[] lastMove;
        protected Player() { super(null, null, null, null); }
        @Override public boolean isSprinting() { return false; }
        @Override public boolean isSneaking() { return false; }
        @Override public boolean isSpectator() { return false; }
        @Override public boolean isInLava() { return false; }
        @Override public boolean isEntityAlive() { return true; }
        @Override public boolean canBeCollidedWith() { return true; }
        @Override public boolean canEntityBeSeen(Entity entity) { return true; }
        @Override public float getAbsorptionAmount() { return 0; }
        @Override public boolean isUsingItem() { return using; }
        @Override public boolean isBlocking() { return blocking; }
        @Override public ItemStack getItemInUse() { return using ? getHeldItem() : null; }
        @Override public boolean isPotionActive(Potion potion) { return effects.containsKey(potion.id); }
        @Override public PotionEffect getActivePotionEffect(Potion potion) { return effects.get(potion.id); }
        @Override public void jump() { jumps++; motionY = .42; }
        @Override public void moveEntity(double x, double y, double z) { lastMove = new double[] {x, y, z}; }
        @Override public void attackTargetEntityWithCurrentItem(Entity target) { }
    }
    public static class Connection extends NetHandlerPlayClient {
        List<Packet<?>> packets; boolean failMovement, failRelease;
        protected Connection() { super(null, null, null, null); }
        @Override public void addToSendQueue(Packet packet) {
            if (failMovement && packet instanceof C03PacketPlayer || failRelease && packet instanceof C07PacketPlayerDigging) throw new IllegalStateException("fixture transport");
            packets.add(packet);
        }
    }
    public static class FixtureWorld extends WorldClient {
        boolean loaded; net.minecraft.block.state.IBlockState underBlock; List<AxisAlignedBB> obstacles;
        protected FixtureWorld() { super(null, null, 0, null, null); }
        @Override public List<AxisAlignedBB> getCollidingBoundingBoxes(Entity entity, AxisAlignedBB box) {
            List<AxisAlignedBB> boxes = new ArrayList<>();
            if (box.minY < 64 && box.maxY > 63) boxes.add(new AxisAlignedBB(-100, 63, -100, 100, 64, 100));
            for (AxisAlignedBB obstacle : obstacles) if (obstacle.intersectsWith(box)) boxes.add(obstacle);
            return boxes;
        }
        @Override public boolean isBlockLoaded(BlockPos pos) { return loaded; }
        @Override public net.minecraft.block.state.IBlockState getBlockState(BlockPos pos) { return pos.getY() == 63 ? underBlock : net.minecraft.init.Blocks.air.getDefaultState(); }
        @Override public List<Entity> getEntitiesWithinAABBExcludingEntity(Entity entity, AxisAlignedBB box) { return Collections.emptyList(); }
        @Override public MovingObjectPosition rayTraceBlocks(Vec3 from, Vec3 to, boolean liquids, boolean ignore, boolean last) { return null; }
    }
    private static void health(Player player) throws Exception {
        net.minecraft.entity.DataWatcher watcher = new net.minecraft.entity.DataWatcher(player);
        watcher.addObject(0, (byte) 0);
        watcher.addObject(6, 20F);
        PathTestFixture.set(Entity.class, player, "dataWatcher", watcher);
    }
    private static Field field(Class<?> type, String name) throws Exception { Field field = type.getDeclaredField(name); field.setAccessible(true); return field; }
}

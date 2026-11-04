package dev.vibe.notebot;

import dev.vibe.Vibe;
import dev.vibe.command.CommandManager;
import dev.vibe.event.ClientEvents;
import dev.vibe.module.Module;
import dev.vibe.module.ModuleManager;
import dev.vibe.module.impl.movement.MoveFixModule;
import dev.vibe.module.impl.world.NoteBotModule;
import dev.vibe.path.PathTestFixture;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.entity.player.PlayerCapabilities;
import net.minecraft.init.Blocks;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityNote;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.MovementInput;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraft.world.border.WorldBorder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static dev.vibe.path.PathTestFixture.allocate;
import static dev.vibe.path.PathTestFixture.set;
import static org.junit.Assert.*;

public class NoteBotPlaybackTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    @Test public void scansAndTunesThroughVanillaAfterTheMoveFixWalkingRotation() throws Exception {
        try (Fixture f = new Fixture()) {
            CommandManager commands = new CommandManager();
            assertEquals(java.util.Collections.singletonList(".notebot play Test Song.nbs"),
                    commands.getSuggestions(".notebot play T"));
            assertTrue(commands.execute(".notebot play Test Song"));
            for (int i = 0; i < 200 && f.digs() == 0; i++) {
                f.module.tickStart();
                assertEquals(0, f.digs());
                f.walk();
                Thread.sleep(2);
            }
            assertEquals(2, f.digs());
            assertTrue(f.queue.packets.get(0) instanceof C03PacketPlayer);
            assertTrue(f.queue.packets.stream().filter(p -> p instanceof C03PacketPlayer)
                    .map(p -> (C03PacketPlayer) p).anyMatch(p -> Math.abs(p.getYaw() + 90) < 1));
            assertEquals(0, f.player.rotationYaw, 0);
            assertFalse(f.mc.playerController.getIsHittingBlock());

            f.acknowledge(24);
            f.tick();
            f.tick();
            assertEquals(1, f.uses());
            f.tick();
            assertEquals("Tuning waits for the server's new pitch", 1, f.uses());
            assertEquals(2, f.digs());

            f.acknowledge(0);
            f.tick();
            f.module.tickStart();
            assertTrue(f.fix.ownsRotation(f.module.getId()));
            f.fix.setFakeRotation("another-module", 30, 0);
            f.walk();
            assertEquals(2, f.digs());

            f.fix.discardOwnedState("another-module");
            f.tick();
            assertEquals(4, f.digs());
            assertTrue(f.queue.packets.stream().filter(p -> p instanceof C07PacketPlayerDigging)
                    .map(p -> ((C07PacketPlayerDigging) p).getStatus())
                    .noneMatch(action -> action == C07PacketPlayerDigging.Action.STOP_DESTROY_BLOCK));

            f.module.stop();
            assertFalse(f.fix.ownsRotation(f.module.getId()));
            assertEquals(4, f.digs());
        }
    }

    @Test public void refusesToTapBlocksInCreative() throws Exception {
        try (Fixture f = new Fixture()) {
            f.player.capabilities.isCreativeMode = true;
            f.module.start("Test Song", false);
            f.tick();
            assertFalse(f.module.isEnabled());
            assertEquals(0, f.digs());
        }
    }

    private final class Fixture implements AutoCloseable {
        final Minecraft oldMc = Minecraft.getMinecraft();
        final Vibe oldVibe = Vibe.getInstance();
        final Minecraft mc = allocate(Minecraft.class);
        final Player player = allocate(Player.class);
        final NoteWorld world = allocate(NoteWorld.class);
        final Queue queue = allocate(Queue.class);
        final ClientEvents events = allocate(ClientEvents.class);
        final NoteBotModule module;
        final MoveFixModule fix;
        final Method walking;

        Fixture() throws Exception {
            dev.vibe.camera.DroneCollisionsTest.registerBlocks();
            set(Minecraft.class, null, "theMinecraft", mc);
            set(ClientEvents.class, events, "minecraft", mc);
            mc.gameSettings = new GameSettings();
            mc.inGameHasFocus = true;
            set(Minecraft.class, mc, "mcDataDir", folder.getRoot());
            world.note = new BlockPos(3, 0, 0);
            world.tile = new TileEntityNote();
            world.border = new WorldBorder();
            set(World.class, world, "isRemote", true);
            mc.theWorld = world;
            mc.thePlayer = player;
            player.worldObj = world;
            player.inventory = new InventoryPlayer(player);
            player.capabilities = new PlayerCapabilities();
            player.movementInput = new MovementInput();
            player.onGround = true;
            player.posX = player.posZ = .5;
            player.setEntityBoundingBox(new AxisAlignedBB(.2, 0, .2, .8, 1.8, .8));
            set(EntityLivingBase.class, player, "activePotionsMap", new HashMap<>());
            set(EntityPlayerSP.class, player, "sendQueue", queue);
            set(EntityPlayerSP.class, player, "mc", mc);
            set(Minecraft.class, mc, "renderViewEntity", player);
            queue.packets = new ArrayList<>();
            mc.playerController = new PlayerControllerMP(mc, queue);
            Vibe vibe = new Vibe();
            set(Vibe.class, null, "instance", vibe);
            ModuleManager modules = allocate(ModuleManager.class);
            set(ModuleManager.class, modules, "modules", new ArrayList<Module>());
            set(Vibe.class, vibe, "moduleManager", modules);
            module = new NoteBotModule();
            fix = new MoveFixModule();
            modules.registerDynamic(module);
            modules.registerDynamic(fix);
            Files.createDirectories(module.folder().toPath());
            Files.write(module.folder().toPath().resolve("Test Song.nbs"), NoteSongTest.fixture(0, 1000, 0, 33));
            walking = EntityPlayerSP.class.getDeclaredMethod("onUpdateWalkingPlayer");
            walking.setAccessible(true);
        }

        void tick() throws Exception {
            fix.beginRotationTick();
            module.tickStart();
            walk();
        }

        void walk() throws Exception {
            MoveFixModule.beginPacketRotationHook(player);
            walking.invoke(player);
            MoveFixModule.endPacketRotationHook(player);
        }

        void acknowledge(int pitch) {
            events.onNotePlayed(new net.minecraftforge.event.world.NoteBlockEvent.Play(
                    world, world.note, Blocks.noteblock.getDefaultState(), pitch, 0));
        }

        long digs() { return queue.packets.stream().filter(p -> p instanceof C07PacketPlayerDigging).count(); }
        long uses() { return queue.packets.stream().filter(p -> p instanceof C08PacketPlayerBlockPlacement).count(); }

        @Override public void close() throws Exception {
            module.stop();
            set(Minecraft.class, null, "theMinecraft", oldMc);
            set(Vibe.class, null, "instance", oldVibe);
        }
    }

    public static final class Player extends EntityPlayerSP {
        private Player() { super(null, null, null, null); }
        @Override public Vec3 getPositionEyes(float delta) { return new Vec3(posX, posY + 1.62, posZ); }
        @Override public void addChatMessage(IChatComponent message) { }
        @Override public boolean isEntityAlive() { return true; }
        @Override public boolean isSprinting() { return false; }
        @Override public boolean isSneaking() { return false; }
        @Override public boolean isSpectator() { return false; }
    }

    public static final class Queue extends NetHandlerPlayClient {
        private List<Packet<?>> packets;
        private Queue() { super(null, null, null, null); }
        @Override public void addToSendQueue(Packet packet) { packets.add(packet); }
    }

    public static final class NoteWorld extends WorldClient {
        private BlockPos note;
        private TileEntityNote tile;
        private WorldBorder border;
        private NoteWorld() { super(null, null, 0, null, null); }
        @Override public IBlockState getBlockState(BlockPos pos) {
            return (note.equals(pos) ? Blocks.noteblock : Blocks.air).getDefaultState();
        }
        @Override public TileEntity getTileEntity(BlockPos pos) { return note.equals(pos) ? tile : null; }
        @Override public boolean isBlockLoaded(BlockPos pos) { return true; }
        @Override public WorldBorder getWorldBorder() { return border; }
        @Override public void sendBlockBreakProgress(int player, BlockPos pos, int progress) { }
    }
}

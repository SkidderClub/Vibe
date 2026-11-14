package dev.vibe.movement;

import dev.vibe.Vibe;
import dev.vibe.combat.CombatTimerAccess;
import dev.vibe.module.Module;
import dev.vibe.module.impl.combat.CriticalsModule;
import dev.vibe.module.impl.movement.AntiVoidModule;
import dev.vibe.module.impl.movement.StepModule;
import dev.vibe.network.PacketDelayService;
import dev.vibe.path.PathTestFixture;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C03PacketPlayer.C04PacketPlayerPosition;
import net.minecraft.util.*;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.junit.Test;
import static org.junit.Assert.*;

public class RewoundExtraModesTest {
    @Test public void stepUsesTheConfiguredDelayExactOffsetsAndOldPositionBase() throws Exception {
        try (RewoundModesTest.Fixture f = new RewoundModesTest.Fixture()) {
            StepModule step = register(new StepModule()); step.mode.setValue("Rewound"); step.setEnabled(true);
            long[] now = {0}; RewoundStep state = new RewoundStep(() -> now[0]);
            PathTestFixture.set(StepModule.class, step, "rewound", state);
            step.rewoundPreUpdate(f.player);
            assertEquals(.6F, RewoundHooks.preStepHeight(f.player, .6F), 0);
            now[0] = 149; assertEquals(.6F, RewoundHooks.preStepHeight(f.player, .6F), 0);
            now[0] = 150; assertEquals(1F, RewoundHooks.preStepHeight(f.player, .6F), 0);
            assertEquals(.6F, f.player.stepHeight, 0);
            assertEquals(1F, RewoundHooks.stepHeight(f.player, f.player.stepHeight), 0);
            f.player.setEntityBoundingBox(f.player.getEntityBoundingBox().offset(.2, 1, .3));
            RewoundHooks.postStep(f.player);
            assertEquals(2, f.queue.packets.size());
            C04PacketPlayerPosition first = (C04PacketPlayerPosition) f.queue.packets.get(0), second = (C04PacketPlayerPosition) f.queue.packets.get(1);
            assertEquals(64 + (double) .42F, first.getPositionY(), 0);
            assertEquals(64 + .7531999805212024, second.getPositionY(), 0);
            assertEquals(0, first.getPositionX(), 0); assertEquals(0, first.getPositionZ(), 0);
            assertFalse(first.isOnGround()); assertFalse(second.isOnGround());
            assertEquals(64, f.player.posY, 0); assertEquals(1, CombatTimerAccess.getSpeed(), 0);
            step.setEnabled(false); assertEquals(.6F, f.player.stepHeight, 0);
            assertEquals(.85F, RewoundHooks.preStepHeight(f.player, .85F), 0);
        }
    }

    @Test public void stepTimerAndItsDelayWindowFollowExiresPreUpdateCounter() throws Exception {
        try (RewoundModesTest.Fixture f = new RewoundModesTest.Fixture()) {
            StepModule step = register(new StepModule()); step.mode.setValue("Rewound"); step.timer.setEnabled(true); step.setEnabled(true);
            long[] now = {0}; PathTestFixture.set(StepModule.class, step, "rewound", new RewoundStep(() -> now[0]));
            step.rewoundPreUpdate(f.player); now[0] = 150;
            assertEquals(1, RewoundHooks.preStepHeight(f.player, .6F), 0);
            f.player.setEntityBoundingBox(f.player.getEntityBoundingBox().offset(0, 1, 0)); RewoundHooks.postStep(f.player);
            assertEquals(.5F, CombatTimerAccess.getSpeed(), 0);
            now[0] = 200; step.rewoundPreUpdate(f.player); assertEquals(1, CombatTimerAccess.getSpeed(), 0);
            now[0] = 250; step.rewoundPreUpdate(f.player);
            now[0] = 399; assertEquals(.6F, RewoundHooks.preStepHeight(f.player, .6F), 0);
            now[0] = 400; assertEquals(1, RewoundHooks.preStepHeight(f.player, .6F), 0);
            step.setEnabled(false);
        }
    }

    @Test public void stepPausesSpeedAheadAndReleasesItAfterThePostStepWindow() throws Exception {
        try (RewoundModesTest.Fixture f = new RewoundModesTest.Fixture()) {
            StepModule step = register(new StepModule()); step.mode.setValue("Rewound"); step.delay.setValue(0D); step.setEnabled(true);
            f.speed.getSettings().stream().filter(s -> s.getRawName().equals("Mode")).forEach(s -> ((dev.vibe.setting.ModeSetting) s).setValue("Rewound"));
            f.speed.setEnabled(true); f.world.obstacles.add(new AxisAlignedBB(-.4, 64, .6, .4, 65, 1.6));
            step.rewoundPreUpdate(f.player);
            assertNull("The impending block must pause the auto-hop", RewoundHooks.move(f.player, 0, 0, .2));
            assertEquals(1, RewoundHooks.preStepHeight(f.player, .6F), 0);
            f.position(65); RewoundHooks.postStep(f.player);
            step.rewoundPreUpdate(f.player); assertNull(RewoundHooks.move(f.player, 0, 0, .2));
            step.rewoundPreUpdate(f.player); assertNotNull(RewoundHooks.move(f.player, 0, 0, .2));
            step.speedCheck.setEnabled(true);
            assertEquals(.6F, RewoundHooks.preStepHeight(f.player, .6F), 0);
            step.setEnabled(false);
            assertNotNull(RewoundHooks.move(f.player, 0, 0, .2));
        }
    }

    @Test public void criticalsForceNativePositionPacketsAfterThreeGroundedUpdatesEvenWhenIdle() throws Exception {
        try (RewoundModesTest.Fixture f = new RewoundModesTest.Fixture()) {
            CriticalsModule criticals = register(new CriticalsModule()); criticals.mode.setValue("Rewound"); criticals.setEnabled(true);
            for (int tick = 0; tick < 4; tick++) {
                f.player.ticksExisted = tick; RewoundHooks.preUpdate(f.player); f.queue.packets.clear(); f.player.onUpdateWalkingPlayer();
                C03PacketPlayer packet = (C03PacketPlayer) f.queue.packets.get(0);
                assertEquals(tick < 2, packet.isOnGround());
                if (tick >= 2) {
                    assertTrue("Odd-tick epsilon rounds away at Y=64, but position still must be forced", packet instanceof C04PacketPlayerPosition);
                    assertEquals(64 + (tick % 2 == 0 ? 1.7E-14 : 1E-17), packet.getPositionY(), 0);
                }
                assertEquals(64, f.player.posY, 0); assertEquals(64, f.player.getEntityBoundingBox().minY, 0);
                assertTrue(f.player.onGround);
                assertEquals(1.5, RewoundHooks.walkingY(1.5), 0);
                // Enabling through the keyboard can precede that tick's native
                // update; the following generic tick must retain its count.
                criticals.tick();
            }
            f.player.onGround = false; RewoundHooks.preUpdate(f.player); f.player.onGround = true;
            f.queue.packets.clear(); f.player.onUpdateWalkingPlayer();
            assertTrue(((C03PacketPlayer) f.queue.packets.get(0)).isOnGround());
            assertFalse(f.queue.packets.get(0) instanceof C04PacketPlayerPosition);
            criticals.setEnabled(false);
        }
    }

    @Test public void criticalsSkipAirSlabsAndStairsAndResetAfterDisable() throws Exception {
        try (RewoundModesTest.Fixture f = new RewoundModesTest.Fixture()) {
            CriticalsModule criticals = register(new CriticalsModule()); criticals.mode.setValue("Rewound"); criticals.setEnabled(true);
            for (int i = 0; i < 3; i++) RewoundHooks.preUpdate(f.player);
            for (IBlockState under : new IBlockState[] {Blocks.air.getDefaultState(), Blocks.stone_slab.getDefaultState(), Blocks.oak_stairs.getDefaultState()}) {
                f.world.underBlock = under; f.queue.packets.clear(); f.player.onUpdateWalkingPlayer();
                assertTrue(((C03PacketPlayer) f.queue.packets.get(0)).isOnGround());
            }
            f.world.underBlock = Blocks.stone.getDefaultState(); f.world.loaded = false; f.queue.packets.clear(); f.player.onUpdateWalkingPlayer();
            assertTrue(((C03PacketPlayer) f.queue.packets.get(0)).isOnGround());
            f.world.loaded = true; criticals.setEnabled(false); criticals.setEnabled(true);
            RewoundHooks.preUpdate(f.player); f.queue.packets.clear(); f.player.onUpdateWalkingPlayer();
            assertTrue(((C03PacketPlayer) f.queue.packets.get(0)).isOnGround());
            criticals.setEnabled(false);
        }
    }

    @Test public void antiVoidUsesSixBlockGroundHistoryEvenWhenNoFallResetsVanillaFallDistance() throws Exception {
        try (RewoundModesTest.Fixture f = new RewoundModesTest.Fixture()) {
            VoidWorld world = voidWorld(); f.mc.theWorld = world; f.player.worldObj = world;
            AntiVoidModule antiVoid = register(new AntiVoidModule()); antiVoid.setEnabled(true);
            antiVoid.preUpdate(f.player); f.player.onGround = false; f.player.motionY = -.7;
            f.position(58); antiVoid.preUpdate(f.player); assertTrue(f.queue.packets.isEmpty());
            f.position(57.99); f.player.fallDistance = 0; antiVoid.preUpdate(f.player);
            assertEquals(1, f.queue.packets.size()); C04PacketPlayerPosition packet = (C04PacketPlayerPosition) f.queue.packets.get(0);
            assertEquals(157.99, packet.getPositionY(), 0); assertFalse(packet.isOnGround());
            assertTrue(PacketDelayService.isUnmodified(packet));
            assertEquals(57.99, f.player.posY, 0); assertEquals(-.7, f.player.motionY, 0);
            f.queue.packets.clear(); f.player.capabilities.allowFlying = true; antiVoid.preUpdate(f.player);
            assertTrue(f.queue.packets.isEmpty());
            antiVoid.setEnabled(false); f.player.capabilities.allowFlying = false; antiVoid.preUpdate(f.player);
            assertTrue(f.queue.packets.isEmpty());
        }
    }

    @Test public void voidPredictionIncludesWaterNearbyLandAndUnknownChunks() throws Exception {
        try (RewoundModesTest.Fixture f = new RewoundModesTest.Fixture()) {
            VoidWorld world = voidWorld(); f.mc.theWorld = world; f.player.worldObj = world;
            f.player.posX = f.player.posZ = .5; f.player.posY = 57; f.player.motionX = f.player.motionZ = 0;
            RewoundVoid prediction = new RewoundVoid(); assertTrue(prediction.isInVoid(f.player));
            world.block(0, 20, 0, Blocks.water.getDefaultState()); world.time++;
            assertFalse(prediction.isInVoid(f.player));
            world.chunks.clear(); world.block(1, 20, 0, Blocks.stone.getDefaultState()); world.time++;
            assertTrue(prediction.isInVoid(f.player));
            f.player.motionX = .3;
            assertFalse("A platform reached by the source's drag prediction prevents a void rescue", prediction.isInVoid(f.player));
            world.chunks.clear(); world.loaded = false; world.time++;
            assertFalse(prediction.isInVoid(f.player));
        }
    }

    @Test public void recoveryBypassesPacketMutationAndDelayOnlyForItsExactPacket() throws Exception {
        try (RewoundModesTest.Fixture f = new RewoundModesTest.Fixture()) {
            C03PacketPlayer rescue = new C04PacketPlayerPosition(1, 150, 2, false);
            PacketDelayService.sendUnmodified(f.queue, rescue);
            assertTrue(PacketDelayService.isUnmodified(rescue));
            assertFalse(PacketDelayService.isUnmodified(new C03PacketPlayer(false)));
            PacketDelayService service = PacketDelayService.getInstance();
            f.fall.getSettings().stream().filter(s -> s.getRawName().equals("Mode")).forEach(s -> ((dev.vibe.setting.ModeSetting) s).setValue("Always On Ground"));
            f.fall.setEnabled(true);
            Method outbound = PacketDelayService.class.getDeclaredMethod("delayOutbound", Packet.class, io.netty.channel.ChannelHandlerContext.class, io.netty.channel.ChannelPromise.class);
            outbound.setAccessible(true);
            assertEquals(false, outbound.invoke(service, rescue, null, null));
            assertFalse("The bypass must be consumed exactly once", PacketDelayService.isUnmodified(rescue));
            assertFalse(rescue.isOnGround());
        }
    }

    @Test public void theThreeAddedChoicesAndStepOptionsPersistInNamedConfigs() throws Exception {
        try (RewoundModesTest.Fixture f = new RewoundModesTest.Fixture()) {
            StepModule step = register(new StepModule()); step.mode.setValue("Rewound"); step.timer.setEnabled(true); step.delay.setValue(250D); step.speedCheck.setEnabled(true);
            AntiVoidModule antiVoid = register(new AntiVoidModule()); antiVoid.fallDistance.setValue(7.25);
            CriticalsModule criticals = register(new CriticalsModule()); criticals.mode.setValue("Rewound");
            java.io.File folder = java.nio.file.Files.createTempDirectory(java.nio.file.Paths.get("build/exire-port"), "extra-config-").toFile();
            dev.vibe.config.VibeConfig config = new dev.vibe.config.VibeConfig(folder);
            assertTrue(config.save("rewound", Vibe.getInstance().getModuleManager()));
            step.mode.setValue("Jump"); step.timer.setEnabled(false); step.delay.setValue(0D); step.speedCheck.setEnabled(false);
            antiVoid.fallDistance.setValue(1D); criticals.mode.setValue("NoGround");
            assertTrue(config.load("rewound", Vibe.getInstance().getModuleManager()));
            assertEquals("Rewound", step.mode.getValue()); assertTrue(step.timer.isEnabled()); assertTrue(step.speedCheck.isEnabled()); assertEquals(250, step.delay.getInt());
            assertEquals("Rewound", antiVoid.mode.getValue()); assertEquals(7.25, antiVoid.fallDistance.getDouble(), 0);
            assertEquals("Rewound", criticals.mode.getValue());
            assertEquals(Collections.singletonList("Rewound"), antiVoid.mode.getModes());
            assertFalse(step.isEnabled()); assertFalse(antiVoid.isEnabled()); assertFalse(criticals.isEnabled());
        }
    }

    private static <T extends Module> T register(T module) { Vibe.getInstance().getModuleManager().registerDynamic(module); return module; }
    private static VoidWorld voidWorld() throws Exception {
        VoidWorld world = PathTestFixture.allocate(VoidWorld.class); world.loaded = true; world.chunks = new HashMap<>();
        world.provider = (IChunkProvider) Proxy.newProxyInstance(RewoundExtraModesTest.class.getClassLoader(), new Class<?>[] {IChunkProvider.class}, (proxy, method, args) -> {
            if (method.getName().equals("chunkExists")) return world.loaded;
            if (method.getReturnType() == boolean.class) return false;
            if (method.getReturnType() == int.class) return 0;
            return null;
        });
        return world;
    }
    public static class VoidWorld extends WorldClient {
        Map<Long, ColumnChunk> chunks; boolean loaded; long time; IChunkProvider provider;
        protected VoidWorld() { super(null, null, 0, null, null); }
        @Override public IChunkProvider getChunkProvider() { return provider; }
        @Override public long getTotalWorldTime() { return time; }
        @Override public Chunk getChunkFromChunkCoords(int x, int z) {
            long key = (long) x << 32 | (long) z & 0xffffffffL;
            if (!chunks.containsKey(key)) try {
                ColumnChunk chunk = PathTestFixture.allocate(ColumnChunk.class); chunk.sections = new ExtendedBlockStorage[16]; chunks.put(key, chunk);
            } catch (Exception failure) { throw new AssertionError(failure); }
            return chunks.get(key);
        }
        void block(int x, int y, int z, IBlockState state) {
            ColumnChunk chunk = (ColumnChunk) getChunkFromChunkCoords(x >> 4, z >> 4);
            if (chunk.sections[y >> 4] == null) chunk.sections[y >> 4] = new ExtendedBlockStorage(y & ~15, true);
            chunk.sections[y >> 4].set(x & 15, y & 15, z & 15, state);
        }
    }
    public static class ColumnChunk extends Chunk {
        ExtendedBlockStorage[] sections;
        protected ColumnChunk() { super((World) null, 0, 0); }
        @Override public boolean isEmpty() { return false; }
        @Override public ExtendedBlockStorage[] getBlockStorageArray() { return sections; }
    }
}

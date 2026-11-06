package dev.vibe.combat;

import dev.vibe.module.impl.combat.ProjectileAuraModule;
import dev.vibe.module.impl.movement.MoveFixModule;
import dev.vibe.module.impl.world.AutoToolModule;
import dev.vibe.path.PathTestFixture;
import java.lang.reflect.Method;
import java.util.*;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.entity.projectile.*;
import net.minecraft.init.*;
import net.minecraft.item.ItemStack;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.*;
import net.minecraft.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ProjectileAuraTest {
    @Test public void realWalkingRotationPrecedesNativeSlotAndUsePacketsAndReturnsToWeapon() throws Exception {
        try (Fixture f = new Fixture()) {
            f.aura.modes.setValue(Collections.singleton("Snowball"));
            f.aura.prepare(); f.aura.apply();
            assertTrue(ProjectileAuraModule.reserving()); assertEquals(0, f.queue.packets.size());
            f.walk();
            assertTrue(f.queue.packets.get(0) instanceof C03PacketPlayer);
            assertEquals(Arrays.asList("C09:4", "C08:Snowball", "C09:0"), f.actions());
            C03PacketPlayer rotation = (C03PacketPlayer)f.queue.packets.get(0);
            assertEquals(0, f.self().rotationYaw, 0);
            assertTrue(rotation.getPitch() != 0); assertEquals(15, f.self().inventory.mainInventory[4].stackSize);
            assertEquals(0, f.self().inventory.currentItem); assertFalse(AutoToolModule.hasHotbarAction());
            f.aura.prepare(); f.aura.apply(); f.walk();
            assertEquals(1, f.uses()); // Minimum time between throws, even with multiple walking calls.
        }
    }
    @Test public void limitedRotationMustHitBeforeThrowingAndOtherOwnersPreventTheThrow() throws Exception {
        try (Fixture f = new Fixture()) {
            f.target.setPosition(7.5, 64, .5); f.target.lastTickPosX = 7.5; f.target.lastTickPosZ = .5;
            f.aura.rotationSpeed.setValue(10D);
            f.aura.prepare(); f.aura.apply(); f.walk(); assertEquals(0, f.uses());
            f.aura.rotationSpeed.setValue(180D);
            f.aura.prepare(); f.aura.apply(); f.fix.setFakeRotation("scaffold", 0, 80); f.walk(); assertEquals(0, f.uses());
            f.fix.discardOwnedState("scaffold"); f.aura.prepare(); f.aura.apply(); f.walk(); assertEquals(1, f.uses());
        }
    }
    @Test public void aiAuraReturnRotationYieldsToAPlannedProjectile() throws Exception {
        try (Fixture f = new Fixture()) {
            dev.vibe.module.impl.combat.AIAuraModule ai = new dev.vibe.module.impl.combat.AIAuraModule();
            dev.vibe.aiaura.AuraLearning learning = PathTestFixture.allocate(dev.vibe.aiaura.AuraLearning.class);
            PathTestFixture.set(dev.vibe.aiaura.AuraLearning.class, learning, "model", dev.vibe.aiaura.AuraFixtures.policy());
            PathTestFixture.set(dev.vibe.module.impl.combat.AIAuraModule.class, ai, "learning", learning);
            f.terrain.base.modules.registerDynamic(ai); ai.setEnabled(true);
            java.lang.reflect.Field rotation = dev.vibe.module.impl.combat.KillAuraModule.class.getDeclaredField("rotation"); rotation.setAccessible(true);
            ((AuraRotation)rotation.get(ai)).reset(60, 20);
            PathTestFixture.set(dev.vibe.module.impl.combat.AIAuraModule.class, ai, "returning", true);
            f.aura.prepare(); assertTrue(ProjectileAuraModule.reserving());
            ai.tickStart(); f.aura.apply(); assertTrue(f.fix.ownsRotation(f.aura.getId()));
            f.walk(); assertEquals(1, f.uses());
        }
    }
    @Test public void respectsRangeModesCoverAndGuiWithoutWastingProjectiles() throws Exception {
        try (Fixture f = new Fixture()) {
            f.aura.distance.setRange(8, 10); f.tick(); assertEquals(0, f.uses());
            f.aura.distance.setRange(0, 6); f.tick(); assertEquals(0, f.uses());
            f.aura.distance.setRange(3, 10); f.aura.modes.setValue(Collections.emptySet()); f.tick(); assertEquals(0, f.uses());
            f.aura.modes.setValue(Collections.singleton("Eggs")); f.tick(); assertEquals(0, f.uses());
            f.self().inventory.mainInventory[5] = new ItemStack(Items.egg, 16);
            for (int x = -1; x <= 1; x++) for (int y = 64; y <= 68; y++) f.terrain.block(x, y, 4, Blocks.stone.getDefaultState());
            f.tick(); assertEquals(0, f.uses());
            f.terrain.world.blocks.entrySet().removeIf(e -> e.getKey().getZ() == 4 && e.getKey().getY() >= 64);
            f.terrain.base.mc.currentScreen = new net.minecraft.client.gui.GuiChat(); f.tick(); assertEquals(0, f.uses());
            f.terrain.base.mc.currentScreen = null; f.tick(); assertEquals(1, f.uses());
            assertTrue(f.actions().contains("C08:Eggs"));
        }
    }
    @Test public void rodStaysSelectedUntilImpactThenReelsAndRestoresWeapon() throws Exception {
        try (Fixture f = new Fixture()) {
            f.aura.modes.setValue(Collections.singleton("Rod"));
            f.self().inventory.mainInventory[6] = new ItemStack(Items.fishing_rod);
            f.tick(); assertEquals(1, f.uses()); assertEquals(6, f.self().inventory.currentItem);
            assertTrue(AutoToolModule.hasHotbarAction()); assertTrue(ProjectileAuraModule.reserving());
            f.self().fishEntity = new EntityFishHook(f.terrain.world, 0, 65, 4, f.self());
            f.tick(); assertEquals(1, f.uses()); assertEquals(6, f.self().inventory.currentItem);
            f.self().fishEntity.caughtEntity = f.target;
            f.tick(); assertEquals(2, f.uses()); assertEquals(0, f.self().inventory.currentItem);
            assertFalse(AutoToolModule.hasHotbarAction()); assertFalse(ProjectileAuraModule.reserving());
        }
    }
    @Test public void rodTimeoutAndManualSlotChangeReleaseOwnership() throws Exception {
        try (Fixture f = new Fixture()) {
            f.aura.modes.setValue(Collections.singleton("Rod"));
            f.self().inventory.mainInventory[6] = new ItemStack(Items.fishing_rod); f.tick();
            f.self().inventory.currentItem = 2; f.tick();
            assertEquals(2, f.self().inventory.currentItem); assertFalse(AutoToolModule.hasHotbarAction()); assertEquals(1, f.uses());
            PathTestFixture.set(ProjectileAuraModule.class, f.aura, "nextThrow", 0L);
            f.self().inventory.currentItem = 0; f.tick();
            for (int i = 0; i < 20; i++) f.tick();
            assertEquals(0, f.self().inventory.currentItem); assertFalse(AutoToolModule.hasHotbarAction());
        }
    }
    @Test public void alternatesStockedModesAndHandlesTheLastItemInAStack() throws Exception {
        try (Fixture f = new Fixture()) {
            f.aura.modes.setValue(new LinkedHashSet<>(Arrays.asList("Snowball", "Eggs")));
            f.self().inventory.mainInventory[4] = new ItemStack(Items.snowball, 1);
            f.self().inventory.mainInventory[5] = new ItemStack(Items.egg, 2);
            f.tick(); assertEquals(1, f.uses()); assertNull(f.self().inventory.mainInventory[4]);
            PathTestFixture.set(ProjectileAuraModule.class, f.aura, "nextThrow", 0L);
            f.tick(); assertEquals(2, f.uses()); assertTrue(f.actions().contains("C08:Eggs"));
            assertEquals(0, f.self().inventory.currentItem); assertEquals(1, f.self().inventory.mainInventory[5].stackSize);
        }
    }
    @Test public void unreachableRodFallsBackToThrowableAndUnloadedTerrainIsNeverRead() throws Exception {
        try (Fixture f = new Fixture()) {
            f.aura.distance.setRange(3, 24); f.aura.modes.setValue(new LinkedHashSet<>(Arrays.asList("Rod", "Snowball")));
            f.self().inventory.mainInventory[6] = new ItemStack(Items.fishing_rod);
            f.target.setPosition(.5, 64, 22.5); f.target.lastTickPosZ = 22.5;
            PathTestFixture.set(ProjectileAuraModule.class, f.aura, "nextMode", 2);
            // prepare initializes the player context once; then request the rod first.
            f.aura.prepare(); PathTestFixture.set(ProjectileAuraModule.class, f.aura, "nextMode", 2);
            f.tick(); assertEquals(1, f.uses()); assertTrue(f.actions().contains("C08:Snowball"));
            PathTestFixture.set(ProjectileAuraModule.class, f.aura, "nextThrow", 0L);
            f.terrain.world.loaded = false; int reads = f.terrain.world.reads;
            f.tick(); assertEquals(1, f.uses()); assertEquals(reads, f.terrain.world.reads);
        }
    }
    @Test public void predictionMatchesRealSnowballAndRodAirPhysicsAndLeadsMovingTargets() throws Exception {
        try (ProjectileFixture f = new ProjectileFixture()) {
            Vec3 eyes = new Vec3(.5, 75, .5), point = new Vec3(.5, 75, 10.5);
            Vec3[] movement = new Vec3[41]; for (int i = 0; i < movement.length; i++) movement[i] = new Vec3(.18 * i, 0, 0);
            for (ProjectileAimSolver.Kind kind : ProjectileAimSolver.Kind.values()) {
                ProjectileAimSolver.Shot shot = ProjectileAimSolver.solve(eyes, point, movement, kind); assertNotNull(shot);
                Vec3 origin = ProjectileAimSolver.origin(eyes, shot.yaw), velocity = ProjectileAimSolver.velocity(shot.yaw, shot.pitch);
                net.minecraft.entity.Entity projectile;
                if (kind == ProjectileAimSolver.Kind.ROD) {
                    EntityFishHook hook = new EntityFishHook(f.world); hook.angler = f.self; projectile = hook;
                } else projectile = new EntitySnowball(f.world, origin.xCoord, origin.yCoord, origin.zCoord);
                projectile.setPosition(origin.xCoord, origin.yCoord, origin.zCoord);
                projectile.motionX = velocity.xCoord; projectile.motionY = velocity.yCoord; projectile.motionZ = velocity.zCoord;
                int ticks = (int)shot.ticks;
                for (int i = 0; i < ticks; i++) projectile.onUpdate();
                double part = shot.ticks - ticks;
                Vec3 actual = new Vec3(projectile.posX + projectile.motionX * part, projectile.posY + projectile.motionY * part, projectile.posZ + projectile.motionZ * part);
                Vec3 expected = point.add(ArrowAimSolver.at(movement, shot.ticks));
                assertEquals(expected.xCoord, actual.xCoord, .002); assertEquals(expected.yCoord, actual.yCoord, .002); assertEquals(expected.zCoord, actual.zCoord, .002);
                assertTrue("Lateral movement must be led", shot.yaw < 0);
            }
        }
    }

    private static final class Fixture implements AutoCloseable {
        final ProjectileFixture terrain = new ProjectileFixture();
        final ProjectileAuraModule aura = new ProjectileAuraModule();
        final MoveFixModule fix = new MoveFixModule();
        final Queue queue = PathTestFixture.allocate(Queue.class);
        final ProjectileFixture.Player target = terrain.player("Enemy", .5, 64, 7.5);
        final Method walking = EntityPlayerSP.class.getDeclaredMethod("onUpdateWalkingPlayer");
        Fixture() throws Exception {
            queue.packets = new ArrayList<>();
            terrain.base.modules.registerDynamic(fix); terrain.base.modules.registerDynamic(aura);
            PathTestFixture.set(EntityPlayerSP.class, self(), "sendQueue", queue);
            PathTestFixture.set(EntityPlayerSP.class, self(), "mc", terrain.base.mc);
            self().capabilities = new net.minecraft.entity.player.PlayerCapabilities();
            self().inventory.mainInventory[0] = new ItemStack(Items.diamond_sword);
            self().inventory.mainInventory[4] = new ItemStack(Items.snowball, 16);
            terrain.base.mc.playerController = SilentMurderPacketsTest.nativeController(terrain.base.mc, queue);
            terrain.base.mc.playerController.updateController(); queue.packets.clear();
            aura.rotationSpeed.setValue(180D); aura.setEnabled(true); walking.setAccessible(true);
        }
        ProjectileFixture.Player self() { return terrain.self; }
        void tick() throws Exception { fix.beginRotationTick(); aura.prepare(); aura.apply(); walk(); }
        void walk() throws Exception {
            MoveFixModule.beginPacketRotationHook(self()); walking.invoke(self()); MoveFixModule.endPacketRotationHook(self());
        }
        long uses() { return queue.packets.stream().filter(p -> p instanceof C08PacketPlayerBlockPlacement).count(); }
        List<String> actions() {
            List<String> result = new ArrayList<>();
            for (Packet p : queue.packets) {
                if (p instanceof C09PacketHeldItemChange) result.add("C09:" + ((C09PacketHeldItemChange)p).getSlotId());
                if (p instanceof C08PacketPlayerBlockPlacement) {
                    net.minecraft.item.Item item = ((C08PacketPlayerBlockPlacement)p).getStack().getItem();
                    result.add("C08:" + (item == Items.snowball ? "Snowball" : item == Items.egg ? "Eggs" : "Rod"));
                }
            }
            return result;
        }
        @Override public void close() throws Exception { try { aura.setEnabled(false); } finally { terrain.close(); } }
    }
    public static final class Queue extends NetHandlerPlayClient {
        List<Packet> packets;
        private Queue() { super(null, null, null, null); }
        @Override public void addToSendQueue(Packet packet) { packets.add(packet); }
    }
}

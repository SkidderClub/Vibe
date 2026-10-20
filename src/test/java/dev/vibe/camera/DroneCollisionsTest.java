package dev.vibe.camera;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/** Uses Minecraft's actual block shapes and collision query, without a player/fake entity. */
public class DroneCollisionsTest {
    @BeforeClass public static void registerBlocks() throws Exception {
        // Register client shapes/items only: full Forge statistics bootstrap requires LaunchClassLoader.
        if (!Bootstrap.isRegistered()) {
            Field registered = Bootstrap.class.getDeclaredField("alreadyRegistered"); registered.setAccessible(true);
            registered.setBoolean(null, true);
            net.minecraft.block.Block.registerBlocks(); net.minecraft.item.Item.registerItems();
        }
    }

    @Test public void sweptCollisionPreventsTunnellingAndReboundsAtWalls() throws Exception {
        CollisionWorld world = world(); world.block(2, 64, 0, Blocks.stone.getDefaultState());
        FpvFlight drone = drone(.5, 64.5, .5); drone.velocityX = 100;
        new DroneCollisions(world).move(drone, 5, 0, 0);
        assertEquals(2 - DroneCollisions.RADIUS, drone.x, 1e-6);
        assertTrue(drone.velocityX < 0 && drone.velocityX > -30);
        assertTrue(drone.isCrashing());
        assertTrue("An above-centre frame impact imparts pitch/roll torque", drone.angularSpeed() > 1);
        assertEquals(64.5, drone.y, 0);
    }

    @Test public void collisionsScrapeAlongWallsAndDissipateEnergy() throws Exception {
        CollisionWorld world = world();
        for (int z = 0; z < 6; z++) world.block(2, 64, z, Blocks.stone.getDefaultState());
        FpvFlight drone = drone(.5, 64.5, .5); drone.velocityX = 10; drone.velocityZ = 2;
        new DroneCollisions(world).move(drone, 3, 0, 2);
        assertEquals(1.8, drone.x, 1e-6);
        assertEquals(2.5, drone.z, 1e-9);
        assertTrue(drone.velocityX < 0);
        assertTrue("Tangential motion is reduced, not killed", drone.velocityZ > 0 && drone.velocityZ < 2);
        assertTrue(drone.speed() < Math.hypot(10, 2));
    }

    @Test public void hardFloorAndCeilingImpactsReboundWithoutPassingThrough() throws Exception {
        CollisionWorld world = world();
        world.block(0, 63, 0, Blocks.stone.getDefaultState());
        world.block(0, 67, 0, Blocks.stone.getDefaultState());
        DroneCollisions collisions = new DroneCollisions(world);
        FpvFlight drone = drone(.5, 66, .5); drone.velocityY = -30;
        collisions.move(drone, 0, -5, 0);
        assertEquals(64 + DroneCollisions.HALF_HEIGHT, drone.y, 1e-6);
        assertTrue(drone.velocityY > 0 && drone.velocityY < 10);
        drone.y = 66; drone.velocityY = 30;
        collisions.move(drone, 0, 5, 0);
        assertEquals(67 - DroneCollisions.HALF_HEIGHT, drone.y, 1e-6);
        assertTrue(drone.velocityY < 0 && drone.velocityY > -10);
    }

    @Test public void slabsAndThinPanesUseTheirActualShapes() throws Exception {
        CollisionWorld world = world(); world.block(2, 64, 0, Blocks.stone_slab.getDefaultState());
        DroneCollisions collisions = new DroneCollisions(world);
        FpvFlight above = drone(.5, 64.8, .5);
        collisions.move(above, 4, 0, 0);
        assertEquals("Space above a bottom slab is flyable", 4.5, above.x, 1e-9);
        FpvFlight low = drone(.5, 64.4, .5);
        collisions.move(low, 4, 0, 0);
        assertEquals(1.8, low.x, 1e-6);

        world.block(2, 64, 0, Blocks.glass_pane.getDefaultState());
        world.block(2, 64, -1, Blocks.glass_pane.getDefaultState());
        world.block(2, 64, 1, Blocks.glass_pane.getDefaultState());
        FpvFlight pane = drone(.5, 64.5, .5);
        collisions.move(pane, 5, 0, 0);
        assertTrue("Thin pane blocks the swept volume", pane.x < 3);
        assertTrue("The pane is not treated as a full cube", pane.x > 1.8);
    }

    @Test public void nonSolidBlocksAndUnloadedChunksDoNotBlockTheCamera() throws Exception {
        CollisionWorld world = world();
        world.block(2, 64, 0, Blocks.yellow_flower.getDefaultState());
        world.block(3, 64, 0, Blocks.water.getDefaultState());
        FpvFlight drone = drone(.5, 64.5, .5);
        new DroneCollisions(world).move(drone, 5, 0, 0);
        assertEquals(5.5, drone.x, 1e-9);
        world.block(2, 64, 0, Blocks.stone.getDefaultState()); world.loaded = false;
        drone = drone(.5, 64.5, .5);
        new DroneCollisions(world).move(drone, 5, 0, 0);
        assertEquals("Camera does not request or fabricate unloaded terrain", 5.5, drone.x, 1e-9);
    }

    @Test public void negativeCoordinatesAndEmbeddedCameraAreHandled() throws Exception {
        CollisionWorld world = world(); world.block(-3, 64, -1, Blocks.stone.getDefaultState());
        DroneCollisions collisions = new DroneCollisions(world);
        FpvFlight drone = drone(-.5, 64.5, -.5); drone.velocityX = -100;
        collisions.move(drone, -5, 0, 0);
        assertEquals(-1.8, drone.x, 1e-6);
        assertTrue(drone.velocityX > 0);

        drone = drone(-2.5, 64.5, -.5);
        collisions.move(drone, 0, 0, 0);
        AxisAlignedBB box = new AxisAlignedBB(drone.x - .2, drone.y - .1, drone.z - .2,
                drone.x + .2, drone.y + .1, drone.z + .2);
        assertTrue("Enabling collisions inside a block separates only the camera", world.getCollisionBoxes(box).isEmpty());
        assertTrue(Math.abs(drone.x + 2.5) < 1 && Math.abs(drone.y - 64.5) < 1 && Math.abs(drone.z + .5) < 1);
    }

    @Test public void physicsSubstepsUseCollisionResolverAndFreeFlightStaysUnchanged() throws Exception {
        CollisionWorld world = world();
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++)
            world.block(x, 63, z, Blocks.stone.getDefaultState());
        DroneCollisions collisions = new DroneCollisions(world);
        FpvFlight solid = drone(.5, 66, .5), free = drone(.5, 66, .5);
        for (int frame = 0; frame < 120; frame++) {
            solid.step(1D / 60, 0, 0, -1, false, 120, 45, 20, .35, collisions);
            free.step(1D / 60, 0, 0, -1, false, 120, 45, 20, .35);
        }
        assertEquals("Drone rests on its frame without sinking/jittering", 64.1, solid.y, 1e-6);
        assertEquals(0, solid.velocityY, 0);
        assertTrue("Disabled collisions retain fly-through movement", free.y < 63);
    }

    @Test public void gentleTouchdownIdlesMotorsAndStaysLandedAfterReleasingShift() throws Exception {
        CollisionWorld world = floor(); DroneCollisions collisions = new DroneCollisions(world);
        FpvFlight drone = drone(.5, 64.12, .5); drone.velocityY = -.6;
        run(drone, collisions, 1, -1, 60);
        assertTrue(drone.isGrounded());
        assertFalse("Soft contact is not a violent crash", drone.isCrashing());
        run(drone, collisions, 3, 0, 60);
        assertEquals(64.1, drone.y, 1e-6);
        assertEquals(0, drone.speed(), 1e-9);
        assertEquals("Releasing Shift does not spool back up into a hover", 0, drone.throttle, 0);
        run(drone, collisions, 1, 1, 60);
        assertFalse("Space takes off again", drone.isGrounded());
        assertTrue(drone.y > 66);
    }

    @Test public void hardTouchdownBouncesThenSettlesWithoutHoveringInMidBounce() throws Exception {
        CollisionWorld world = floor(); DroneCollisions collisions = new DroneCollisions(world);
        FpvFlight drone = drone(.5, 64.2, .5); drone.velocityY = -12;
        run(drone, collisions, .05, 0, 60);
        assertTrue("Hard touchdown rebounds above the ground", drone.y > 64.1 && drone.velocityY > 0);
        assertTrue(drone.isCrashing());
        run(drone, collisions, 4, 0, 60);
        assertTrue(drone.isGrounded());
        assertEquals(64.1, drone.y, 1e-6);
        assertEquals(0, drone.velocityY, 0);
        assertEquals(0, drone.throttle, 0);
    }

    @Test public void groundFrictionStopsSlidingAndGroundSupportIsNotLatchedOverLedges() throws Exception {
        CollisionWorld world = floor(); DroneCollisions collisions = new DroneCollisions(world);
        FpvFlight drone = drone(.5, 64.1, .5); drone.velocityX = 3;
        run(drone, collisions, 2, 0, 60);
        assertEquals(0, drone.groundSpeed(), 1e-9);
        assertTrue(drone.x > .5 && drone.x < 2);
        world.blocks.clear();
        run(drone, collisions, .5, 0, 60);
        assertFalse(drone.isGrounded());
        assertTrue("Motor-idle drone falls when its support is removed", drone.y < 63.2);
        run(drone, null, .1, 0, 60);
        assertEquals("Disabling collisions restores the ordinary neutral throttle", FpvFlight.GRAVITY / 20, drone.throttle, 0);
    }

    @Test public void landingOnSlabsUsesTheSurfaceHeightAndResetsCleanly() throws Exception {
        CollisionWorld world = world(); world.block(0, 63, 0, Blocks.stone_slab.getDefaultState());
        DroneCollisions collisions = new DroneCollisions(world);
        FpvFlight drone = drone(.5, 64, .5);
        run(drone, collisions, 2, -1, 144);
        assertEquals(63.5 + DroneCollisions.HALF_HEIGHT, drone.y, 1e-6);
        assertTrue(drone.isGrounded());
        drone.reset(.5, 66, .5, 0);
        assertFalse(drone.isGrounded()); assertFalse(drone.isCrashing());
        run(drone, collisions, 1, 0, 60);
        assertEquals("Reset has no old idle latch", 66, drone.y, 1e-9);
    }

    @Test public void hardLandingsAreStableAcrossFrameRates() throws Exception {
        CollisionWorld world = floor();
        FpvFlight slow = drone(.5, 66, .5), fast = drone(.5, 66, .5);
        slow.velocityX = fast.velocityX = 1;
        slow.velocityY = fast.velocityY = -12;
        run(slow, new DroneCollisions(world), 4, 0, 30);
        run(fast, new DroneCollisions(world), 4, 0, 144);
        assertTrue("Both frame rates settle: " + slow.y + "/" + fast.y, slow.isGrounded() && fast.isGrounded());
        assertEquals(slow.x, fast.x, .04); assertEquals(slow.y, fast.y, 1e-9);
        assertEquals(slow.roll(), fast.roll(), .1); assertEquals(slow.pitch(), fast.pitch(), .1);
    }

    @Test public void anInvertedCrashRestsUpsideDownInsteadOfAutoRighting() throws Exception {
        FpvFlight drone = drone(.5, 65, .5);
        drone.step(.1, 0, 1, -1, false, 1800, 45, 20, .35);
        drone.y = 64.1; drone.velocityX = drone.velocityY = drone.velocityZ = 0;
        run(drone, new DroneCollisions(floor()), 2, 0, 60);
        assertTrue(drone.isGrounded());
        assertEquals(180, Math.abs(drone.roll()), .1);
        assertEquals(0, drone.throttle, 0);
    }

    private static CollisionWorld floor() throws Exception {
        CollisionWorld world = world();
        for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++)
            world.block(x, 63, z, Blocks.stone.getDefaultState());
        return world;
    }

    private static void run(FpvFlight drone, DroneCollisions collisions, double seconds, double throttle, int fps) {
        for (int i = 0; i < Math.round(seconds * fps); i++)
            drone.step(1D / fps, 0, 0, throttle, false, 120, 45, 20, .35, collisions);
    }

    private static FpvFlight drone(double x, double y, double z) {
        FpvFlight drone = new FpvFlight(); drone.reset(x, y, z, 0); return drone;
    }

    static CollisionWorld world() throws Exception {
        registerBlocks();
        Class<?> unsafe = Class.forName("sun.misc.Unsafe");
        Field field = unsafe.getDeclaredField("theUnsafe"); field.setAccessible(true);
        CollisionWorld world = (CollisionWorld) unsafe.getMethod("allocateInstance", Class.class)
                .invoke(field.get(null), CollisionWorld.class);
        world.blocks = new HashMap<BlockPos, IBlockState>(); world.loaded = true;
        return world;
    }

    static class CollisionWorld extends WorldClient {
        Map<BlockPos, IBlockState> blocks;
        boolean loaded;
        private CollisionWorld() { super(null, null, 0, null, null); }
        void block(int x, int y, int z, IBlockState state) { blocks.put(new BlockPos(x, y, z), state); }
        @Override public boolean isBlockLoaded(BlockPos position) { return loaded; }
        @Override public IBlockState getBlockState(BlockPos position) {
            IBlockState state = blocks.get(position);
            return state == null ? Blocks.air.getDefaultState() : state;
        }
        @Override public List<AxisAlignedBB> getCollidingBoundingBoxes(Entity entity, AxisAlignedBB bounds) {
            throw new AssertionError("Camera collision must not use entity physics queries");
        }
    }
}

package dev.vibe.game.gta8;

import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

public class Gta8WorldTest {
    private static Gta8World world;
    private static long buildMillis;

    @BeforeClass public static void city() {
        long start = System.nanoTime();
        world = new Gta8World();
        buildMillis = (System.nanoTime() - start) / 1000000;
    }

    @Test public void generatesAFullCityQuickly() {
        System.out.println("GTA8 world: " + world.buildings.size() + " buildings, " + world.props.size() + " props, " + world.solids.size()
                + " solids, " + world.lanes.size() + " lanes, " + world.lights.size() + " lights, " + world.parking.size() + " parking spots in " + buildMillis + " ms");
        assertTrue(world.buildings.size() > 400);
        assertTrue(world.lanes.size() > 300);
        assertTrue("World generation should stay fast", buildMillis < 8000);
        for (Gta8World.Building b : world.buildings) {
            assertFalse("Building on a road: " + b.name, world.onRoad((b.x0 + b.x1) / 2, (b.z0 + b.z1) / 2));
            assertTrue(b.volumes.size() > 0);
        }
    }

    @Test public void everyLaneHasAWayOnward() {
        for (Gta8World.Lane lane : world.lanes) {
            assertFalse("Dead-end lane " + lane.id + " dir " + lane.dir + " at " + lane.toI + "," + lane.toJ, lane.next.isEmpty());
            assertTrue(lane.length > 20);
            for (Gta8World.Connector c : lane.next) {
                assertEquals(lane.toI, c.to.fromI);
                assertEquals(lane.toJ, c.to.fromJ);
                assertTrue(c.length > 1 && c.length < 60);
            }
            // Lanes run on asphalt and are clear of solids.
            for (double t = 0; t <= lane.length; t += 4) {
                double x = lane.x0 + Gta8World.DIR_X[lane.dir] * t, z = lane.z0 + Gta8World.DIR_Z[lane.dir] * t;
                assertTrue("Lane off road at " + x + "," + z, world.onRoad(x, z));
                assertFalse("Lane obstructed at " + x + "," + z, world.blocked(x, .05, z, .9, 1.5));
            }
        }
    }

    @Test public void opposingSignalsNeverShareGreen() {
        for (Gta8World.Signal s : world.signals) if (s.active) for (double t = 0; t < Gta8World.SIGNAL_CYCLE * 2; t += .25) {
            boolean ns = world.signalState(s, Gta8World.NORTH, t) != Gta8World.RED_LIGHT;
            boolean ew = world.signalState(s, Gta8World.EAST, t) != Gta8World.RED_LIGHT;
            assertFalse("Conflicting green at " + s.i + "," + s.j + " t=" + t, ns && ew);
            if (world.walkState(s, true, t) != Gta8World.DONT_WALK) assertEquals(Gta8World.RED_LIGHT, world.signalState(s, Gta8World.NORTH, t));
        }
    }

    @Test public void spawnPointsAndSidewalksAreFree() {
        assertFalse(world.blocked(world.spawnX, Gta8World.CURB, world.spawnZ, .3, 1.8));
        assertEquals(Gta8World.CURB, world.groundHeight(world.spawnX, world.spawnZ), 1e-6);
        for (Gta8World.WalkNode n : world.walkNodes) assertFalse("Walk node blocked " + n.x + "," + n.z, world.blocked(n.x, Gta8World.CURB, n.z, .3, 1.7));
        for (Gta8World.Poi p : world.pois) assertFalse("POI blocked " + p.name, world.blocked(p.interactX, Gta8World.CURB, p.interactZ, .3, 1.7));
        int clear = 0;
        for (Gta8World.ParkingSpot s : world.parking) if (!world.blocked(s.x, world.groundHeight(s.x, s.z) + .3, s.z, .9, 1.2)) clear++;
        assertTrue(clear > world.parking.size() * .9);
    }

    @Test public void terrainHasBeachHarbourAndHills() {
        assertTrue(world.isWater(0, 700));
        assertTrue(world.isWater(700, 0));
        assertFalse(world.isWater(0, 0));
        assertTrue(world.terrain(-110, -640) > 90);
        assertTrue(world.groundHeight(-104, 650) > Gta8World.WATER);
        assertEquals(0, world.groundHeight(0, 0), 1e-9);
        assertEquals(Gta8World.CURB, world.groundHeight(20, 20), 1e-9);
    }

    @Test public void raysHitBuildingsAndGround() {
        Gta8World.Building b = world.buildings.get(0);
        double cx = (b.x0 + b.x1) / 2, cz = (b.z0 + b.z1) / 2;
        double d = world.ray(cx, 3, b.z0 - 20, 0, 0, 1, 100);
        assertTrue(d <= 20.001);
        double down = world.ray(0, 10, 0, 0, -1, 0, 100);
        assertEquals(10, down, .05);
        List<double[]> route = world.route(-400, -400, 400, 400);
        assertTrue(route.size() >= 17);
    }
}

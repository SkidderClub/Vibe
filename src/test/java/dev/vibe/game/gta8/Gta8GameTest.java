package dev.vibe.game.gta8;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.*;

public class Gta8GameTest {
    private static Gta8World world;
    @Rule public TemporaryFolder folder = new TemporaryFolder();
    @BeforeClass public static void city() { world = new Gta8World(); }

    private Gta8Game game() throws Exception {
        return new Gta8Game(world, new Gta8Progress(folder.newFolder().toPath().resolve("gta8.json")));
    }
    private static void run(Gta8Game g, double seconds, Gta8Game.Input in) {
        for (int i = 0; i < Math.round(seconds * 60); i++) g.advance(1 / 60.0, in);
    }

    @Test public void cityComesAliveWithoutErrors() throws Exception {
        Gta8Game g = game();
        Gta8Game.Input in = new Gta8Game.Input();
        run(g, 40, in);
        int moving = 0, cars = 0, peds = 0;
        for (Gta8Vehicle v : g.vehicles) {
            assertTrue(Double.isFinite(v.x) && Double.isFinite(v.z) && Double.isFinite(v.y));
            if (v.ai != null) { cars++; if (v.ai.speed > 2) moving++; }
        }
        for (Gta8Ped p : g.peds) { assertTrue(Double.isFinite(p.x) && Double.isFinite(p.z)); if (p.kind == Gta8Ped.Kind.CIVILIAN && p.vehicle == null) peds++; }
        System.out.println("After 40 s: " + cars + " traffic cars (" + moving + " moving), " + peds + " pedestrians, " + g.vehicles.size() + " vehicles total");
        assertTrue(cars > 10);
        assertTrue(moving > 3);
        assertTrue(peds > 15);
    }

    @Test public void trafficFlowsWithoutCrashing() throws Exception {
        Gta8Game g = game();
        Gta8Game.Input in = new Gta8Game.Input();
        run(g, 20, in);
        int crashesBefore = 0;
        for (Gta8Vehicle v : g.vehicles) if (v.ai == null && v.dynamic && !v.parked && v.driver != null && v.driver != g.player) crashesBefore++;
        run(g, 60, in);
        int released = 0, traffic = 0;
        for (Gta8Vehicle v : g.vehicles) {
            if (v.ai != null) traffic++;
            if (v.ai == null && v.dynamic && !v.parked && v.driver != null && v.driver != g.player && v.model != Gta8Vehicle.Model.POLICE) released++;
        }
        System.out.println("Traffic cars " + traffic + ", knocked out of lane-following: " + released);
        assertTrue("Traffic should not pile up into collisions", released <= Math.max(2, traffic / 8));
    }

    @Test public void holdingTheSirenKeyTogglesOnce() throws Exception {
        Gta8Game g = game();
        Gta8Game.Input in = new Gta8Game.Input();
        run(g, 1, in);
        Gta8Vehicle car = new Gta8Vehicle(Gta8Vehicle.Model.POLICE, g.player.x, g.player.z + 9, 90, 0xFFFFFF);
        car.dynamic = true; car.parked = true; car.y = 0;
        g.addVehicle(car);
        g.player.x = car.doorX(); g.player.z = car.doorZ() - .3;
        in.enterExit = true;
        run(g, 2.5, in);
        assertSame(car, g.player.vehicle);
        boolean before = car.siren;
        in.crouch = true;
        run(g, .5, in);
        assertEquals("One press switches the siren", !before, car.siren);
        in.crouch = false;
        run(g, .1, in);
        in.crouch = true;
        run(g, .3, in);
        assertEquals("A second press switches it back", before, car.siren);
    }

    @Test public void shootingCausesPanicAndPolice() throws Exception {
        Gta8Game g = game();
        Gta8Game.Input in = new Gta8Game.Input();
        run(g, 2, in);
        Gta8Ped victim = g.createCivilian(g.player.x + g.player.forwardX() * 6, g.player.z + g.player.forwardZ() * 6);
        victim.state = Gta8Ped.State.IDLE; victim.waitTimer = 100;
        g.peds.add(victim);
        // Aim straight at the victim's chest from the camera.
        g.firstPerson = true;
        in.aim = true;
        int shots = 0;
        while (!victim.dead && shots < 12) {
            // Track the victim like a player would.
            g.updateCamera(0);
            double dx = victim.x - g.camera.x, dz = victim.z - g.camera.z;
            g.cameraYaw = Math.toDegrees(Math.atan2(dx, -dz));
            g.cameraPitch = Math.toDegrees(Math.atan2(g.camera.y - victim.chestY(), Math.hypot(dx, dz)));
            g.updateCamera(0);
            in.attack = true; run(g, .05, in); in.attack = false; run(g, .25, in); shots++;
        }
        assertTrue("Pistol should kill a civilian", victim.dead);
        assertTrue(g.progress.kills >= 1);
        run(g, 20, new Gta8Game.Input());
        assertTrue("Killing in public should draw police attention", g.police.wanted >= 1);
    }

    @Test public void playerCanStealAndDriveACar() throws Exception {
        Gta8Game g = game();
        Gta8Game.Input in = new Gta8Game.Input();
        run(g, 1, in);
        Gta8Vehicle car = new Gta8Vehicle(Gta8Vehicle.Model.SEDAN, g.player.x + 3, g.player.z, 0, 0xFFFFFF);
        car.dynamic = true; car.parked = true;
        car.y = world.groundHeight(car.x, car.z);
        // Place the car on the road next to the safehouse sidewalk.
        car.x = g.player.x; car.z = g.player.z + 9; car.y = 0; car.yaw = 90;
        g.addVehicle(car);
        g.player.x = car.doorX(); g.player.z = car.doorZ() - .3;
        in.enterExit = true;
        run(g, 2.5, in);
        assertSame("Player should be in the car", car, g.player.vehicle);
        double x0 = car.x;
        in.forward = true;
        run(g, 4, in);
        in.forward = false;
        assertTrue("Car should move under the player's control", Math.abs(car.x - x0) > 10);
        in.back = true;
        run(g, 3, in);
        in.back = false;
        assertTrue(Math.abs(car.speed()) < 6);
    }

    @Test public void arrestAndDeathRespawn() throws Exception {
        Gta8Game g = game();
        Gta8Game.Input in = new Gta8Game.Input();
        run(g, 1, in);
        long money = g.progress.money;
        Gta8Ped cop = g.createCop(g.player.x + 1, g.player.z);
        g.peds.add(cop);
        g.police.crime(Gta8Police.Crime.ASSAULT, g.player.x, g.player.z);
        g.police.playerHostile = false;
        assertEquals(1, g.police.wanted);
        run(g, 9, in);
        assertTrue("Standing still next to an officer at one star should end in an arrest", g.progress.arrests == 1 || g.busted);
        run(g, 6, in);
        assertFalse(g.busted);
        assertEquals(0, g.police.wanted);
        assertTrue(g.progress.money < money);
        g.damagePed(g.player, 10000, null, 1, 0, 0);
        assertTrue(g.dead);
        run(g, 8, in);
        assertFalse(g.dead);
        assertEquals(g.player.maxHealth, g.player.health, 1e-9);
    }

    @Test public void progressIsEncryptedAndTamperEvident() throws Exception {
        Path file = folder.newFolder().toPath().resolve("gta8.json");
        Gta8Progress p = new Gta8Progress(file);
        p.money = 123456; p.ammo.put(Gta8Weapon.RIFLE, 90); p.kills = 7;
        assertTrue(p.save());
        String raw = new String(Files.readAllBytes(file), "ISO-8859-1");
        assertFalse(raw.contains("123456"));
        Gta8Progress loaded = Gta8Progress.load(file);
        assertEquals(123456, loaded.money);
        assertEquals(90, loaded.ammo(Gta8Weapon.RIFLE));
        byte[] bytes = Files.readAllBytes(file);
        bytes[bytes.length - 3] ^= 1;
        Files.write(file, bytes);
        Gta8Progress recovered = Gta8Progress.load(file);
        assertNotNull(recovered.error());
    }
}

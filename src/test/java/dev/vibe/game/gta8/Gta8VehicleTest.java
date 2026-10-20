package dev.vibe.game.gta8;

import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

/** Vehicle dynamics stay within real-world ranges for acceleration, braking, top speed and grip. */
public class Gta8VehicleTest {
    private static Gta8World world;
    @BeforeClass public static void city() { world = new Gta8World(); }

    /** A long straight: Fairmont Blvd runs north-south through the whole grid at x = 0. */
    private Gta8Vehicle onBoulevard(Gta8Vehicle.Model model) {
        Gta8Vehicle v = new Gta8Vehicle(model, 3.5, 400, 0, 0xFFFFFF);
        v.dynamic = true;
        v.y = world.groundHeight(v.x, v.z);
        return v;
    }
    private static void step(Gta8Vehicle v, double seconds) {
        for (int i = 0; i < Math.round(seconds * 180); i++) { v.physics(1 / 180.0, world, 1); v.collideWorld(world); }
    }

    @Test public void sedanAcceleratesLikeARealCar() {
        Gta8Vehicle v = onBoulevard(Gta8Vehicle.Model.SEDAN);
        v.throttle = 1;
        double t = 0;
        while (v.speedKmh() < 100 && t < 30) { step(v, .05); t += .05; }
        System.out.printf("Sedan 0-100 km/h: %.1f s%n", t);
        assertTrue("Sedan 0-100 took " + t, t > 6 && t < 13);
        assertTrue(Double.isFinite(v.x) && Double.isFinite(v.z));
        assertEquals(0, v.yaw, 2);
    }

    @Test public void sportsCarIsMuchQuicker() {
        Gta8Vehicle v = onBoulevard(Gta8Vehicle.Model.SPORTS);
        v.throttle = 1;
        double t = 0;
        while (v.speedKmh() < 100 && t < 30) { step(v, .05); t += .05; }
        System.out.printf("Sports 0-100 km/h: %.1f s%n", t);
        assertTrue("Sports car 0-100 took " + t, t > 2.5 && t < 5.5);
    }

    @Test public void brakingDistanceFromHundred() {
        Gta8Vehicle v = onBoulevard(Gta8Vehicle.Model.SEDAN);
        v.vz = -27.8;
        double z0 = v.z;
        v.brake = 1;
        step(v, 6);
        double distance = Math.abs(v.z - z0);
        System.out.printf("Sedan braking 100-0 km/h: %.1f m%n", distance);
        assertTrue("Braking distance " + distance, distance > 30 && distance < 55);
        assertTrue(Math.abs(v.speed()) < .2);
    }

    @Test public void wetRoadsLengthenBraking() {
        Gta8Vehicle dry = onBoulevard(Gta8Vehicle.Model.SEDAN), wet = onBoulevard(Gta8Vehicle.Model.SEDAN);
        dry.vz = wet.vz = -25; dry.brake = wet.brake = 1;
        for (int i = 0; i < 900; i++) { dry.physics(1 / 180.0, world, 1); wet.physics(1 / 180.0, world, .7); }
        assertTrue(Math.abs(wet.z - 400) > Math.abs(dry.z - 400) * 1.2);
    }

    @Test public void corneringIsStableAndGripLimited() {
        Gta8Vehicle v = new Gta8Vehicle(Gta8Vehicle.Model.SEDAN, 0, 0, 0, 0);
        v.dynamic = true;
        v.vz = -18;
        v.steer = .12;
        double maxLateral = 0;
        for (int i = 0; i < 180 * 6; i++) {
            v.throttle = v.speed() < 18 ? .5 : 0;
            v.physics(1 / 180.0, world, 1);
            double lateral = Math.abs(v.speed() * v.yawRate);
            maxLateral = Math.max(maxLateral, lateral);
            assertTrue(Double.isFinite(v.yawRate));
        }
        System.out.printf("Steady cornering lateral acceleration: %.2f m/s^2%n", maxLateral);
        assertTrue("Car should turn", maxLateral > 2);
        assertTrue("Lateral grip is bounded by tyre friction", maxLateral < 11.5);
    }

    @Test public void wallsStopCarsAndCauseDamage() {
        Gta8Vehicle v = onBoulevard(Gta8Vehicle.Model.SEDAN);
        // Drive west from the boulevard straight into the block's buildings.
        v.x = -12; v.z = 30; v.yaw = 270; v.vx = -20;
        double before = v.health;
        for (int i = 0; i < 180 * 3; i++) { v.physics(1 / 180.0, world, 1); v.collideWorld(world); }
        assertTrue("Car passed through a building", !world.blocked(v.x, v.y + .5, v.z, .8, 1));
        assertTrue(v.health < before);
        assertTrue(Math.abs(v.vx) < 10);
    }

    @Test public void topSpeedMatchesTheModel() {
        Gta8Vehicle v = new Gta8Vehicle(Gta8Vehicle.Model.COMPACT, 0, 0, 0, 0);
        v.dynamic = true;
        v.throttle = 1;
        for (int i = 0; i < 180 * 90; i++) { v.physics(1 / 180.0, world, 1); v.x = 0; v.z = 0; v.y = 0; }
        System.out.printf("Compact top speed: %.0f km/h%n", v.speedKmh());
        assertEquals(Gta8Vehicle.Model.COMPACT.topSpeed * 3.6, v.speedKmh(), 25);
    }
}

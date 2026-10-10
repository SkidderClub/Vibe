package dev.vibe.camera;

import org.junit.Test;
import static org.junit.Assert.*;

public class FpvFlightTest {
    @Test public void levelHoverThrustAndGravity() {
        FpvFlight drone = drone();
        run(drone, 2, 0, 0, 0, false, 60);
        assertEquals(64, drone.y, 1e-9);
        assertEquals(0, drone.speed(), 1e-9);
        run(drone, 1, 0, 0, 1, false, 60);
        assertTrue(drone.y > 67);
        assertTrue(drone.velocityY > 5);
        drone = drone();
        run(drone, 1, 0, 0, -1, false, 60);
        assertTrue(drone.y < 60);
    }

    @Test public void pitchAndRollRedirectThrustAndKeepMomentum() {
        FpvFlight pitch = drone();
        // Thirty degrees nose-down accelerates south, rather than teleporting along the look vector.
        pitch.look(0, 30, false);
        run(pitch, 1, 0, 0, 0, false, 60);
        assertTrue(pitch.z > 2);
        assertTrue(pitch.velocityZ > 4);
        assertTrue(pitch.y < 64);
        double speed = pitch.velocityZ;
        pitch.look(0, -30, false);
        run(pitch, 0.5, 0, 0, 0, false, 60);
        assertTrue("Releasing/levelling the sticks does not stop the drone instantly", pitch.velocityZ > speed * 0.7);

        FpvFlight roll = drone();
        run(roll, 0.25, 0, 1, 0, false, 60);
        assertEquals(30, roll.roll(), 0.001);
        run(roll, 1, 0, 0, 0, false, 60);
        assertTrue("Right bank accelerates to camera-right at Minecraft yaw zero", roll.velocityX < -4);
        assertEquals(30, roll.roll(), 0.001);
    }

    @Test public void acroCanInvertAndAngleSelfLevels() {
        FpvFlight drone = drone();
        run(drone, 1.5, 0, 1, 0, false, 60);
        assertEquals(180, Math.abs(drone.roll()), 0.001);
        double v = drone.velocityY;
        run(drone, 0.3, 0, 0, 1, false, 60);
        assertTrue("Inverted thrust points down", drone.velocityY < v - 3);

        drone = drone();
        run(drone, 1, 1, 1, 0, true, 60);
        assertEquals(45, drone.pitch(), 0.1);
        assertEquals(45, drone.roll(), 0.1);
        run(drone, 1, 0, 0, 0, true, 60);
        assertEquals(0, drone.pitch(), 0.1);
        assertEquals(0, drone.roll(), 0.1);
    }

    @Test public void cameraTiltAffectsLensOnlyAndFlightIsFrameRateIndependent() {
        FpvFlight drone = drone();
        assertEquals(-20, drone.cameraPitch(20), 1e-6);
        assertEquals(0, drone.pitch(), 1e-6);
        float[] matrix = drone.viewMatrix(20);
        for (int a = 0; a < 3; a++) for (int b = 0; b < 3; b++) {
            double dot = 0;
            for (int k = 0; k < 3; k++) dot += matrix[a * 4 + k] * matrix[b * 4 + k];
            assertEquals(a == b ? 1 : 0, dot, 1e-6);
        }
        FpvFlight slow = drone(), fast = drone();
        run(slow, 2, .2, .1, .3, false, 30);
        run(fast, 2, .2, .1, .3, false, 144);
        assertEquals(slow.x, fast.x, .04);
        assertEquals(slow.y, fast.y, .04);
        assertEquals(slow.z, fast.z, .04);
        assertEquals(slow.pitch(), fast.pitch(), .01);
        assertEquals(slow.roll(), fast.roll(), .01);
    }

    @Test public void osdTelemetryUsesLaunchHomeAndMinecraftCompassDirections() {
        FpvFlight drone = drone();
        drone.reset(10, 64, 20, 0);
        drone.x = 13; drone.y = 66; drone.z = 24;
        drone.velocityX = 3; drone.velocityY = 12; drone.velocityZ = 4;
        assertEquals(5, drone.homeDistance(), 1e-9);
        assertEquals(2, drone.relativeAltitude(), 0);
        assertEquals(5, drone.groundSpeed(), 1e-9);
        assertEquals(13, drone.speed(), 1e-9);
        assertEquals(323.130102, drone.homeBearing(), 1e-5);
        assertEquals(180, FpvFlight.compassHeading(0), 0);
        assertEquals(270, FpvFlight.compassHeading(90), 0);
        assertEquals(90, FpvFlight.compassHeading(-90), 0);
        assertEquals(0, FpvFlight.compassHeading(180), 0);
        assertEquals(0, FpvFlight.compassHeading(-180), 0);
        assertEquals(180, FpvFlight.compassHeading(-720), 0);
    }

    @Test public void tripAndMaximumSpeedAccumulateAndResetWithFlight() {
        FpvFlight drone = drone();
        run(drone, 1, 0, 0, 1, false, 60);
        assertEquals(drone.y - 64, drone.distanceTravelled, 1e-9);
        assertEquals(drone.speed(), drone.maxSpeed, 1e-9);
        assertTrue(drone.maxSpeed > 5);
        drone.reset(5, 80, -10, 90);
        assertEquals(0, drone.distanceTravelled, 0);
        assertEquals(0, drone.maxSpeed, 0);
        assertEquals(0, drone.homeDistance(), 0);
        assertEquals(0, drone.relativeAltitude(), 0);
        assertEquals(0, drone.flightTime, 0);
    }

    @Test public void impactsScaleWithClosingSpeedAndTumbleDecays() {
        FpvFlight soft = drone(), hard = drone();
        soft.velocityZ = .5; hard.velocityZ = 15;
        soft.impact(0, 0, -1); hard.impact(0, 0, -1);
        assertEquals(0, soft.velocityZ, 0); assertFalse(soft.isCrashing());
        assertEquals(0, soft.angularSpeed(), 0);
        assertTrue(hard.velocityZ < 0 && hard.velocityZ > -15);
        assertTrue(hard.angularSpeed() > 1); assertTrue(hard.isCrashing());
        double spin = hard.angularSpeed();
        run(hard, .2, 0, 0, 0, false, 60);
        assertTrue("A wall crash visibly pitches the FPV camera", Math.abs(hard.pitch()) > 5);
        assertTrue(hard.angularSpeed() < spin);
        assertEquals("A hard hit briefly interrupts lift", 0, hard.throttle, 0);
        run(hard, 1, 0, 0, 0, false, 60);
        assertFalse(hard.isCrashing()); assertTrue(hard.angularSpeed() < .1);
        assertEquals(FpvFlight.GRAVITY / 20, hard.throttle, 0);
    }

    @Test public void contactsCannotAddLinearEnergyOrBounceAwayFromARecedingSurface() {
        FpvFlight drone = drone(); drone.velocityX = -8; drone.velocityY = 3; drone.velocityZ = 2;
        double speed = drone.speed(); drone.impact(1, 0, 0);
        assertTrue(drone.speed() < speed); assertTrue(drone.velocityX > 0);
        double[] velocity = {drone.velocityX, drone.velocityY, drone.velocityZ};
        drone.impact(1, 0, 0);
        assertArrayEquals(velocity, new double[] {drone.velocityX, drone.velocityY, drone.velocityZ}, 0);
        drone.reset(0, 64, 0, 0);
        assertEquals(0, drone.angularSpeed(), 0); assertFalse(drone.isCrashing());
    }

    private FpvFlight drone() { FpvFlight drone = new FpvFlight(); drone.reset(0, 64, 0, 0); return drone; }
    private void run(FpvFlight drone, double seconds, double pitch, double roll, double throttle, boolean angle, int fps) {
        for (int i = 0; i < Math.round(seconds * fps); i++)
            drone.step(1.0 / fps, pitch, roll, throttle, angle, 120, 45, 20, .35);
    }
}

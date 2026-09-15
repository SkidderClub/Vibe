package dev.vibe.combat;

import org.junit.Test;
import static org.junit.Assert.*;

public class AuraRotationTest {
    @Test public void crossesWrapBoundaryWithoutSnapping() {
        AuraRotation rotation = new AuraRotation();
        rotation.reset(179.0F, 0.0F);
        rotation.advance(-179.0F, 0.0F, 10.0F, false, 0.5F);
        assertTrue(rotation.getYaw() > 180.0F);
        assertEquals(181.0F, rotation.getYaw(), 0.16F);
        rotation.reset(-179.0F, 0.0F);
        rotation.advance(179.0F, 0.0F, 10.0F, false, 0.5F);
        assertEquals(-181.0F, rotation.getYaw(), 0.16F);
    }

    @Test public void repeatedCirclesKeepAccumulatedYawAndBoundEveryPacketDelta() {
        for (boolean acceleration : new boolean[] {false, true}) {
            AuraRotation rotation = new AuraRotation();
            rotation.reset(0.0F, 0.0F);
            for (int tick = 1; tick <= 10000; tick++) {
                float previous = rotation.getYaw();
                rotation.advance((tick * 3.0F) % 360.0F, 30.0F, acceleration ? 3.0F : 20.0F, acceleration, 0.5F);
                assertTrue(Math.abs(rotation.getYaw() - previous) <= 90.01F);
                assertTrue(Math.abs(rotation.getPitch()) <= 90.0F);
                // The raw yaw itself must retain its revolution count.
                assertEquals(tick * 3.0F, rotation.getYaw(), 12.0F);
            }
        }
    }

    @Test public void handoffToCameraUsesNearestEquivalentIncludingManyRevolutions() {
        assertEquals(361.0F, RotationMath.nearest(1.0F, 359.0F), 0.0F);
        assertEquals(-361.0F, RotationMath.nearest(-1.0F, -359.0F), 0.0F);
        assertEquals(36005.0F, RotationMath.nearest(5.0F, 36000.0F), 0.0F);
        assertEquals(-36005.0F, RotationMath.nearest(-5.0F, -36000.0F), 0.0F);
    }

    @Test public void maxAngleIsAnAbsoluteLimitAnd180IncludesBehind() {
        assertTrue(RotationMath.withinAngle(180, 90, 0, -90, 180));
        assertFalse(RotationMath.withinAngle(180, 0, 0, 0, 179));
        assertFalse(RotationMath.withinAngle(0, 40, 0, 0, 30));
        assertTrue(RotationMath.withinAngle(720, 0, 0, 0, 0));
        assertFalse(RotationMath.withinAngle(1, 0, 0, 0, 0));
    }

    @Test public void mouseStepsAndPitchBoundsHoldAcrossSensitivities() {
        for (float sensitivity : new float[] {0.0F, 0.5F, 1.0F}) {
            AuraRotation rotation = new AuraRotation();
            rotation.reset(0.0F, 0.0F);
            float factor = sensitivity * 0.6F + 0.2F;
            float step = factor * factor * factor * 8.0F * 0.15F;
            rotation.advance(95.0F, 180.0F, 23.0F, false, sensitivity);
            assertEquals(Math.round(rotation.getYaw() / step), rotation.getYaw() / step, 0.001F);
            assertTrue(rotation.getYaw() <= 23.0F);
            for (int i = 0; i < 50; i++) rotation.advance(95.0F, 180.0F, 23.0F, false, sensitivity);
            assertEquals(90.0F, rotation.getPitch(), step + 0.001F);
        }
    }

    @Test public void accelerationRampsUpAndSettlesAfterTargetReversal() {
        AuraRotation rotation = new AuraRotation();
        rotation.reset(0, 0);
        rotation.advance(150, 30, 2, true, 0.5F);
        float first = rotation.getYaw();
        rotation.advance(150, 30, 2, true, 0.5F);
        assertTrue(rotation.getYaw() - first > first);
        for (int i = 0; i < 100; i++) rotation.advance(-30, -20, 2, true, 0.5F);
        assertEquals(0.0F, RotationMath.difference(-30, rotation.getYaw()), 0.16F);
        assertEquals(-20.0F, rotation.getPitch(), 0.16F);
    }
}

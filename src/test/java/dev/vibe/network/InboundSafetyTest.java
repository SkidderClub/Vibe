package dev.vibe.network;

import java.util.Collections;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S2APacketParticles;
import net.minecraft.util.EnumParticleTypes;
import org.junit.Test;
import static org.junit.Assert.*;

public class InboundSafetyTest {
    @Test public void rejectsNonFiniteCoordinatesAndUnboundedParticleCounts() {
        assertTrue(InboundSafety.valid(position(42, 90)));
        assertFalse(InboundSafety.valid(position(Double.NaN, 90)));
        assertFalse(InboundSafety.valid(position(42, Float.POSITIVE_INFINITY)));
        assertTrue("Zero is vanilla's single-particle form", InboundSafety.valid(particles(0, 1)));
        assertTrue(InboundSafety.valid(particles(12, .1F)));
        assertFalse(InboundSafety.valid(particles(-1, 1)));
        assertFalse(InboundSafety.valid(particles(10001, 1)));
        assertFalse(InboundSafety.valid(particles(1, Float.NaN)));
    }

    private static S08PacketPlayerPosLook position(double x, float yaw) {
        return new S08PacketPlayerPosLook(x, 64, 0, yaw, 0, Collections.emptySet());
    }

    private static S2APacketParticles particles(int count, float speed) {
        return new S2APacketParticles(EnumParticleTypes.FLAME, false, 1, 64, 1, .1F, .1F, .1F, speed, count);
    }
}

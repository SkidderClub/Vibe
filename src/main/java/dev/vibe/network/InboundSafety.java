package dev.vibe.network;

import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S2APacketParticles;

/** Checks decoded play packets before they reach the world or the renderer. */
public final class InboundSafety {
    private InboundSafety() { }

    public static boolean reject(Object message) {
        return !valid(message) && dev.vibe.module.impl.client.QolModule.active(dev.vibe.module.impl.client.QolModule.ANTI_CRASH);
    }

    public static boolean valid(Object message) {
        if (message instanceof S08PacketPlayerPosLook) {
            S08PacketPlayerPosLook position = (S08PacketPlayerPosLook) message;
            return Double.isFinite(position.getX()) && Double.isFinite(position.getY())
                    && Double.isFinite(position.getZ()) && Float.isFinite(position.getYaw())
                    && Float.isFinite(position.getPitch());
        }
        if (message instanceof S2APacketParticles) {
            S2APacketParticles particles = (S2APacketParticles) message;
            return particles.getParticleCount() >= 0 && particles.getParticleCount() <= 10000
                    && Double.isFinite(particles.getXCoordinate()) && Double.isFinite(particles.getYCoordinate())
                    && Double.isFinite(particles.getZCoordinate()) && Float.isFinite(particles.getXOffset())
                    && Float.isFinite(particles.getYOffset()) && Float.isFinite(particles.getZOffset())
                    && Float.isFinite(particles.getParticleSpeed());
        }
        return true;
    }
}

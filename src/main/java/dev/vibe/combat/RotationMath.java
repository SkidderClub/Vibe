package dev.vibe.combat;

/** Angle differences wrap; accumulated/server yaw must never wrap. */
public final class RotationMath {
    private RotationMath() { }

    public static float difference(float target, float current) {
        double delta = ((double) target - current) % 360.0D;
        if (delta >= 180.0D) delta -= 360.0D;
        if (delta < -180.0D) delta += 360.0D;
        return (float) delta;
    }

    public static float nearest(float target, float current) {
        return current + difference(target, current);
    }

    public static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public static boolean withinAngle(float yaw, float pitch, float cameraYaw, float cameraPitch, float limit) {
        return Math.abs(difference(yaw, cameraYaw)) <= limit && Math.abs(pitch - cameraPitch) <= limit;
    }
}

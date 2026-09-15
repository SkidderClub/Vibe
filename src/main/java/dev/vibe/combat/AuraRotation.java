package dev.vibe.combat;

/** Tick-based mouse steps with separate yaw/pitch momentum. */
public final class AuraRotation {
    private float yaw;
    private float pitch;
    private float yawVelocity;
    private float pitchVelocity;
    private boolean accelerating;

    public void reset(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = RotationMath.clamp(pitch, -90.0F, 90.0F);
        yawVelocity = pitchVelocity = 0.0F;
    }

    public void advance(float targetYaw, float targetPitch, float amount, boolean acceleration, float sensitivity) {
        if (acceleration != accelerating) yawVelocity = pitchVelocity = 0.0F;
        accelerating = acceleration;
        float yawError = RotationMath.difference(targetYaw, yaw);
        float pitchError = RotationMath.clamp(targetPitch, -90.0F, 90.0F) - pitch;
        if (acceleration) {
            yawVelocity = accelerate(yawVelocity, yawError, amount);
            pitchVelocity = accelerate(pitchVelocity, pitchError, amount);
        } else {
            yawVelocity = RotationMath.clamp(yawError, -amount, amount);
            pitchVelocity = RotationMath.clamp(pitchError, -amount, amount);
        }
        float factor = sensitivity * 0.6F + 0.2F;
        float mouseStep = factor * factor * factor * 8.0F * 0.15F;
        yaw += quantize(yawVelocity, mouseStep);
        pitch = RotationMath.clamp(pitch + quantize(pitchVelocity, mouseStep), -90.0F, 90.0F);
    }

    private float accelerate(float velocity, float error, float acceleration) {
        // Braking distance determines speed; acceleration bounds changes in
        // momentum. Small errors decelerate instead of snapping to the point.
        float desired = Math.copySign(Math.min(90.0F, (float) Math.sqrt(2.0F * acceleration * Math.abs(error))), error);
        float next = velocity + RotationMath.clamp(desired - velocity, -acceleration, acceleration);
        if (Math.signum(next) == Math.signum(error) && Math.abs(next) > Math.abs(error)) return error;
        return next;
    }

    private float quantize(float value, float step) {
        return (int) (value / step) * step;
    }

    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }
}

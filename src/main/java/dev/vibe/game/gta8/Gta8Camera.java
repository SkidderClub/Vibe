package dev.vibe.game.gta8;

/** Render camera. Yaw 0 looks north (-Z), 90 east; positive pitch looks down. */
public final class Gta8Camera {
    public double x, y = 2, z, yaw, pitch, fov = 70, roll;
    public boolean firstPerson;
    /** Previous frame's pose, for motion effects. */
    public double shake;

    public double forwardX() { return Math.sin(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)); }
    public double forwardY() { return -Math.sin(Math.toRadians(pitch)); }
    public double forwardZ() { return -Math.cos(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)); }
    public void set(double x, double y, double z, double yaw, double pitch) { this.x = x; this.y = y; this.z = z; this.yaw = yaw; this.pitch = pitch; }
}

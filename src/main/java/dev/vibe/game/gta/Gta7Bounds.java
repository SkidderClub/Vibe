package dev.vibe.game.gta;

/** Shared collision and ray geometry; no Minecraft or OpenGL dependency. */
public final class Gta7Bounds {
    public final double x0, y0, z0, x1, y1, z1;
    int rayStamp;
    Gta7World.Window window;
    public Gta7Bounds(double x0, double y0, double z0, double x1, double y1, double z1) {
        this.x0 = x0; this.y0 = y0; this.z0 = z0; this.x1 = x1; this.y1 = y1; this.z1 = z1;
    }
    public boolean intersects(double x, double y, double z, double radius, double height) {
        return x + radius > x0 && x - radius < x1 && z + radius > z0 && z - radius < z1 && y + height > y0 && y < y1;
    }
    public double ray(double x, double y, double z, double dx, double dy, double dz, double limit) {
        double near = 0, far = limit;
        if (Math.abs(dx) < 1e-9) { if (x < x0 || x > x1) return limit; }
        else { double a = (x0-x)/dx, b = (x1-x)/dx; near = Math.max(near, Math.min(a,b)); far = Math.min(far, Math.max(a,b)); }
        if (Math.abs(dy) < 1e-9) { if (y < y0 || y > y1) return limit; }
        else { double a = (y0-y)/dy, b = (y1-y)/dy; near = Math.max(near, Math.min(a,b)); far = Math.min(far, Math.max(a,b)); }
        if (Math.abs(dz) < 1e-9) { if (z < z0 || z > z1) return limit; }
        else { double a = (z0-z)/dz, b = (z1-z)/dz; near = Math.max(near, Math.min(a,b)); far = Math.min(far, Math.max(a,b)); }
        return far >= near ? near : limit;
    }
}

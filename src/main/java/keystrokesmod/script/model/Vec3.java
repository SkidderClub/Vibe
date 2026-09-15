package keystrokesmod.script.model;

import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;

/** Raven BS source-compatible mutable vector. */
public class Vec3 {
    public double x, y, z;
    public Vec3(double x, double y, double z) { this.x = x; this.y = y; this.z = z; }
    public Vec3(net.minecraft.util.Vec3 value) { this(value.xCoord, value.yCoord, value.zCoord); }
    public Vec3(BlockPos value) { this(value.getX(), value.getY(), value.getZ()); }
    public boolean equals(Vec3 other) { return other != null && x == other.x && y == other.y && z == other.z; }
    public Vec3 offset(Vec3 value) { return offset(value.x, value.y, value.z); }
    public Vec3 offset(double dx, double dy, double dz) { return new Vec3(x + dx, y + dy, z + dz); }
    public Vec3 translate(Vec3 value) { return offset(value); }
    public Vec3 translate(double dx, double dy, double dz) { return offset(dx, dy, dz); }
    public Vec3 ceil() { return new Vec3(Math.ceil(x), Math.ceil(y), Math.ceil(z)); }
    public Vec3 floor() { return new Vec3(Math.floor(x), Math.floor(y), Math.floor(z)); }
    public Vec3 inverse() { return new Vec3(-x, -y, -z); }
    public double distanceTo(Vec3 value) { return MathHelper.sqrt_double(distanceToSq(value)); }
    public double distanceToSq(Vec3 value) { double dx=x-value.x, dy=y-value.y, dz=z-value.z; return dx*dx+dy*dy+dz*dz; }
    public static Vec3 convert(BlockPos value) { return value == null ? null : new Vec3(value); }
    public static BlockPos getBlockPos(Vec3 value) { return new BlockPos(value.x, value.y, value.z); }
    public static net.minecraft.util.Vec3 getVec3(Vec3 value) { return new net.minecraft.util.Vec3(value.x, value.y, value.z); }
    @Override public String toString() { return "Vec3(" + x + "," + y + "," + z + ")"; }
}

package dev.vibe.combat;

import net.minecraft.block.material.Material;
import net.minecraft.util.*;
import net.minecraft.world.World;

/** Mean 1.8.9 launch trajectories. Vanilla's random projectile spread is left intact. */
public final class ProjectileAimSolver {
    public static final int HORIZON = 40;
    public enum Kind {
        THROWABLE(.9900000095367432D, .029999999329447746D),
        // FishHook adds gravity before multiplying all three axes by drag.
        ROD(.9200000166893005D, .03999999910593033D * .9200000166893005D);
        public final double drag, gravity;
        Kind(double drag, double gravity) { this.drag = drag; this.gravity = gravity; }
    }
    public static final class Shot {
        public final float yaw, pitch;
        public final double ticks;
        Shot(Vec3 velocity, double ticks) {
            yaw = (float)Math.toDegrees(Math.atan2(velocity.zCoord, velocity.xCoord)) - 90;
            pitch = (float)-Math.toDegrees(Math.atan2(velocity.yCoord, Math.hypot(velocity.xCoord, velocity.zCoord)));
            this.ticks = ticks;
        }
    }
    private ProjectileAimSolver() { }

    public static Vec3 origin(Vec3 eyes, float yaw) {
        double angle = Math.toRadians(yaw);
        return eyes.addVector(-Math.cos(angle) * .16F, -.1F, -Math.sin(angle) * .16F);
    }
    public static Vec3 velocity(float yaw, float pitch) {
        double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
        return new Vec3(-Math.sin(y) * Math.cos(p) * 1.5, -Math.sin(p) * 1.5, Math.cos(y) * Math.cos(p) * 1.5);
    }
    public static Shot solve(Vec3 eyes, Vec3 point, Vec3[] movement, Kind kind) {
        float yaw = (float)Math.toDegrees(Math.atan2(point.zCoord - eyes.zCoord, point.xCoord - eyes.xCoord)) - 90;
        Shot shot = null;
        // The launch point depends on yaw, including at very short PvP distances.
        for (int pass = 0; pass < 3; pass++) {
            shot = solveOrigin(origin(eyes, yaw), point, movement, kind);
            if (shot == null) return null;
            yaw = shot.yaw;
        }
        return shot;
    }
    private static Shot solveOrigin(Vec3 origin, Vec3 point, Vec3[] movement, Kind kind) {
        double previous = .01, previousError = error(origin, point, movement, kind, previous);
        for (double time = .25; time <= Math.min(HORIZON, movement.length - 1); time += .25) {
            double error = error(origin, point, movement, kind, time);
            if (previousError > 0 && error <= 0) {
                double low = previous, high = time;
                for (int i = 0; i < 18; i++) {
                    double mid = (low + high) * .5;
                    if (error(origin, point, movement, kind, mid) > 0) low = mid; else high = mid;
                }
                double ticks = (low + high) * .5;
                return new Shot(required(origin, point, movement, kind, ticks), ticks);
            }
            previous = time; previousError = error;
        }
        return null;
    }
    private static double error(Vec3 origin, Vec3 point, Vec3[] movement, Kind kind, double time) {
        Vec3 velocity = required(origin, point, movement, kind, time);
        return velocity.dotProduct(velocity) - 2.25;
    }
    private static Vec3 required(Vec3 origin, Vec3 point, Vec3[] movement, Kind kind, double time) {
        int n = (int)time;
        double fraction = time - n, power = Math.pow(kind.drag, n), sum = (1 - power) / (1 - kind.drag);
        double fall = kind.gravity / (1 - kind.drag) * (n - sum + fraction * (1 - power));
        sum += fraction * power;
        Vec3 offset = ArrowAimSolver.at(movement, time);
        return new Vec3((point.xCoord + offset.xCoord - origin.xCoord) / sum,
                (point.yCoord + offset.yCoord - origin.yCoord + fall) / sum,
                (point.zCoord + offset.zCoord - origin.zCoord) / sum);
    }

    /** Checks the actual limited rotation, rather than assuming aiming has finished. */
    public static boolean hits(World world, Vec3 eyes, AxisAlignedBB target, Vec3[] movement,
            Kind kind, float yaw, float pitch, double time) {
        Vec3 position = origin(eyes, yaw), velocity = velocity(yaw, pitch);
        AxisAlignedBB body = target.expand(.3, .3, .3);
        for (int tick = 1; tick <= Math.min(movement.length - 1, Math.ceil(time) + 2); tick++) {
            Vec3 next = position.add(velocity);
            if (!world.isBlockLoaded(new BlockPos(position)) || !world.isBlockLoaded(new BlockPos(next))) return false;
            if (!world.isAreaLoaded(new BlockPos(Math.min(position.xCoord, next.xCoord), Math.min(position.yCoord, next.yCoord), Math.min(position.zCoord, next.zCoord)),
                    new BlockPos(Math.max(position.xCoord, next.xCoord), Math.max(position.yCoord, next.yCoord), Math.max(position.zCoord, next.zCoord)), true)) return false;
            MovingObjectPosition block = world.rayTraceBlocks(position, next, false, true, false);
            Vec3 start = position.subtract(ArrowAimSolver.at(movement, tick - 1));
            Vec3 end = next.subtract(ArrowAimSolver.at(movement, tick));
            MovingObjectPosition hit = body.calculateIntercept(start, end);
            if (hit != null || body.isVecInside(start)) {
                double fraction = hit == null || body.isVecInside(start) ? 0 : start.distanceTo(hit.hitVec) / Math.max(1e-9, start.distanceTo(end));
                Vec3 actual = position.addVector(velocity.xCoord * fraction, velocity.yCoord * fraction, velocity.zCoord * fraction);
                if (block == null || block.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK
                        || position.squareDistanceTo(actual) < position.squareDistanceTo(block.hitVec) - 1e-6) return true;
            }
            if (block != null && block.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) return false;
            // Submerged flight is not an air trajectory (especially a buoyant fishing hook).
            if (world.getBlockState(new BlockPos(next)).getBlock().getMaterial() == Material.water) return false;
            position = next;
            velocity = new Vec3(velocity.xCoord * kind.drag, velocity.yCoord * kind.drag - kind.gravity, velocity.zCoord * kind.drag);
        }
        return false;
    }
}

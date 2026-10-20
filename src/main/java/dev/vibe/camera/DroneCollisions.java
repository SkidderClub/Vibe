package dev.vibe.camera;

import java.util.List;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;

/** Swept block collisions for a small camera volume; never moves or queries an entity. */
public final class DroneCollisions implements FpvFlight.Movement {
    public static final double RADIUS = 0.2;
    public static final double HALF_HEIGHT = 0.1;
    private static final double CONTACT_GAP = 1e-7;
    private final World world;

    public DroneCollisions(World world) { this.world = world; }

    @Override public void move(FpvFlight flight, double x, double y, double z) {
        AxisAlignedBB box = bounds(flight);
        box = separate(flight, box);
        if (box == null) {
            // If enabled deep inside terrain, hold still until there is a clear exit or collisions are disabled.
            flight.velocityX = flight.velocityY = flight.velocityZ = 0;
            return;
        }
        List<AxisAlignedBB> blocks = world.getCollisionBoxes(box.addCoord(x, y, z));
        double wantedX = x, wantedY = y, wantedZ = z;
        for (AxisAlignedBB block : blocks) y = block.calculateYOffset(box, y);
        box = box.offset(0, y, 0);
        for (AxisAlignedBB block : blocks) x = block.calculateXOffset(box, x);
        box = box.offset(x, 0, 0);
        for (AxisAlignedBB block : blocks) z = block.calculateZOffset(box, z);
        flight.x += x; flight.y += y; flight.z += z;
        // Outward contact normals drive rebound, scraping and attitude impulses, not entity physics.
        // A sub-pixel gap avoids floating-point overlap recovery deleting the next bounce impulse.
        if (x != wantedX) {
            double normal = wantedX < 0 ? 1 : -1;
            flight.x += normal * CONTACT_GAP; flight.impact(normal, 0, 0);
        }
        if (y != wantedY) {
            double normal = wantedY < 0 ? 1 : -1;
            flight.y += normal * CONTACT_GAP; flight.impact(0, normal, 0);
        }
        if (z != wantedZ) {
            double normal = wantedZ < 0 ? 1 : -1;
            flight.z += normal * CONTACT_GAP; flight.impact(0, 0, normal);
        }
    }

    @Override public boolean supports(FpvFlight flight) {
        AxisAlignedBB box = bounds(flight);
        for (AxisAlignedBB block : world.getCollisionBoxes(box.addCoord(0, -.002, 0))) {
            if (block.calculateYOffset(box, -.002) > -.002) return true;
        }
        return false;
    }

    private static AxisAlignedBB bounds(FpvFlight flight) {
        return new AxisAlignedBB(flight.x - RADIUS, flight.y - HALF_HEIGHT, flight.z - RADIUS,
                flight.x + RADIUS, flight.y + HALF_HEIGHT, flight.z + RADIUS);
    }

    /** Recover when toggled on inside a block (or a block is placed around the camera). */
    private AxisAlignedBB separate(FpvFlight flight, AxisAlignedBB box) {
        List<AxisAlignedBB> initial = world.getCollisionBoxes(box);
        if (initial.isEmpty()) return box;
        double nearest = Double.POSITIVE_INFINITY, shift = 0;
        int bestAxis = -1;
        for (int axis = 0; axis < 3; axis++) for (int sign = -1; sign <= 1; sign += 2) {
            double distance = 0;
            List<AxisAlignedBB> blocks = initial;
            for (int attempt = 0; attempt < 16; attempt++) {
                for (AxisAlignedBB block : blocks) {
                    double edge = axis == 0 ? (sign > 0 ? block.maxX - box.minX : block.minX - box.maxX)
                            : axis == 1 ? (sign > 0 ? block.maxY - box.minY : block.minY - box.maxY)
                            : (sign > 0 ? block.maxZ - box.minZ : block.minZ - box.maxZ);
                    edge += sign * 1e-7;
                    distance = sign > 0 ? Math.max(distance, edge) : Math.min(distance, edge);
                }
                if (Math.abs(distance) > 8 || Math.abs(distance) >= nearest) break;
                blocks = world.getCollisionBoxes(offset(box, axis, distance));
                if (blocks.isEmpty()) {
                    nearest = Math.abs(distance); shift = distance; bestAxis = axis;
                    break;
                }
            }
        }
        if (bestAxis < 0) return null;
        if (bestAxis == 0) { flight.x += shift; flight.velocityX = 0; }
        if (bestAxis == 1) { flight.y += shift; flight.velocityY = 0; }
        if (bestAxis == 2) { flight.z += shift; flight.velocityZ = 0; }
        return offset(box, bestAxis, shift);
    }

    private static AxisAlignedBB offset(AxisAlignedBB box, int axis, double distance) {
        return box.offset(axis == 0 ? distance : 0, axis == 1 ? distance : 0, axis == 2 ? distance : 0);
    }
}

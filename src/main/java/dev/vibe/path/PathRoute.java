package dev.vibe.path;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.util.BlockPos;
import net.minecraft.util.Vec3;

/** Immutable world-space polyline. Only collinear vertices are removed; no corner-cutting smoothing. */
public final class PathRoute {
    public static final PathRoute EMPTY = new PathRoute(Collections.<Vec3>emptyList(), Collections.<BlockPos>emptyList(), false, null);
    public final List<Vec3> points;
    /** Unsimplified walking nodes (including start), retained for safe, topology-aware repair. */
    public final List<BlockPos> nodes;
    public final boolean complete;
    public final BlockPos end;

    private PathRoute(List<Vec3> points, List<BlockPos> nodes, boolean complete, BlockPos end) {
        this.points = Collections.unmodifiableList(points); this.nodes = Collections.unmodifiableList(nodes);
        this.complete = complete; this.end = end;
    }

    public static PathRoute build(BlockPos start, List<BlockPos> steps, PathTerrain terrain, boolean complete) {
        List<Vec3> points = new ArrayList<>();
        List<BlockPos> nodes = new ArrayList<>(); nodes.add(start);
        BlockPos previous = start;
        Vec3 point = centre(start, terrain); add(points, point);
        for (BlockPos step : steps) {
            Vec3 next = centre(step, terrain);
            if (points.size() + (Math.abs(next.yCoord - point.yCoord) > .01 ? 2 : 1) > 512) {
                complete = false; break;
            }
            // Draw the actual raised jump / ledge drop, not a diagonal through the solid step.
            boolean swim=terrain.isSwimming(previous)||terrain.isSwimming(step);
            if (!swim && next.yCoord > point.yCoord + .01) add(points, new Vec3(point.xCoord, next.yCoord, point.zCoord));
            if (!swim && next.yCoord < point.yCoord - .01) add(points, new Vec3(next.xCoord, point.yCoord, next.zCoord));
            add(points, next); nodes.add(step); point = next; previous = step;
            if (points.size() >= 512) { complete = false; break; }
        }
        return new PathRoute(points, nodes, complete, previous);
    }

    private static Vec3 centre(BlockPos pos, PathTerrain terrain) {
        double y = terrain.standingY(pos);
        return new Vec3(pos.getX() + .5, Double.isNaN(y) ? pos.getY() : y, pos.getZ() + .5);
    }

    private static void add(List<Vec3> points, Vec3 next) {
        if (!points.isEmpty() && points.get(points.size() - 1).squareDistanceTo(next) < 1e-12) return;
        if (points.size() >= 2) {
            Vec3 a = points.get(points.size() - 2), b = points.get(points.size() - 1);
            double x1 = b.xCoord-a.xCoord, y1 = b.yCoord-a.yCoord, z1 = b.zCoord-a.zCoord;
            double x2 = next.xCoord-b.xCoord, y2 = next.yCoord-b.yCoord, z2 = next.zCoord-b.zCoord;
            double cx = y1*z2-z1*y2, cy = z1*x2-x1*z2, cz = x1*y2-y1*x2;
            if (cx*cx+cy*cy+cz*cz < 1e-12 && x1*x2+y1*y2+z1*z2 > 0) points.remove(points.size() - 1);
        }
        points.add(next);
    }
}

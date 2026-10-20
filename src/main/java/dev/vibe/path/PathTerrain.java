package dev.vibe.path;

import dev.vibe.movement.WalkPathfinder;
import dev.vibe.movement.WorldWalkTerrain;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.entity.Entity;
import net.minecraft.util.BlockPos;
import net.minecraft.world.World;

/** One-search geometry cache; discarded on every replan so block changes cannot accumulate. */
public final class PathTerrain implements WalkPathfinder.Terrain {
    private final WorldWalkTerrain terrain;
    private static final int CACHE_LIMIT = 32768;
    private final Map<BlockPos, Cell> cells = new HashMap<>();

    public PathTerrain(World world, Entity player) { terrain = new WorldWalkTerrain(world, player); }

    @Override public boolean canStand(BlockPos feet) {
        return cell(feet).standable;
    }

    @Override public boolean canMove(BlockPos from, BlockPos to) {
        return terrain.canMove(from, to, standingY(from), standingY(to));
    }

    public double standingY(BlockPos feet) {
        return cell(feet).height;
    }

    private Cell cell(BlockPos feet) {
        Cell value = cells.get(feet);
        if (value == null) {
            double height = terrain.standingY(feet);
            value = new Cell(terrain.canStand(feet, height), height);
            if (cells.size() < CACHE_LIMIT) cells.put(feet, value);
        }
        return value;
    }

    private static final class Cell {
        final boolean standable;
        final double height;
        Cell(boolean standable, double height) { this.standable = standable; this.height = height; }
    }

    /** Airborne actors route to the ground below them, not an invented platform in mid-air. */
    public BlockPos grounded(BlockPos feet) {
        for (int down = 0; down <= 3; down++) {
            BlockPos candidate = feet.down(down);
            if (canStand(candidate)) return candidate;
        }
        return null;
    }
}

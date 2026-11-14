package dev.vibe.movement;

import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

/** Exire VoidUtil's loaded-column scan and twenty-tick gravity/drag prediction. */
public final class RewoundVoid {
    private static final BitSet UNKNOWN = new BitSet(0);
    private final Map<Long, BitSet> columns = new HashMap<>();
    private World world;
    private long tick = Long.MIN_VALUE;

    public void clear() { columns.clear(); world = null; tick = Long.MIN_VALUE; }

    public boolean isInVoid(Entity entity) {
        World current = entity.worldObj;
        long currentTick = current.getTotalWorldTime();
        if (current != world || currentTick != tick) { columns.clear(); world = current; tick = currentTick; }
        double x = entity.posX, y = entity.posY, z = entity.posZ;
        if (!Double.isNaN(groundBelow(x, y, z))) return false;
        double mx = entity.motionX, my = entity.motionY, mz = entity.motionZ;
        for (int i = 0; i < 20 && y > -64; i++) {
            x += mx *= .91;
            y += my = (my - .08) * .98;
            z += mz *= .91;
            if (!Double.isNaN(groundBelow(x, y, z))) return false;
            if (Math.abs(mx) < .003 && Math.abs(mz) < .003) break;
        }
        return true;
    }

    private double groundBelow(double x, double y, double z) {
        int below = Math.min(255, (int) Math.floor(y - .01));
        if (below < 0) return Double.NaN;
        double ground = Double.NaN;
        for (int bx = (int) Math.floor(x - .3); bx <= (int) Math.floor(x + .3); bx++) {
            for (int bz = (int) Math.floor(z - .3); bz <= (int) Math.floor(z + .3); bz++) {
                long key = (long) bx << 32 | (long) bz & 0xffffffffL;
                BitSet column = columns.get(key);
                if (column == null) { column = scan(bx, bz); columns.put(key, column); }
                // Unknown chunks must never be mistaken for confirmed void.
                if (column == UNKNOWN) return y;
                int top = column.previousSetBit(below);
                if (top >= 0 && (Double.isNaN(ground) || top + 1 > ground)) ground = top + 1;
            }
        }
        return ground;
    }

    private BitSet scan(int x, int z) {
        if (!world.getChunkProvider().chunkExists(x >> 4, z >> 4)) return UNKNOWN;
        Chunk chunk = world.getChunkFromChunkCoords(x >> 4, z >> 4);
        if (chunk.isEmpty()) return UNKNOWN;
        BitSet stops = new BitSet(256);
        ExtendedBlockStorage[] sections = chunk.getBlockStorageArray();
        for (int section = 0; section < sections.length; section++) {
            ExtendedBlockStorage storage = sections[section];
            if (storage == null || storage.isEmpty()) continue;
            for (int y = 0; y < 16; y++) {
                Material material = storage.getBlockByExtId(x & 15, y, z & 15).getMaterial();
                if (material.blocksMovement() || material.isLiquid()) stops.set(section * 16 + y);
            }
        }
        return stops;
    }
}

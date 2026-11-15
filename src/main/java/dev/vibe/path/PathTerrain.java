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
    private final java.util.function.Predicate<BlockPos> allowed;
    private final World world;
    private final Entity player;
    private static final int CACHE_LIMIT = 32768;
    private final Map<BlockPos, Cell> cells = new HashMap<>();

    public PathTerrain(World world, Entity player) { this(world,player,p->true); }
    public PathTerrain(World world, Entity player,java.util.function.Predicate<BlockPos> allowed) { terrain = new WorldWalkTerrain(world, player,true);this.allowed=allowed;this.world=world;this.player=player; }
    /** Villagers can stand on decorations or just below an area boundary. Route to a reachable interaction spot. */
    public BlockPos interactionGoal(Entity target){
        BlockPos origin=new BlockPos(target.posX,target.posY+.05,target.posZ),best=null;double cost=Double.MAX_VALUE;
        for(BlockPos p:BlockPos.getAllInBox(origin.add(-2,-2,-2),origin.add(2,2,2))){
            if(!canStand(p))continue;double y=standingY(p),dx=p.getX()+.5-target.posX,dz=p.getZ()+.5-target.posZ,dy=y-target.posY;
            if(dx*dx+dy*dy+dz*dz>6.25)continue;
            net.minecraft.util.Vec3 eyes=new net.minecraft.util.Vec3(p.getX()+.5,y+player.getEyeHeight(),p.getZ()+.5),aim=new net.minecraft.util.Vec3(target.posX,target.posY+target.height*.65,target.posZ);
            if(world.rayTraceBlocks(eyes,aim,false,true,false)!=null)continue;
            double distance=player.getDistanceSq(p.getX()+.5,y,p.getZ()+.5)+.05*(dx*dx+dy*dy+dz*dz);
            if(distance<cost){cost=distance;best=p;}
        }return best;
    }

    @Override public boolean canStand(BlockPos feet) {
        return allowed.test(feet)&&cell(feet).standable;
    }

    @Override public boolean canMove(BlockPos from, BlockPos to) {
        return allowed.test(from)&&allowed.test(to)&&terrain.canMove(from, to, standingY(from), standingY(to));
    }

    public double standingY(BlockPos feet) {
        return cell(feet).height;
    }
    @Override public boolean isSwimming(BlockPos feet) { return cell(feet).swimming; }

    private Cell cell(BlockPos feet) {
        Cell value = cells.get(feet);
        if (value == null) {
            double height = terrain.standingY(feet);
            value = new Cell(terrain.canStand(feet, height), height,terrain.isSwimming(feet));
            if (cells.size() < CACHE_LIMIT) cells.put(feet, value);
        }
        return value;
    }

    private static final class Cell {
        final boolean standable,swimming;
        final double height;
        Cell(boolean standable, double height,boolean swimming) { this.standable = standable; this.height = height;this.swimming=swimming; }
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

package dev.vibe.movement;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.world.World;

/** Uses block collision boxes, including slabs/fences, without treating nearby players as walls. */
public final class WorldWalkTerrain implements WalkPathfinder.Terrain {
    private final World world;
    private final Entity player;
    public WorldWalkTerrain(World world, Entity player) { this.world=world;this.player=player; }

    public boolean canStand(BlockPos feet) {
        if (!loadedAndSafe(feet.down()) || !loadedAndSafe(feet)) return false;
        double y=standingY(feet);
        return !Double.isNaN(y) && clear(body(feet,y));
    }
    public boolean canMove(BlockPos from, BlockPos to) {
        double fromY=standingY(from),toY=standingY(to);
        if(Double.isNaN(fromY))fromY=from.getY();
        if(Double.isNaN(toY)||toY-fromY>1.1||fromY-toY>3)return false;
        double height = Math.max(fromY,toY);
        // Sweep at the raised height when jumping and check the entire descent.
        AxisAlignedBB start = body(from,height);
        AxisAlignedBB end = body(to,height);
        return clear(start.union(end)) && clear(end.union(body(to,toY)));
    }
    /** Nodes denote the block containing the feet; slabs may lift them by half a block. */
    public double standingY(BlockPos pos) {
        AxisAlignedBB support=new AxisAlignedBB(pos.getX()+.2,pos.getY()-.01,pos.getZ()+.2,
                pos.getX()+.8,pos.getY()+.51,pos.getZ()+.8);
        double result=Double.NaN;
        for(AxisAlignedBB collision:collisions(support))
            if(collision.maxY>=pos.getY() && collision.maxY<=pos.getY()+.5)
                result=Double.isNaN(result)?collision.maxY:Math.max(result,collision.maxY);
        return result;
    }
    private AxisAlignedBB body(BlockPos pos,double y) {
        double half = Math.max(.3,player.width*.5);
        return new AxisAlignedBB(pos.getX()+.5-half,y+.001,pos.getZ()+.5-half,
                pos.getX()+.5+half,y+Math.max(1.8,player.height),pos.getZ()+.5+half);
    }
    private boolean clear(AxisAlignedBB box) {
        for (BlockPos pos : BlockPos.getAllInBox(new BlockPos(box.minX,box.minY,box.minZ),new BlockPos(box.maxX,box.maxY,box.maxZ)))
            if (!loadedAndSafe(pos)) return false;
        return collisions(box).isEmpty();
    }
    private boolean loadedAndSafe(BlockPos pos) {
        if (!world.isBlockLoaded(pos)) return false;
        Block block = world.getBlockState(pos).getBlock();
        return !block.getMaterial().isLiquid() && block!=Blocks.fire && block!=Blocks.cactus;
    }
    private List<AxisAlignedBB> collisions(AxisAlignedBB box) {
        List<AxisAlignedBB> result = new ArrayList<>();
        for (BlockPos pos : BlockPos.getAllInBox(new BlockPos(box.minX,box.minY-1,box.minZ),new BlockPos(box.maxX,box.maxY,box.maxZ))) {
            IBlockState state=world.getBlockState(pos);
            state.getBlock().addCollisionBoxesToList(world,pos,state,box,result,player);
        }
        return result;
    }
}

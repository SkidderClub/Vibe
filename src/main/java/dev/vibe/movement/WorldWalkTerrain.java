package dev.vibe.movement;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
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
    private final boolean allowSwimming;
    public WorldWalkTerrain(World world, Entity player) { this(world,player,false); }
    public WorldWalkTerrain(World world, Entity player,boolean allowSwimming) {
        this.world=world;this.player=player;this.allowSwimming=allowSwimming;
    }

    @Override public boolean isSwimming(BlockPos feet) {
        return allowSwimming && (water(feet) || water(feet.down()));
    }
    private boolean water(BlockPos pos) {
        return world.isBlockLoaded(pos) && world.getBlockState(pos).getBlock().getMaterial()==Material.water;
    }

    public boolean canStand(BlockPos feet) {
        return canStand(feet, standingY(feet));
    }
    /** Allows a bounded-search caller to reuse its cached support height. */
    public boolean canStand(BlockPos feet, double y) {
        if (!loadedAndSafe(feet.down()) || !loadedAndSafe(feet)) return false;
        return !Double.isNaN(y) && clear(body(feet,y));
    }
    public boolean canMove(BlockPos from, BlockPos to) {
        return canMove(from, to, standingY(from), standingY(to));
    }
    public boolean canMove(BlockPos from, BlockPos to, double fromY, double toY) {
        if(Double.isNaN(fromY))fromY=from.getY();
        boolean swimming=isSwimming(from);
        if(Double.isNaN(toY)||toY-fromY>(swimming&&isSwimming(to)?2:1.1)||fromY-toY>3)return false;
        double height = Math.max(fromY,toY);
        // Sweep at the raised height when jumping and check the entire descent.
        AxisAlignedBB start = body(from,height);
        AxisAlignedBB end = body(to,height);
        return (!swimming || clear(body(from,fromY).union(start)))
                && clear(start.union(end)) && clear(end.union(body(to,toY)));
    }
    /** Nodes denote the block containing the feet; slabs may lift them by half a block. */
    public double standingY(BlockPos pos) {
        if (isSwimming(pos)) {
            BlockPos liquid=water(pos)?pos:pos.down();
            if (water(liquid.up())) return pos.getY();
            IBlockState state=world.getBlockState(liquid);
            double surface=liquid.getY()+1-BlockLiquid.getLiquidHeightPercent(state.getValue(BlockLiquid.LEVEL));
            // Keep the feet immersed at the surface, rather than inventing
            // solid ground above the water. Flowing water uses its real level.
            return Math.max(liquid.getY(),surface-.4);
        }
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
        return (!block.getMaterial().isLiquid() || allowSwimming && block.getMaterial()==Material.water)
                && block!=Blocks.fire && block!=Blocks.cactus;
    }
    private List<AxisAlignedBB> collisions(AxisAlignedBB box) {
        List<AxisAlignedBB> result = new ArrayList<>();
        // Shape implementations (stairs, panes, fences) inspect neighbouring blocks too.
        for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++)
            if (!world.isBlockLoaded(new BlockPos((x == 0 ? box.minX - 1 : box.maxX + 1), box.minY,
                    (z == 0 ? box.minZ - 1 : box.maxZ + 1)))) return java.util.Collections.singletonList(box);
        for (BlockPos pos : BlockPos.getAllInBox(new BlockPos(box.minX,box.minY-1,box.minZ),new BlockPos(box.maxX,box.maxY,box.maxZ))) {
            // Unknown terrain is closed, not air. In particular standingY must never request a chunk.
            if (!world.isBlockLoaded(pos)) return java.util.Collections.singletonList(box);
            IBlockState state=world.getBlockState(pos);
            state.getBlock().addCollisionBoxesToList(world,pos,state,box,result,player);
        }
        return result;
    }
}

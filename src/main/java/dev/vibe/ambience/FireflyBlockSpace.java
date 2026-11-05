package dev.vibe.ambience;

import java.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.util.*;
import net.minecraft.world.World;

/** Read only loaded block collision shapes, including fences and other tall shapes. */
public final class FireflyBlockSpace implements FireflySwarm.Space {
    private final World world;
    private final Entity view;
    public FireflyBlockSpace(World world,Entity view){this.world=world;this.view=view;}
    public boolean available(double x,double y,double z){
        AxisAlignedBB box=new AxisAlignedBB(x-.07,y-.07,z-.07,x+.07,y+.07,z+.07);
        return loaded(box)&&!world.getBlockState(new BlockPos(x,y,z)).getBlock().getMaterial().isLiquid();
    }
    public boolean loaded(AxisAlignedBB b){
        // Connected shapes can inspect their neighbours while building collision boxes.
        return world.isAreaLoaded(new BlockPos(b.minX-1,Math.max(0,b.minY-1),b.minZ-1),new BlockPos(b.maxX+1,b.maxY,b.maxZ+1));
    }
    public List<AxisAlignedBB> collisions(AxisAlignedBB area){
        if(!loaded(area))return Collections.emptyList();
        List<AxisAlignedBB> boxes=new ArrayList<>();
        for(int x=MathHelper.floor_double(area.minX);x<=MathHelper.floor_double(area.maxX);x++)
            for(int z=MathHelper.floor_double(area.minZ);z<=MathHelper.floor_double(area.maxZ);z++)
                for(int y=Math.max(0,MathHelper.floor_double(area.minY)-1);y<=Math.min(255,MathHelper.floor_double(area.maxY));y++){
                    BlockPos pos=new BlockPos(x,y,z);IBlockState state=world.getBlockState(pos);
                    state.getBlock().addCollisionBoxesToList(world,pos,state,area,boxes,view);
                }
        return boxes;
    }
}

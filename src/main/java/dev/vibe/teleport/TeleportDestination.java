package dev.vibe.teleport;

import java.util.*;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.*;
import net.minecraft.world.World;

/** Loaded collision shapes define the landing height; slabs and fences are not full cubes. */
public final class TeleportDestination {
    private TeleportDestination(){}
    public static MovingObjectPosition look(World world,Vec3 eyes,Vec3 direction,double range){
        Vec3 end=eyes.addVector(direction.xCoord*range,direction.yCoord*range,direction.zCoord*range);
        if(!rayAreaLoaded(world,eyes,end)){
            // A block nearby can still be selected when the configured range extends past loaded chunks.
            double low=0,high=range;
            for(int i=0;i<12;i++){double mid=(low+high)/2;Vec3 point=eyes.addVector(direction.xCoord*mid,direction.yCoord*mid,direction.zCoord*mid);if(rayAreaLoaded(world,eyes,point))low=mid;else high=mid;}
            if(low<.01)return null;double distance=Math.max(0,low-.001);end=eyes.addVector(direction.xCoord*distance,direction.yCoord*distance,direction.zCoord*distance);
        }
        return world.rayTraceBlocks(eyes,end,false,true,false);
    }
    private static boolean rayAreaLoaded(World world,Vec3 from,Vec3 to){return loaded(world,new AxisAlignedBB(from.xCoord,from.yCoord,from.zCoord,to.xCoord,to.yCoord,to.zCoord).expand(1,0,1));}
    public static Vec3 above(World world,EntityPlayer player,BlockPos pos){
        if(pos==null||!world.isBlockLoaded(pos))return null;
        IBlockState state=world.getBlockState(pos);Material material=state.getBlock().getMaterial();
        if(material.isLiquid()||material==Material.fire||state.getBlock()==net.minecraft.init.Blocks.cactus)return null;
        AxisAlignedBB area=new AxisAlignedBB(pos.getX()-1,pos.getY()-.01,pos.getZ()-1,pos.getX()+2,pos.getY()+2,pos.getZ()+2);
        if(!loaded(world,area))return null;
        List<AxisAlignedBB> supports=new ArrayList<>();state.getBlock().addCollisionBoxesToList(world,pos,state,area,supports,player);
        double x=pos.getX()+.5,z=pos.getZ()+.5,y=Double.NaN;
        for(AxisAlignedBB shape:supports)if(x>=shape.minX&&x<=shape.maxX&&z>=shape.minZ&&z<=shape.maxZ)y=Double.isNaN(y)?shape.maxY:Math.max(y,shape.maxY);
        if(Double.isNaN(y)||y<0||y+player.height>256)return null;
        double half=Math.max(.3,player.width*.5);AxisAlignedBB body=new AxisAlignedBB(x-half,y+.001,z-half,x+half,y+player.height,z+half);
        if(!loaded(world,body.expand(1,0,1)))return null;
        if(world.getWorldBorder()!=null&&!world.getWorldBorder().contains(new BlockPos(x,y,z)))return null;
        for(BlockPos cell:BlockPos.getAllInBox(new BlockPos(body.minX,body.minY-1,body.minZ),new BlockPos(body.maxX,body.maxY,body.maxZ))){
            if(!world.isBlockLoaded(cell))return null;IBlockState block=world.getBlockState(cell);
            Material m=block.getBlock().getMaterial();if(cell.getY()>=MathHelper.floor_double(y)&&(m==Material.lava||m==Material.fire||block.getBlock()==net.minecraft.init.Blocks.cactus))return null;
            List<AxisAlignedBB> collisions=new ArrayList<>();block.getBlock().addCollisionBoxesToList(world,cell,block,body,collisions,player);if(!collisions.isEmpty())return null;
        }
        return new Vec3(x,y,z);
    }
    public static boolean loaded(World world,AxisAlignedBB b){
        for(int x=MathHelper.floor_double(b.minX)>>4;x<=(MathHelper.floor_double(b.maxX)>>4);x++)for(int z=MathHelper.floor_double(b.minZ)>>4;z<=(MathHelper.floor_double(b.maxZ)>>4);z++)
            if(!world.isBlockLoaded(new BlockPos(x*16,64,z*16)))return false;return true;
    }
}

package dev.vibe.ui.render.esp;

import java.util.*;
import net.minecraft.entity.item.EntityTNTPrimed;
import net.minecraft.util.*;
import net.minecraft.world.World;

/** Primed TNT's native gravity, drag, collision clipping and ground bounce until its observed fuse ends. */
public final class TntPrediction {
    private TntPrediction(){}
    public static final class Result {public final Vec3 position;public final List<Vec3> path;public final boolean complete;Result(Vec3 p,List<Vec3> path,boolean complete){position=p;this.path=Collections.unmodifiableList(path);this.complete=complete;}}
    public static Result current(EntityTNTPrimed e){Vec3 p=new Vec3(e.posX,e.posY+e.height/16D,e.posZ);return new Result(p,Collections.singletonList(p),false);}
    public static Result simulate(World world,EntityTNTPrimed e){
        AxisAlignedBB box=e.getEntityBoundingBox();double vx=e.motionX,vy=e.motionY,vz=e.motionZ;List<Vec3> path=new ArrayList<>();path.add(new Vec3(e.posX,e.posY+e.height/16D,e.posZ));
        // 1.8.9 tests fuse-- <= 0 after movement, so fuse zero still has one movement tick.
        int steps=Math.max(1,e.fuse+1);boolean complete=steps<=400;
        for(int tick=0;tick<Math.min(400,steps);tick++){
            vy-=.03999999910593033D;double dx=vx,dy=vy,dz=vz;AxisAlignedBB swept=box.addCoord(dx,dy,dz);
            if(!world.isAreaLoaded(new BlockPos(swept.minX,swept.minY,swept.minZ),new BlockPos(swept.maxX,swept.maxY,swept.maxZ))){complete=false;break;}
            List<AxisAlignedBB> collisions=world.getCollidingBoundingBoxes(e,swept);
            for(AxisAlignedBB b:collisions)dy=b.calculateYOffset(box,dy);box=box.offset(0,dy,0);
            for(AxisAlignedBB b:collisions)dx=b.calculateXOffset(box,dx);box=box.offset(dx,0,0);
            for(AxisAlignedBB b:collisions)dz=b.calculateZOffset(box,dz);box=box.offset(0,0,dz);
            boolean grounded=vy<0&&vy!=dy;if(vx!=dx)vx=0;if(vy!=dy)vy=0;if(vz!=dz)vz=0;
            vx*=.9800000190734863D;vy*=.9800000190734863D;vz*=.9800000190734863D;
            if(grounded){vx*=.699999988079071D;vz*=.699999988079071D;vy*= -.5D;}
            if(tick%3==0||tick==steps-1)path.add(new Vec3((box.minX+box.maxX)/2,box.minY+e.height/16D,(box.minZ+box.maxZ)/2));
        }
        Vec3 end=new Vec3((box.minX+box.maxX)/2,box.minY+e.height/16D,(box.minZ+box.maxZ)/2);return new Result(end,path,complete);
    }
}

package dev.vibe.combat;

import net.minecraft.util.*;
import net.minecraft.world.World;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.block.material.Material;
import java.util.*;

/** Native 1.8.9 arrow drag/gravity, moving-body collision forecasts, and segment-by-segment clearance. */
public final class ArrowAimSolver {
    public static final double DRAG=.9900000095367432D,GRAVITY=.05000000074505806D;
    public static final int HORIZON=120;
    private ArrowAimSolver(){}
    public static final class Shot{
        public final Vec3 origin,velocity,point;public final double ticks;public final float yaw,pitch;
        Shot(Vec3 origin,Vec3 velocity,Vec3 point,double ticks){this.origin=origin;this.velocity=velocity;this.point=point;this.ticks=ticks;yaw=(float)Math.toDegrees(Math.atan2(velocity.zCoord,velocity.xCoord))-90;pitch=(float)-Math.toDegrees(Math.atan2(velocity.yCoord,Math.hypot(velocity.xCoord,velocity.zCoord)));}
    }
    public static Shot solve(Vec3 origin,Vec3 point,Vec3[] movement,double speed){
        if(speed<.1||movement.length<2)return null;double previous=.02,previousError=error(origin,point,movement,speed,previous);
        for(double t=.25;t<=movement.length-1;t+=.5){double error=error(origin,point,movement,speed,t);
            if(previousError>0&&error<=0){double a=previous,b=t;for(int i=0;i<20;i++){double mid=(a+b)*.5;if(error(origin,point,movement,speed,mid)>0)a=mid;else b=mid;}double time=(a+b)*.5;Vec3 v=required(origin,point,movement,time);double length=v.lengthVector();return new Shot(origin,new Vec3(v.xCoord*speed/length,v.yCoord*speed/length,v.zCoord*speed/length),point,time);}
            previous=t;previousError=error;
        }
        return null;
    }
    private static double error(Vec3 origin,Vec3 point,Vec3[] movement,double speed,double t){Vec3 v=required(origin,point,movement,t);return v.dotProduct(v)-speed*speed;}
    private static Vec3 required(Vec3 origin,Vec3 point,Vec3[] movement,double t){double sum=(1-Math.pow(DRAG,t))/(1-DRAG),fall=GRAVITY/(1-DRAG)*(t-sum);Vec3 offset=at(movement,t);return new Vec3((point.xCoord+offset.xCoord-origin.xCoord)/sum,(point.yCoord+offset.yCoord-origin.yCoord+fall)/sum,(point.zCoord+offset.zCoord-origin.zCoord)/sum);}
    public static Vec3 at(Vec3[] movement,double t){int i=Math.min(movement.length-1,Math.max(0,(int)t)),j=Math.min(movement.length-1,i+1);double f=Math.max(0,Math.min(1,t-i));return movement[i].addVector((movement[j].xCoord-movement[i].xCoord)*f,(movement[j].yCoord-movement[i].yCoord)*f,(movement[j].zCoord-movement[i].zCoord)*f);}
    public static Vec3[] forecast(World world,EntityLivingBase entity,boolean predict){
        Vec3[] result=new Vec3[HORIZON+1];result[0]=new Vec3(0,0,0);AxisAlignedBB box=entity.getEntityBoundingBox();
        double startX=(box.minX+box.maxX)*.5,startY=box.minY,startZ=(box.minZ+box.maxZ)*.5;
        double vx=predict?MathHelper.clamp_double(entity.posX-entity.lastTickPosX,-1.5,1.5):0,vz=predict?MathHelper.clamp_double(entity.posZ-entity.lastTickPosZ,-1.5,1.5):0,vy=predict?MathHelper.clamp_double(entity.posY-entity.lastTickPosY,-1.5,1.5):0;
        for(int i=1;i<result.length;i++){
            if(!predict){result[i]=result[0];continue;}double dx=vx,dy=vy,dz=vz;AxisAlignedBB swept=box.addCoord(dx,dy,dz);
            if(!world.isBlockLoaded(new BlockPos(swept.minX,swept.minY,swept.minZ))||!world.isBlockLoaded(new BlockPos(swept.maxX,swept.maxY,swept.maxZ)))return Arrays.copyOf(result,i);
            java.util.List<AxisAlignedBB> collisions=world.getCollidingBoundingBoxes(entity,swept);
            for(AxisAlignedBB b:collisions)dy=b.calculateYOffset(box,dy);box=box.offset(0,dy,0);for(AxisAlignedBB b:collisions)dx=b.calculateXOffset(box,dx);box=box.offset(dx,0,0);for(AxisAlignedBB b:collisions)dz=b.calculateZOffset(box,dz);box=box.offset(0,0,dz);
            if(dx!=vx)vx=0;if(dy!=vy)vy=0;if(dz!=vz)vz=0;vy=(vy-.08D)*.9800000190734863D;
            result[i]=new Vec3((box.minX+box.maxX)*.5-startX,box.minY-startY,(box.minZ+box.maxZ)*.5-startZ);
        }
        return result;
    }
    public static boolean hits(World world,AxisAlignedBB target,Vec3[] movement,Shot shot){
        Vec3 pos=shot.origin,velocity=shot.velocity;AxisAlignedBB body=target.expand(.3,.3,.3);
        for(int tick=1;tick<=Math.min(movement.length-1,Math.ceil(shot.ticks)+3);tick++){
            Vec3 next=pos.add(velocity);if(!world.isBlockLoaded(new BlockPos(next)))return false;
            MovingObjectPosition block=world.rayTraceBlocks(pos,next,false,true,false);Vec3 before=at(movement,tick-1),after=at(movement,tick);
            MovingObjectPosition hit=body.calculateIntercept(pos.subtract(before),next.subtract(after));
            if(hit!=null){double total=pos.subtract(before).distanceTo(next.subtract(after)),fraction=total<1e-8?0:pos.subtract(before).distanceTo(hit.hitVec)/total;Vec3 actual=pos.addVector(velocity.xCoord*fraction,velocity.yCoord*fraction,velocity.zCoord*fraction);
                if(block==null||block.typeOfHit!=MovingObjectPosition.MovingObjectType.BLOCK||pos.squareDistanceTo(actual)<pos.squareDistanceTo(block.hitVec)-1e-6)return true;}
            if(block!=null&&block.typeOfHit==MovingObjectPosition.MovingObjectType.BLOCK)return false;
            pos=next;double drag=world.getBlockState(new BlockPos(pos)).getBlock().getMaterial()==Material.water?.6D:DRAG;
            velocity=new Vec3(velocity.xCoord*drag,velocity.yCoord*drag-GRAVITY,velocity.zCoord*drag);
        }
        return false;
    }
}

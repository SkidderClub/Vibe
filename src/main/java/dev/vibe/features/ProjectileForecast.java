package dev.vibe.features;

import java.util.*;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.*;
import net.minecraft.util.*;
import net.minecraft.world.World;

public final class ProjectileForecast {
    public static final class Flight {public final List<Vec3> points=new ArrayList<>();public MovingObjectPosition impact;public boolean loaded=true;}
    private ProjectileForecast(){}
    public static Entity owner(Entity projectile){return projectile instanceof EntityArrow?((EntityArrow)projectile).shootingEntity:projectile instanceof EntityThrowable?((EntityThrowable)projectile).getThrower():null;}
    public static Flight trace(World world,Entity projectile,int ticks){return trace(world,projectile,ticks,owner(projectile));}
    public static Flight trace(World world,Entity projectile,int ticks,Entity thrower){return trace(world,projectile.getPositionVector(),new Vec3(projectile.motionX,projectile.motionY,projectile.motionZ),projectile instanceof EntityArrow,.125,projectile,ticks,thrower);}
    public static Flight traceMovingPlayer(World world,Entity projectile,int ticks,Entity player){return trace(world,projectile.getPositionVector(),new Vec3(projectile.motionX,projectile.motionY,projectile.motionZ),projectile instanceof EntityArrow,.125,projectile,ticks,owner(projectile),player);}
    public static Flight trace(World world,Vec3 position,Vec3 velocity,boolean arrow,double radius,Entity source,int ticks){return trace(world,position,velocity,arrow,radius,source,ticks,source==null?null:owner(source));}
    public static Flight trace(World world,Vec3 position,Vec3 velocity,boolean arrow,double radius,Entity source,int ticks,Entity thrower){return trace(world,position,velocity,arrow,radius,source,ticks,thrower,null);}
    private static Flight trace(World world,Vec3 position,Vec3 velocity,boolean arrow,double radius,Entity source,int ticks,Entity thrower,Entity predictedPlayer){Flight result=new Flight();result.points.add(position);
        for(int i=0;i<Math.min(100,ticks);i++){
            Vec3 next=position.add(velocity);AxisAlignedBB sweep=new AxisAlignedBB(position.xCoord,position.yCoord,position.zCoord,next.xCoord,next.yCoord,next.zCoord).expand(radius,radius,radius);
            if(!MovementForecast.loaded(sweep)){result.loaded=false;break;}
            MovingObjectPosition block=world.rayTraceBlocks(position,next,false,true,false);double distance=block==null?Double.MAX_VALUE:position.squareDistanceTo(block.hitVec);MovingObjectPosition hit=block;
            for(Entity e:world.getEntitiesWithinAABBExcludingEntity(source,sweep.expand(1,1,1))){if(e==thrower||e==predictedPlayer||!e.canBeCollidedWith())continue;AxisAlignedBB body=e.getEntityBoundingBox().expand(radius,radius,radius);MovingObjectPosition intercept=body.calculateIntercept(position,next);if(intercept!=null&&position.squareDistanceTo(intercept.hitVec)<distance){distance=position.squareDistanceTo(intercept.hitVec);hit=new MovingObjectPosition(e,intercept.hitVec);}}
            if(hit!=null){result.impact=hit;result.points.add(hit.hitVec);break;}
            result.points.add(next);double drag=world.getBlockState(new BlockPos(next)).getBlock().getMaterial()==Material.water?(arrow?.6:.8):.99;
            velocity=new Vec3(velocity.xCoord*drag,velocity.yCoord*drag-(arrow?.05:.03),velocity.zCoord*drag);position=next;if(position.yCoord<0)break;
        }return result;
    }
}

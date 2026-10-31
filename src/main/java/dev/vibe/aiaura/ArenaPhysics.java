package dev.vibe.aiaura;

import java.util.*;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;

/** Minecraft 1.8 dry-land motion, using native AABB clipping against the local arena geometry. */
public final class ArenaPhysics {
    public static class Body {
        public double x,y,z,vx,vy,vz,previousX,previousY,previousZ,fallDistance;
        public float yaw,previousYaw;
        public boolean grounded=true,sprint,horizontalCollision;
        public int jumpCooldown;
        public AxisAlignedBB box(){return new AxisAlignedBB(x-.3,y,z-.3,x+.3,y+1.8,z+.3);}
    }
    private ArenaPhysics(){}
    public static void tick(Body body,float strafe,float forward,boolean jump,boolean sneak,List<RecorderArena.Pillar> pillars){
        if(body.jumpCooldown>0)body.jumpCooldown--;if(!jump)body.jumpCooldown=0;
        if(Math.abs(body.vx)<.005)body.vx=0;if(Math.abs(body.vy)<.005)body.vy=0;if(Math.abs(body.vz)<.005)body.vz=0;
        if(jump&&body.grounded&&body.jumpCooldown==0){body.vy=.41999998688697815D;if(body.sprint){float angle=body.yaw*.017453292F;body.vx-=MathHelper.sin(angle)*.2F;body.vz+=MathHelper.cos(angle)*.2F;}body.jumpCooldown=10;}
        float friction=body.grounded?.6F*.91F:.91F;
        float acceleration=body.grounded?(body.sprint?.13F:.1F)*(.16277136F/(friction*friction*friction)):(body.sprint?.026F:.02F);
        if(sneak){strafe*=.3F;forward*=.3F;}strafe*=.98F;forward*=.98F;
        float length=strafe*strafe+forward*forward;
        if(length>=1e-4F){length=MathHelper.sqrt_float(length);if(length<1)length=1;float factor=acceleration/length;strafe*=factor;forward*=factor;float rad=body.yaw*(float)Math.PI/180F,sin=MathHelper.sin(rad),cos=MathHelper.cos(rad);body.vx+=strafe*cos-forward*sin;body.vz+=forward*cos+strafe*sin;}
        move(body,body.vx,body.vy,body.vz,sneak,pillars);
        body.vy=(body.vy-.08D)*.9800000190734863D;body.vx*=friction;body.vz*=friction;
    }
    public static List<AxisAlignedBB> geometry(List<RecorderArena.Pillar> pillars){
        List<AxisAlignedBB> boxes=new ArrayList<>();boxes.add(new AxisAlignedBB(-64,-2,-64,64,0,64));
        for(RecorderArena.Pillar p:pillars)boxes.add(new AxisAlignedBB(p.x-p.width/2,0,p.z-p.width/2,p.x+p.width/2,p.height,p.z+p.width/2));
        boxes.add(new AxisAlignedBB(-25,0,-25,-24,16,25));boxes.add(new AxisAlignedBB(24,0,-25,25,16,25));boxes.add(new AxisAlignedBB(-24,0,-25,24,16,-24));boxes.add(new AxisAlignedBB(-24,0,24,24,16,25));return boxes;
    }
    public static void move(Body b,double dx,double dy,double dz,boolean sneak,List<RecorderArena.Pillar> pillars){
        List<AxisAlignedBB> boxes=geometry(pillars);AxisAlignedBB start=b.box();
        if(b.grounded&&sneak){while(dx!=0&&!supported(start.offset(dx,-1,0),boxes))dx=reduce(dx);while(dz!=0&&!supported(start.offset(0,-1,dz),boxes))dz=reduce(dz);while(dx!=0&&dz!=0&&!supported(start.offset(dx,-1,dz),boxes)){dx=reduce(dx);dz=reduce(dz);}}
        double requestedX=dx,requestedY=dy,requestedZ=dz;Move flat=clip(start,dx,dy,dz,boxes),result=flat;
        if((b.grounded||dy<0&&dy!=flat.dy)&&(dx!=flat.dx||dz!=flat.dz)){
            // Both native step candidates: upward clearance over the swept box or over the original box.
            for(boolean swept:new boolean[]{true,false}){
                double up=.6;AxisAlignedBB clearance=swept?start.addCoord(dx,0,dz):start;
                for(AxisAlignedBB box:boxes)up=box.calculateYOffset(clearance,up);
                Move raised=clip(start.offset(0,up,0),dx,0,dz,boxes);double down=-up;
                for(AxisAlignedBB box:boxes)down=box.calculateYOffset(raised.box,down);
                Move step=new Move(raised.box.offset(0,down,0),raised.dx,up+down,raised.dz);
                if(step.dx*step.dx+step.dz*step.dz>result.dx*result.dx+result.dz*result.dz)result=step;
            }
        }
        b.x=(result.box.minX+result.box.maxX)*.5;b.y=result.box.minY;b.z=(result.box.minZ+result.box.maxZ)*.5;
        b.horizontalCollision=requestedX!=result.dx||requestedZ!=result.dz;b.grounded=requestedY!=result.dy&&requestedY<0;
        if(requestedX!=result.dx)b.vx=0;if(requestedY!=result.dy)b.vy=0;if(requestedZ!=result.dz)b.vz=0;
        if(b.grounded)b.fallDistance=0;else if(result.dy<0)b.fallDistance-=result.dy;
    }
    private static Move clip(AxisAlignedBB start,double dx,double dy,double dz,List<AxisAlignedBB> boxes){
        AxisAlignedBB box=start;for(AxisAlignedBB shape:boxes)dy=shape.calculateYOffset(box,dy);box=box.offset(0,dy,0);
        for(AxisAlignedBB shape:boxes)dx=shape.calculateXOffset(box,dx);box=box.offset(dx,0,0);
        for(AxisAlignedBB shape:boxes)dz=shape.calculateZOffset(box,dz);return new Move(box.offset(0,0,dz),dx,dy,dz);
    }
    private static boolean supported(AxisAlignedBB box,List<AxisAlignedBB> boxes){for(AxisAlignedBB shape:boxes)if(shape.intersectsWith(box))return true;return false;}
    private static double reduce(double value){return Math.abs(value)<.05?0:value-Math.copySign(.05,value);}
    private static final class Move {final AxisAlignedBB box;final double dx,dy,dz;Move(AxisAlignedBB box,double dx,double dy,double dz){this.box=box;this.dx=dx;this.dy=dy;this.dz=dz;}}
    public static void knockback(Body target,double attackerX,double attackerZ){
        double dx=attackerX-target.x,dz=attackerZ-target.z;float distance=MathHelper.sqrt_double(dx*dx+dz*dz);if(distance<1e-5F)return;
        target.vx=target.vx/2-dx/distance*.4F;target.vz=target.vz/2-dz/distance*.4F;target.vy=Math.min(.4000000059604645D,target.vy/2+.4F);
    }
    public static void push(Body a,Body b){
        if(Math.abs(a.y-b.y)>1.8)return;double dx=b.x-a.x,dz=b.z-a.z,largest=Math.max(Math.abs(dx),Math.abs(dz));if(largest<.01||dx*dx+dz*dz>.64)return;
        double root=Math.sqrt(largest);dx/=root;dz/=root;double strength=Math.min(1,1/root)*.05;dx*=strength;dz*=strength;a.vx-=dx;a.vz-=dz;b.vx+=dx;b.vz+=dz;
    }
}

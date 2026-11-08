package dev.vibe.companion;

import dev.vibe.path.PathTerrain;
import dev.vibe.movement.WalkPathfinder;
import java.util.*;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.*;
import net.minecraft.world.World;

/** A local character controller: loaded block shapes only, no entity AI, inputs or world mutations. */
public final class CompanionFollower {
    public double x,y,z,previousX,previousY,previousZ;
    public float yaw,previousYaw,headYaw,previousHeadYaw,pitch,previousPitch,limbSwing,walkAmount,previousWalkAmount;
    public boolean active,onGround;
    private double vx,vy,vz;private float heading;private int ticks,stuck;
    private World world;private PathTerrain terrain;private WalkPathfinder.Search search;
    private BlockPos searchGoal;private List<Vec3> route=Collections.emptyList();private int cursor;
    private double height=1.72;
    public void clear(){world=null;active=false;vx=vy=vz=0;search=null;route=Collections.emptyList();cursor=stuck=ticks=0;limbSwing=walkAmount=previousWalkAmount=0;}
    public void tick(World next,EntityPlayer player,double distance,double radius,double speed,double scale){
        if(next==null||player==null){clear();return;}if(world!=next){clear();world=next;heading=player.rotationYaw;}
        height=1.8*scale;ticks++;
        double pdx=player.posX-player.lastTickPosX,pdz=player.posZ-player.lastTickPosZ;
        // Looking back must not move the follow anchor around the player. Only travel changes it.
        if(pdx*pdx+pdz*pdz>.0004)heading=angle(heading,(float)Math.toDegrees(Math.atan2(-pdx,pdz)),12);
        double a=Math.toRadians(heading);
        double idealX=player.posX+Math.sin(a)*distance-Math.cos(a)*.4,idealZ=player.posZ-Math.cos(a)*distance-Math.sin(a)*.4;
        if(active&&(!loaded(box(x,y,z))||!collisions(box(x,y,z)).isEmpty()))active=false;
        if(!active||sq(x-player.posX)+sq(y-player.posY)+sq(z-player.posZ)>radius*radius||stuck>35){
            Vec3 safe=placement(player,idealX,idealZ);
            if(safe==null){active=false;return;}x=previousX=safe.xCoord;y=previousY=safe.yCoord;z=previousZ=safe.zCoord;
            yaw=previousYaw=heading;headYaw=previousHeadYaw=heading;pitch=previousPitch=0;vx=vy=vz=0;active=true;stuck=0;search=null;route=Collections.emptyList();
        }
        previousX=x;previousY=y;previousZ=z;previousYaw=yaw;previousHeadYaw=headYaw;previousPitch=pitch;previousWalkAmount=walkAmount;
        if(ticks%5==0||terrain==null){
            terrain=new PathTerrain(world,player);BlockPos start=terrain.grounded(new BlockPos(x,y+.05,z)),goal=goal(player,idealX,idealZ,terrain);
            if(start!=null&&goal!=null){
                if(terrain.canMove(start,goal)){route=Collections.singletonList(new Vec3(goal.getX()+.5,terrain.standingY(goal),goal.getZ()+.5));cursor=0;search=null;}
                else if(search==null||!goal.equals(searchGoal)){search=new WalkPathfinder.Search(terrain,start,goal,10,768,6,true,3,true);searchGoal=goal;}
            }
        }
        if(search!=null){search.advance(80,System.nanoTime()+900_000);if(search.isDone()){
            List<Vec3> points=new ArrayList<>();for(BlockPos p:search.path())points.add(new Vec3(p.getX()+.5,terrain.standingY(p),p.getZ()+.5));route=points;cursor=0;search=null;
        }}
        while(cursor<route.size()&&sq(route.get(cursor).xCoord-x)+sq(route.get(cursor).zCoord-z)<.12&&Math.abs(route.get(cursor).yCoord-y)<.55)cursor++;
        Vec3 target=cursor<route.size()?route.get(cursor):new Vec3(x,y,z);
        double dx=target.xCoord-x,dz=target.zCoord-z,length=Math.sqrt(dx*dx+dz*dz),toPlayer=Math.sqrt(sq(x-player.posX)+sq(z-player.posZ));
        double velocity=length<.1?0:Math.min(.43*speed,Math.max(.1,.205*speed+(toPlayer-distance)*.055));
        if(toPlayer<.75)velocity=0;
        vx+=(length<.001?0:dx/length*velocity-vx)*.42;vz+=(length<.001?0:dz/length*velocity-vz)*.42;
        if(length<.1){vx*=.5;vz*=.5;}
        boolean water=world.isBlockLoaded(new BlockPos(x,y+.6,z))&&world.getBlockState(new BlockPos(x,y+.6,z)).getBlock().getMaterial()==Material.water;
        boolean flying=player.capabilities!=null&&player.capabilities.isFlying;
        if(flying){if(loaded(box(x,y,z).addCoord(idealX-x,player.posY-y,idealZ-z))&&collisions(box(x,y,z).addCoord(idealX-x,player.posY-y,idealZ-z)).isEmpty()){vx+=(MathHelper.clamp_double(idealX-x,-.35,.35)-vx)*.4;vz+=(MathHelper.clamp_double(idealZ-z,-.35,.35)-vz)*.4;}vy=MathHelper.clamp_double((player.posY-y)*.25,-.3,.3);}
        else if(water)vy=Math.min(.16,(vy+.055)*.8);else if(onGround&&target.yCoord-y>.45&&length<1.6)vy=.42;else vy=Math.max(-1.2,(vy-.08)*.98);
        move(vx,vy,vz);
        double moved=Math.sqrt(sq(x-previousX)+sq(z-previousZ));
        if(length>.7&&moved<.005)stuck++;else stuck=0;
        if(moved>.008)yaw=angle(yaw,(float)Math.toDegrees(Math.atan2(-(x-previousX),z-previousZ)),18);
        else yaw=angle(yaw,heading,5);
        float look=(float)Math.toDegrees(Math.atan2(-(player.posX-x),player.posZ-z));headYaw=angle(headYaw,yaw+MathHelper.clamp_float(MathHelper.wrapAngleTo180_float(look-yaw),-65,65),10);
        pitch+=(MathHelper.clamp_float((float)-Math.toDegrees(Math.atan2(player.posY-y,Math.max(.2,toPlayer))),-30,30)-pitch)*.2F;
        walkAmount+=(Math.min(1,(float)moved*4)-walkAmount)*.4F;limbSwing+=walkAmount*.75F;
    }
    private BlockPos goal(EntityPlayer player,double ix,double iz,PathTerrain t){
        BlockPos best=null;double score=Double.POSITIVE_INFINITY;BlockPos base=new BlockPos(ix,player.posY+.05,iz);
        for(int r=0;r<=2;r++)for(int xx=-r;xx<=r;xx++)for(int zz=-r;zz<=r;zz++){
            if(r>0&&Math.abs(xx)!=r&&Math.abs(zz)!=r)continue;
            for(int yy=1;yy>=-2;yy--){BlockPos p=base.add(xx,yy,zz);if(!t.canStand(p))continue;
                double distance=sq(p.getX()+.5-player.posX)+sq(p.getZ()+.5-player.posZ);if(distance<.64)continue;
                double value=sq(p.getX()+.5-ix)+sq(p.getZ()+.5-iz)+sq(t.standingY(p)-player.posY)*2;if(value<score){score=value;best=p;}
            }
            if(best!=null&&r==0)return best;
        }return best;
    }
    private Vec3 placement(EntityPlayer player,double ix,double iz){
        PathTerrain t=new PathTerrain(world,player);BlockPos p=goal(player,ix,iz,t);
        if(p!=null)return new Vec3(p.getX()+.5,t.standingY(p),p.getZ()+.5);
        // Flight/teleports have no nearby ground. Keep the local companion close in clear loaded air.
        AxisAlignedBB b=box(ix,player.posY,iz);return loaded(b)&&collisions(b).isEmpty()?new Vec3(ix,player.posY,iz):null;
    }
    private AxisAlignedBB box(double x,double y,double z){return new AxisAlignedBB(x-.28,y+.00001,z-.28,x+.28,y+height,z+.28);}
    private boolean loaded(AxisAlignedBB box){for(double xx:new double[]{box.minX-1,box.maxX+1})for(double zz:new double[]{box.minZ-1,box.maxZ+1})if(!world.isBlockLoaded(new BlockPos(xx,Math.max(0,Math.min(255,box.minY)),zz)))return false;return true;}
    private List<AxisAlignedBB> collisions(AxisAlignedBB box){
        if(!loaded(box))return Collections.singletonList(box);List<AxisAlignedBB> result=new ArrayList<>();
        for(BlockPos p:BlockPos.getAllInBox(new BlockPos(box.minX,Math.max(0,box.minY-1),box.minZ),new BlockPos(box.maxX,Math.min(255,box.maxY),box.maxZ))){IBlockState state=world.getBlockState(p);state.getBlock().addCollisionBoxesToList(world,p,state,box,result,null);}return result;
    }
    private void move(double dx,double dy,double dz){
        AxisAlignedBB start=box(x,y,z);if(!loaded(start.addCoord(dx,dy,dz))){vx=vy=vz=0;return;}
        List<AxisAlignedBB> blocks=collisions(start.addCoord(dx,dy,dz));double yy=dy,xx=dx,zz=dz;
        for(AxisAlignedBB b:blocks)yy=b.calculateYOffset(start,yy);AxisAlignedBB box=start.offset(0,yy,0);
        for(AxisAlignedBB b:blocks)xx=b.calculateXOffset(box,xx);box=box.offset(xx,0,0);for(AxisAlignedBB b:blocks)zz=b.calculateZOffset(box,zz);
        if((xx!=dx||zz!=dz)&&(onGround||dy<0&&yy!=dy)){
            double up=.6;List<AxisAlignedBB> steps=collisions(start.addCoord(dx,.6,dz));for(AxisAlignedBB b:steps)up=b.calculateYOffset(start,up);
            AxisAlignedBB raised=start.offset(0,up,0);double sx=dx,sz=dz;for(AxisAlignedBB b:steps)sx=b.calculateXOffset(raised,sx);raised=raised.offset(sx,0,0);for(AxisAlignedBB b:steps)sz=b.calculateZOffset(raised,sz);raised=raised.offset(0,0,sz);
            double down=-up;for(AxisAlignedBB b:steps)down=b.calculateYOffset(raised,down);
            if(sx*sx+sz*sz>xx*xx+zz*zz){xx=sx;zz=sz;yy=up+down;}
        }
        x+=xx;y+=yy;z+=zz;onGround=dy<0&&yy!=dy;if(yy!=dy)vy=0;if(xx!=dx)vx=0;if(zz!=dz)vz=0;
    }
    private static double sq(double v){return v*v;}
    private static float angle(float a,float b,float speed){return a+MathHelper.clamp_float(MathHelper.wrapAngleTo180_float(b-a),-speed,speed);}
}

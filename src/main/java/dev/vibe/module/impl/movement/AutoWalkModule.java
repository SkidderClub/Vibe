package dev.vibe.module.impl.movement;

import dev.vibe.Vibe;
import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.module.impl.visual.PathModule;
import dev.vibe.movement.WorldWalkTerrain;
import dev.vibe.path.PathRoute;
import dev.vibe.setting.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.*;
import net.minecraft.world.World;
import org.lwjgl.input.Keyboard;

/** Supplies normal walking input before MoveFix; Path owns the shared bounded route search. */
public final class AutoWalkModule extends Module {
    public final ModeSetting mode=addSetting(new ModeSetting("Mode","Forward","Forward","Path"));
    public final ModeSetting pathMode=addSetting(new ModeSetting("Path Mode","Player",()->mode.is("Path"),"Player","Position"));
    public final BlockCoordinateSetting positionX=coordinate("Position X",0),positionY=coordinate("Position Y",64),positionZ=coordinate("Position Z",0);
    public final BooleanSetting patrol=addSetting(new BooleanSetting("Patrol",false,this::positionMode));
    public final NumberSetting patrolRadius=addSetting(new NumberSetting("Patrol Radius",6,1,128,.5,()->positionMode()&&patrol.isEnabled()));
    private BlockCoordinateSetting coordinate(String name,int initial){return addSetting(new BlockCoordinateSetting(name,initial,this::positionMode));}
    private boolean positionMode(){return mode.is("Path")&&pathMode.is("Position");}
    public BlockPos getPosition(){return new BlockPos(positionX.getInt(),positionY.getInt(),positionZ.getInt());}
    public void setPosition(BlockPos pos){positionX.setInt(pos.getX());positionY.setInt(pos.getY());positionZ.setInt(pos.getZ());resetPosition();}
    private BlockPos positionCenter;
    private Vec3 positionGoal;
    private double patrolAngle,previousRadius;
    private boolean previousPatrol;
    public final BooleanSetting autoJump=addSetting(new BooleanSetting("Auto Jump",true));
    public final BooleanSetting sprint=addSetting(new BooleanSetting("Sprint",false));
    public final BooleanSetting noRotate=addSetting(new BooleanSetting("NoRotate",false,()->mode.is("Path")));
    public final NumberSetting yawSpeed=addSetting(new NumberSetting("Yaw Speed",180,20,720,10,()->mode.is("Path")&&!noRotate.isEnabled()));
    public final BooleanSetting rotatePitch=addSetting(new BooleanSetting("Rotate Pitch",true,()->mode.is("Path")&&!noRotate.isEnabled()));
    public final NumberSetting pitchSpeed=addSetting(new NumberSetting("Pitch Speed",90,10,360,5,()->mode.is("Path")&&!noRotate.isEnabled()&&rotatePitch.isEnabled()));
    public final BooleanSetting showPath=addSetting(new BooleanSetting("Show Path",true,()->mode.is("Path")));
    private boolean driving,jump,rotated,ownedSprint;
    private boolean preparingJump,aligningApproach,navigationJump;
    private int jumpTick;
    private double jumpX,jumpY,jumpZ;
    private float wantedYaw,wantedPitch;
    private World world;
    private EntityPlayerSP player;
    private WorldWalkTerrain terrain;
    private PathRoute route;
    private EntityLivingBase routeTarget;
    private int waypoint;
    private PathModule path;
    private String status="Idle";
    private Object navigationOwner;
    private EntityLivingBase navigationTarget;
    private java.util.function.Predicate<BlockPos> navigationArea=p->true;

    /** A service can temporarily navigate/hold without toggling the user's AutoWalk setting. */
    public void requestNavigation(Object owner,EntityLivingBase destination,java.util.function.Predicate<BlockPos> allowed){
        if(navigationOwner!=owner||navigationTarget!=destination){route=null;routeTarget=null;waypoint=0;driving=false;}
        navigationOwner=owner;navigationTarget=destination;navigationArea=allowed;
        PathModule shared=dev.vibe.features.FeatureModule.module(PathModule.class);
        if(shared!=null)shared.requestNavigation(owner,destination,allowed);
    }
    public void releaseNavigation(Object owner){if(navigationOwner==owner){
        PathModule shared=dev.vibe.features.FeatureModule.module(PathModule.class);if(shared!=null)shared.releaseNavigation(owner);
        navigationOwner=null;navigationTarget=null;navigationArea=p->true;release();
    }}
    public boolean ownsNavigation(Object owner){return navigationOwner==owner;}

    public AutoWalkModule(){super("AutoWalk","Walk forward, follow a player or navigate to coordinates",Category.MOVEMENT,Keyboard.KEY_NONE);}
    @Override protected void onDisable(){release();}
    public void tick(){
        Minecraft mc=Minecraft.getMinecraft(); driving=false;jump=false;rotated=false;preparingJump=aligningApproach=false;
        path=Vibe.getInstance()==null?null:Vibe.getInstance().getModuleManager().getModule(PathModule.class);
        FreecamModule freecam=Vibe.getInstance()==null?null:Vibe.getInstance().getModuleManager().getModule(FreecamModule.class);
        MoveFixModule fix=Vibe.getInstance()==null?null:Vibe.getInstance().getModuleManager().getModule(MoveFixModule.class);
        if(isSuspended()||(!isEnabled()&&navigationOwner==null)||mc==null||mc.thePlayer==null||mc.theWorld==null||mc.thePlayer.isDead||!guiAllowed(mc)
                ||mc.isGamePaused()||mc.thePlayer.isRiding()||freecam!=null&&freecam.isEnabled()||fix!=null&&fix.hasForcedMovement()){
            release();status=isEnabled()?"Paused":"Idle";return;
        }
        if(world!=mc.theWorld||player!=mc.thePlayer){release();world=mc.theWorld;player=mc.thePlayer;terrain=new WorldWalkTerrain(world,player,true);}
        if(player.isInWater()||player.onGround&&player.ticksExisted>jumpTick)navigationJump=false;
        if(path!=null)path.requestWalking(navigationOwner!=null||mode.is("Path"),autoJump.isEnabled(),showPath.isEnabled());
        if(navigationOwner!=null&&navigationTarget==null){stop("Waiting for service");return;}
        if(navigationOwner==null&&mode.is("Forward")){
            resetPosition();route=null;driving=true;status="Walking forward";jump=player.isInWater();
            if(!jump&&autoJump.isEnabled()&&player.onGround&&player.isCollidedHorizontally){
                double angle=Math.toRadians(player.rotationYaw);
                BlockPos feet=new BlockPos(player.posX,player.posY+.05,player.posZ);
                BlockPos ahead=new BlockPos(player.posX-Math.sin(angle)*.8,player.posY+.05,player.posZ+Math.cos(angle)*.8);
                double y=terrain.standingY(ahead.up());
                jump=terrain.canStand(ahead.up())&&terrain.canMove(feet,ahead.up())
                        &&terrain.canJumpFrom(player.getEntityBoundingBox(),ahead.getX()+.5,y,ahead.getZ()+.5);
                preparingJump=jump;
            }
            return;
        }
        if(path==null){stop("Path module is unavailable");return;}
        boolean fixed=navigationOwner==null&&positionMode();
        EntityLivingBase target=fixed?null:navigationOwner==null?path.getTarget():navigationTarget;
        if(fixed){
            updatePositionGoal();path.requestPosition(this,new BlockPos(positionGoal.xCoord,positionGoal.yCoord+.05,positionGoal.zCoord));
        }else{
            resetPosition();
            if(target==null||target.worldObj!=world||target.isDead||!target.isEntityAlive()||world.getEntityByID(target.getEntityId())!=target){stop("Looking for an opponent");return;}
        }
        if(routeTarget!=target){route=null;waypoint=0;routeTarget=target;}
        double destinationX=fixed?positionGoal.xCoord:target.posX,destinationY=fixed?positionGoal.yCoord:target.posY,destinationZ=fixed?positionGoal.zCoord:target.posZ;
        BlockPos feet=new BlockPos(player.posX,player.posY+.05,player.posZ);
        BlockPos targetFeet=new BlockPos(destinationX,destinationY+.05,destinationZ);
        if(fixed){for(int down=0;down<=3;down++)if(terrain.canStand(targetFeet.down(down))){targetFeet=targetFeet.down(down);destinationY=terrain.standingY(targetFeet);break;}}
        // Follow the actual player within the final cells, including a one-node
        // route. A block-centre endpoint must not become a stopping distance.
        double targetX=destinationX-player.posX,targetZ=destinationZ-player.posZ;
        double targetY=terrain.standingY(targetFeet);
        if(fixed&&!patrol.isEnabled()&&targetX*targetX+targetZ*targetZ<=.1225&&Math.abs(destinationY-player.posY)<.65){stop("At position");return;}
        boolean raisedTarget=!player.isInWater()&&targetY-player.getEntityBoundingBox().minY>player.stepHeight+.05;
        if(navigationOwner==null&&targetX*targetX+targetZ*targetZ<=4&&terrain.canStand(targetFeet)&&terrain.canMove(feet,targetFeet)
                &&(!fixed||safePositionApproach(destinationX,destinationZ,targetY))
                &&(!raisedTarget||terrain.canJumpFrom(player.getEntityBoundingBox(),destinationX,targetY,destinationZ))){
            steer(destinationX,destinationZ,targetY,target);return;
        }
        PathRoute nextRoute=path.getRoute();
        if(nextRoute.nodes.isEmpty() && route==null){followTarget(target);return;}
        if(!nextRoute.nodes.isEmpty() && route!=nextRoute){
            route=nextRoute;waypoint=0;double best=Double.MAX_VALUE;
            for(int i=0;i<route.nodes.size();i++){
                BlockPos node=route.nodes.get(i);double dx=node.getX()+.5-player.posX,dz=node.getZ()+.5-player.posZ;
                double dy=node.getY()-player.posY,distance=dx*dx+dz*dz+dy*dy*2;
                if(distance<best){best=distance;waypoint=i;}
            }
        }
        while(waypoint<route.nodes.size()){
            BlockPos node=route.nodes.get(waypoint);double dx=node.getX()+.5-player.posX,dz=node.getZ()+.5-player.posZ;
            // Look ahead through a bend instead of requiring the player to
            // touch each block centre. A small departure from the line is fine.
            boolean reached=dx*dx+dz*dz<=1;
            if(navigationOwner!=null&&waypoint==route.nodes.size()-1)reached=player.getDistanceSqToEntity(target)<=6.25;
            if(!reached&&waypoint+1<route.nodes.size()){
                BlockPos after=route.nodes.get(waypoint+1);double ex=after.getX()-node.getX(),ez=after.getZ()-node.getZ(),length=ex*ex+ez*ez;
                double along=-dx*ex-dz*ez,cross=dx*ez-dz*ex;
                // A refreshed route starts at the current block centre, which can already
                // be behind the player. Continue along that segment instead of turning back.
                reached=length>0&&along>0&&along<=length*1.5&&cross*cross<=length;
            }
            boolean level=Math.abs(node.getY()-player.posY)<1 || player.isInWater()&&node.getY()<player.posY+1;
            double standing=terrain.standingY(node);
            if(!player.isInWater()&&waypoint>0&&standing>terrain.standingY(route.nodes.get(waypoint-1))+.65){
                // Do not turn away from a landing while still below its top or
                // merely within one block of its centre during take-off.
                reached=dx*dx+dz*dz<=.16;
                level=player.getEntityBoundingBox().minY>=standing-.05;
            }
            if(reached&&level&&!player.isInWater()&&autoJump.isEnabled()&&waypoint+1<route.nodes.size()){
                BlockPos after=route.nodes.get(waypoint+1);double landing=terrain.standingY(after);
                if(landing-player.getEntityBoundingBox().minY>player.stepHeight+.05
                        &&!terrain.canJumpFrom(player.getEntityBoundingBox(),after.getX()+.5,landing,after.getZ()+.5))reached=false;
            }
            if(reached&&navigationOwner!=null&&waypoint+1<route.nodes.size()){
                BlockPos after=route.nodes.get(waypoint+1);
                AxisAlignedBB body=player.getEntityBoundingBox();
                AxisAlignedBB end=body.offset(after.getX()+.5-player.posX,0,after.getZ()+.5-player.posZ);
                // Retain the corner's approach until skipping it would stay inside the service area.
                AxisAlignedBB sweep=body.union(end);
                for(BlockPos cell:BlockPos.getAllInBox(new BlockPos(sweep.minX,sweep.minY+.05,sweep.minZ),new BlockPos(sweep.maxX,sweep.minY+.05,sweep.maxZ)))
                    if(!navigationArea.test(cell))reached=false;
            }
            if(!reached||!level)break;waypoint++;
        }
        if(waypoint>=route.nodes.size()){path.refreshWalkingRoute();followTarget(target);return;}
        BlockPos next=route.nodes.get(waypoint);
        double segmentX=next.getX()+.5-player.posX,segmentZ=next.getZ()+.5-player.posZ;
        if(segmentX*segmentX+segmentZ*segmentZ>4)path.refreshWalkingRoute();
        // The planner checks the route's collision geometry. Rechecking a full
        // block-centre sweep from an off-line player caused the corner stops.
        // Real player collision handles the curve while a fresh route catches up.
        if(!terrain.canStand(next)){path.refreshWalkingRoute();followTarget(target);return;}
        double landing=terrain.standingY(next);
        if(!player.isInWater()&&autoJump.isEnabled()&&landing-player.getEntityBoundingBox().minY>player.stepHeight+.05
                &&!terrain.canJumpFrom(player.getEntityBoundingBox(),next.getX()+.5,landing,next.getZ()+.5)){
            BlockPos approach=waypoint>0?route.nodes.get(waypoint-1):feet;
            double approachY=terrain.standingY(approach);
            if(!Double.isNaN(approachY)&&Math.abs(approachY-player.getEntityBoundingBox().minY)<=.65){
                steer(approach.getX()+.5,approach.getZ()+.5,approachY,target);
                preparingJump=aligningApproach=true;jump=false;return;
            }
            path.refreshWalkingRoute();
        }
        steer(next.getX()+.5,next.getZ()+.5,landing,target);
    }
    private void followTarget(EntityLivingBase target){
        if(navigationOwner!=null){stop("Waiting for a safe service route");return;}
        if(target==null){stop("Waiting for a safe position route");return;}
        BlockPos feet=new BlockPos(target.posX,target.posY+.05,target.posZ);
        double y=terrain.standingY(feet);
        steer(target.posX,target.posZ,Double.isNaN(y)?player.posY:y,target);
    }
    private void steer(double x,double z,double nextY,EntityLivingBase target){
        double rise=nextY-player.getEntityBoundingBox().minY;
        double dx=x-player.posX,dz=z-player.posZ;
        wantedYaw=dx*dx+dz*dz<.0001?player.rotationYaw:(float)(Math.toDegrees(Math.atan2(dz,dx))-90);
        wantedPitch=(float)Math.max(-35,Math.min(35,-Math.toDegrees(Math.atan2(nextY-player.posY,Math.hypot(dx,dz)))));
        jumpX=x;jumpY=nextY;jumpZ=z;
        preparingJump=!player.isInWater()&&autoJump.isEnabled()&&rise>player.stepHeight+.05;
        jump=player.isInWater() || preparingJump&&player.onGround&&dx*dx+dz*dz<=1.44
                &&terrain.canJumpFrom(player.getEntityBoundingBox(),x,nextY,z);
        driving=true;status=target!=null?"Following "+target.getName():patrol.isEnabled()?"Patrolling position":"Walking to position";
    }
    /** The input wrapper calls this before silent movement correction and normal player physics. */
    public boolean apply(MovementInput input){
        if(isSuspended()||(!isEnabled()&&navigationOwner==null)||player==null)return false;
        Minecraft mc=Minecraft.getMinecraft();
        if(mc.thePlayer!=player||!guiAllowed(mc)){releaseSprint();return false;}
        // Surfacing is independent of a completed route or a selected target.
        if(player.isInWater())input.jump=true;
        if(!driving){if(navigationOwner!=null)input.moveForward=input.moveStrafe=0;return false;}
        float scale=input.sneak?.3F:1F;
        boolean fixedCamera = noRotate.isEnabled() && (navigationOwner != null || mode.is("Path"));
        if(navigationOwner!=null||mode.is("Path")){
            if(!fixedCamera&&!rotated){
                player.rotationYaw=turn(player.rotationYaw,wantedYaw,yawSpeed.getFloat()/20);
                if(rotatePitch.isEnabled())player.rotationPitch=turn(player.rotationPitch,wantedPitch,pitchSpeed.getFloat()/20);
                rotated=true;
            }
            // Keep W held through the turn, including while rejoining a route.
            // Sideways input helps the curve without releasing forward/sprint.
            float angle=MathHelper.wrapAngleTo180_float(wantedYaw-player.rotationYaw);
            input.moveForward=scale;
            input.moveStrafe=Math.abs(angle)>25?-Math.signum(angle)*scale:0;
            if(fixedCamera||navigationOwner!=null){
                input.moveForward=(float)Math.cos(Math.toRadians(angle))*scale;
                input.moveStrafe=-(float)Math.sin(Math.toRadians(angle))*scale;
            }
            if(navigationOwner!=null){
                double yaw=Math.toRadians(player.rotationYaw);
                double dx=(-Math.sin(yaw)*input.moveForward+Math.cos(yaw)*input.moveStrafe)*.45;
                double dz=(Math.cos(yaw)*input.moveForward+Math.sin(yaw)*input.moveStrafe)*.45;
                AxisAlignedBB ahead=player.getEntityBoundingBox().offset(dx,0,dz);
                for(double x:new double[]{ahead.minX,ahead.maxX})for(double z:new double[]{ahead.minZ,ahead.maxZ})
                    if(!navigationArea.test(new BlockPos(x,ahead.minY+.05,z))){input.moveForward=input.moveStrafe=0;path.refreshWalkingRoute();return fixedCamera;}
            }
            boolean launch=jump;
            if(preparingJump&&!aligningApproach&&player.onGround){
                // Face the opening before launching. Continuing around a bend
                // while already jumping clips the walls beside narrow steps.
                if(!fixedCamera&&Math.abs(angle)>12){input.moveForward=input.moveStrafe=0;launch=false;}
                else launch&=terrain.canJumpFrom(player.getEntityBoundingBox(),jumpX,jumpY,jumpZ);
            }
            input.jump|=launch;
            if(launch&&player.onGround&&!player.isInWater()){navigationJump=true;jumpTick=player.ticksExisted;}
        }else{
            if(!mc.gameSettings.keyBindForward.isKeyDown())input.moveForward=Math.min(scale,input.moveForward+scale);
            input.jump|=jump;
            if(jump&&player.onGround&&!player.isInWater()){navigationJump=true;jumpTick=player.ticksExisted;}
        }
        boolean canSprint=sprint.isEnabled()&&input.moveForward>.8F&&!input.sneak&&!player.isUsingItem()&&!player.isInWater()
                &&!player.isCollidedHorizontally&&player.getFoodStats().getFoodLevel()>6;
        if(canSprint){if(!player.isSprinting()){player.setSprinting(true);ownedSprint=true;}}
        else releaseSprint();
        return fixedCamera;
    }
    public static boolean inputHook(MovementInput input){
        Vibe vibe=Vibe.getInstance();
        AutoWalkModule module=vibe==null||vibe.getModuleManager()==null?null:vibe.getModuleManager().getModule(AutoWalkModule.class);
        return module!=null && module.apply(input);
    }
    /** Terrain jumps own their native input/trajectory until landing. */
    public static boolean reservesJump(){
        Vibe vibe=Vibe.getInstance();AutoWalkModule module=vibe==null||vibe.getModuleManager()==null?null:vibe.getModuleManager().getModule(AutoWalkModule.class);
        Minecraft mc=Minecraft.getMinecraft();
        return module!=null&&(module.navigationOwner!=null||module.isEnabled()&&module.driving&&(module.preparingJump||module.navigationJump))
                &&mc!=null&&mc.thePlayer==module.player&&mc.theWorld==module.world&&guiAllowed(mc);
    }
    public static float turn(float current,float target,float limit){return current+Math.max(-limit,Math.min(limit,MathHelper.wrapAngleTo180_float(target-current)));}
    private void stop(String reason){driving=false;jump=false;releaseSprint();status=reason;}
    private void releaseSprint(){
        Minecraft mc=Minecraft.getMinecraft();
        if(ownedSprint&&player!=null&&(mc==null||mc.gameSettings==null||!mc.gameSettings.keyBindSprint.isKeyDown()))player.setSprinting(false);
        ownedSprint=false;
    }
    private static boolean guiAllowed(Minecraft mc){return mc.currentScreen==null||GuiMoveModule.allowsAutoWalk();}
    private boolean safePositionApproach(double x,double z,double y){
        int samples=Math.max(1,(int)Math.ceil(Math.hypot(x-player.posX,z-player.posZ)/.2));
        for(int i=1;i<=samples;i++){double t=i/(double)samples;BlockPos p=new BlockPos(player.posX+(x-player.posX)*t,player.posY+(y-player.posY)*t+.05,player.posZ+(z-player.posZ)*t);if(!terrain.canStand(p))return false;}
        return true;
    }
    private void resetPosition(){
        if(positionCenter!=null){if(path!=null)path.releasePosition(this);positionCenter=null;positionGoal=null;route=null;waypoint=0;}
    }
    private void updatePositionGoal(){
        BlockPos center=getPosition();double radius=patrolRadius.getDouble();boolean circling=patrol.isEnabled();
        if(!center.equals(positionCenter)||circling!=previousPatrol||radius!=previousRadius){
            resetPosition();positionCenter=center;previousPatrol=circling;previousRadius=radius;
            patrolAngle=Math.atan2(player.posZ-(center.getZ()+.5),player.posX-(center.getX()+.5));
            positionGoal=circling?patrolPoint(center,radius,patrolAngle):new Vec3(center.getX()+.5,center.getY(),center.getZ()+.5);
        }
        if(circling){double dx=positionGoal.xCoord-player.posX,dz=positionGoal.zCoord-player.posZ;
            double tolerance=Math.min(.65,radius*.25);
            if(dx*dx+dz*dz<=tolerance*tolerance&&Math.abs(positionGoal.yCoord-player.posY)<1){
                patrolAngle+=Math.min(Math.PI/4,1.5/radius);
                positionGoal=patrolPoint(center,radius,patrolAngle);
            }
        }
    }
    private Vec3 patrolPoint(BlockPos center,double radius,double angle){
        double x=center.getX()+.5+Math.cos(angle)*radius,z=center.getZ()+.5+Math.sin(angle)*radius;
        BlockPos feet=new BlockPos(x,center.getY()+.05,z);double y=Double.NaN;
        for(int down=0;down<=3;down++)if(terrain.canStand(feet.down(down))){y=terrain.standingY(feet.down(down));break;}
        return new Vec3(x,Double.isNaN(y)?center.getY():y,z);
    }
    private void release(){
        resetPosition();
        if(path!=null)path.requestWalking(false,true,false);
        releaseSprint();driving=jump=rotated=preparingJump=aligningApproach=navigationJump=false;world=null;player=null;terrain=null;route=null;routeTarget=null;waypoint=0;
    }
    public boolean isDriving(){return driving;}
    public String getStatus(){return status;}
}

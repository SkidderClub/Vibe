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
    public final BooleanSetting autoJump=addSetting(new BooleanSetting("Auto Jump",true));
    public final BooleanSetting sprint=addSetting(new BooleanSetting("Sprint",false));
    public final NumberSetting yawSpeed=addSetting(new NumberSetting("Yaw Speed",180,20,720,10,()->mode.is("Path")));
    public final BooleanSetting rotatePitch=addSetting(new BooleanSetting("Rotate Pitch",true,()->mode.is("Path")));
    public final NumberSetting pitchSpeed=addSetting(new NumberSetting("Pitch Speed",90,10,360,5,()->mode.is("Path")&&rotatePitch.isEnabled()));
    public final BooleanSetting showPath=addSetting(new BooleanSetting("Show Path",true,()->mode.is("Path")));
    private boolean driving,jump,rotated,ownedSprint;
    private float wantedYaw,wantedPitch;
    private World world;
    private EntityPlayerSP player;
    private WorldWalkTerrain terrain;
    private PathRoute route;
    private int waypoint;
    private PathModule path;
    private String status="Idle";

    public AutoWalkModule(){super("AutoWalk","Walk forward or follow Path to a filtered opponent",Category.MOVEMENT,Keyboard.KEY_NONE);}
    @Override protected void onDisable(){release();}
    public void tick(){
        Minecraft mc=Minecraft.getMinecraft(); driving=false;jump=false;rotated=false;
        path=Vibe.getInstance()==null?null:Vibe.getInstance().getModuleManager().getModule(PathModule.class);
        FreecamModule freecam=Vibe.getInstance()==null?null:Vibe.getInstance().getModuleManager().getModule(FreecamModule.class);
        MoveFixModule fix=Vibe.getInstance()==null?null:Vibe.getInstance().getModuleManager().getModule(MoveFixModule.class);
        if(!isEnabled()||mc==null||mc.thePlayer==null||mc.theWorld==null||mc.thePlayer.isDead||mc.currentScreen!=null
                ||mc.isGamePaused()||mc.thePlayer.isRiding()||freecam!=null&&freecam.isEnabled()||fix!=null&&fix.hasForcedMovement()){
            release();status=isEnabled()?"Paused":"Idle";return;
        }
        if(world!=mc.theWorld||player!=mc.thePlayer){release();world=mc.theWorld;player=mc.thePlayer;terrain=new WorldWalkTerrain(world,player);}
        if(path!=null)path.requestWalking(mode.is("Path"),autoJump.isEnabled(),showPath.isEnabled());
        if(mode.is("Forward")){
            route=null;driving=true;status="Walking forward";
            if(autoJump.isEnabled()&&player.onGround&&player.isCollidedHorizontally){
                double angle=Math.toRadians(player.rotationYaw);
                BlockPos feet=new BlockPos(player.posX,player.posY+.05,player.posZ);
                BlockPos ahead=new BlockPos(player.posX-Math.sin(angle)*.8,player.posY+.05,player.posZ+Math.cos(angle)*.8);
                jump=terrain.canStand(ahead.up())&&terrain.canMove(feet,ahead.up());
            }
            return;
        }
        if(path==null){stop("Path module is unavailable");return;}
        EntityLivingBase target=path.getTarget();
        if(target==null||target.worldObj!=world||target.isDead||!target.isEntityAlive()||world.getEntityByID(target.getEntityId())!=target){stop("Looking for an opponent");return;}
        BlockPos feet=new BlockPos(player.posX,player.posY+.05,player.posZ);
        BlockPos targetFeet=new BlockPos(target.posX,target.posY+.05,target.posZ);
        // Follow the actual player within the final cells, including a one-node
        // route. A block-centre endpoint must not become a stopping distance.
        double targetX=target.posX-player.posX,targetZ=target.posZ-player.posZ;
        if(targetX*targetX+targetZ*targetZ<=4&&terrain.canStand(targetFeet)&&terrain.canMove(feet,targetFeet)){
            steer(target.posX,target.posZ,terrain.standingY(targetFeet),target);return;
        }
        PathRoute nextRoute=path.getRoute();
        if(nextRoute.nodes.size()<2){stop("Waiting for a path");return;}
        if(route!=nextRoute){
            route=nextRoute;waypoint=0;double best=Double.MAX_VALUE;
            for(int i=0;i<route.nodes.size();i++){
                BlockPos node=route.nodes.get(i);double dx=node.getX()+.5-player.posX,dz=node.getZ()+.5-player.posZ;
                double dy=node.getY()-player.posY,distance=dx*dx+dz*dz+dy*dy*2;
                if(distance<best){best=distance;waypoint=i;}
            }
            if(best>4){stop("Waiting for a fresh path");return;}
        }
        while(waypoint<route.nodes.size()){
            BlockPos node=route.nodes.get(waypoint);double dx=node.getX()+.5-player.posX,dz=node.getZ()+.5-player.posZ;
            boolean reached=dx*dx+dz*dz<=.04;
            if(!reached&&waypoint+1<route.nodes.size()){
                BlockPos after=route.nodes.get(waypoint+1);double ex=after.getX()-node.getX(),ez=after.getZ()-node.getZ(),length=ex*ex+ez*ez;
                double along=-dx*ex-dz*ez,cross=dx*ez-dz*ex;
                // A refreshed route starts at the current block centre, which can already
                // be behind the player. Continue along that segment instead of turning back.
                reached=length>0&&along>0&&along<=length*1.25&&cross*cross<=.04*length;
            }
            if(!reached||Math.abs(node.getY()-player.posY)>.6)break;waypoint++;
        }
        if(waypoint>=route.nodes.size()){stop(route.complete?"Waiting for a fresh path":"Waiting for the rest of the path");return;}
        BlockPos next=route.nodes.get(waypoint);
        double segmentX=next.getX()+.5-player.posX,segmentZ=next.getZ()+.5-player.posZ;
        if(segmentX*segmentX+segmentZ*segmentZ>4){route=null;stop("Waiting for a fresh path");return;}
        if(!terrain.canStand(next)||!terrain.canMove(feet,next)){stop("Path is blocked; replanning");return;}
        steer(next.getX()+.5,next.getZ()+.5,terrain.standingY(next),target);
    }
    private void steer(double x,double z,double nextY,EntityLivingBase target){
        double rise=nextY-player.getEntityBoundingBox().minY;
        if(rise>player.stepHeight+.05&&!autoJump.isEnabled()){stop("A jump is required");return;}
        double dx=x-player.posX,dz=z-player.posZ;
        wantedYaw=dx*dx+dz*dz<.0001?player.rotationYaw:(float)(Math.toDegrees(Math.atan2(dz,dx))-90);
        wantedPitch=(float)Math.max(-35,Math.min(35,-Math.toDegrees(Math.atan2(nextY-player.posY,Math.hypot(dx,dz)))));
        jump=autoJump.isEnabled()&&player.onGround&&rise>player.stepHeight+.05&&dx*dx+dz*dz<2.25;
        driving=true;status="Following "+target.getName();
    }
    /** The input wrapper calls this before silent movement correction and normal player physics. */
    public void apply(MovementInput input){
        if(!driving||!isEnabled()||player==null)return;
        Minecraft mc=Minecraft.getMinecraft();
        if(mc.thePlayer!=player||mc.currentScreen!=null)return;
        float scale=input.sneak?.3F:1F;
        if(mode.is("Path")){
            if(!rotated){
                player.rotationYaw=turn(player.rotationYaw,wantedYaw,yawSpeed.getFloat()/20);
                if(rotatePitch.isEnabled())player.rotationPitch=turn(player.rotationPitch,wantedPitch,pitchSpeed.getFloat()/20);
                rotated=true;
            }
            // Select normal WASD directions relative to the smoothly turning
            // view. Movement follows the route throughout the turn.
            double angle=Math.toRadians(MathHelper.wrapAngleTo180_float(wantedYaw-player.rotationYaw));
            input.moveForward=(float)Math.round(Math.cos(angle))*scale;
            input.moveStrafe=-(float)Math.round(Math.sin(angle))*scale;
            input.jump|=jump;
        }else{
            if(!mc.gameSettings.keyBindForward.isKeyDown())input.moveForward=Math.min(scale,input.moveForward+scale);
            input.jump|=jump;
        }
        boolean canSprint=sprint.isEnabled()&&input.moveForward>.8F&&!input.sneak&&!player.isUsingItem()
                &&!player.isCollidedHorizontally&&player.getFoodStats().getFoodLevel()>6;
        if(canSprint){if(!player.isSprinting()){player.setSprinting(true);ownedSprint=true;}}
        else releaseSprint();
    }
    public static void inputHook(MovementInput input){
        Vibe vibe=Vibe.getInstance();
        AutoWalkModule module=vibe==null||vibe.getModuleManager()==null?null:vibe.getModuleManager().getModule(AutoWalkModule.class);
        if(module!=null)module.apply(input);
    }
    public static float turn(float current,float target,float limit){return current+Math.max(-limit,Math.min(limit,MathHelper.wrapAngleTo180_float(target-current)));}
    private void stop(String reason){driving=false;jump=false;releaseSprint();status=reason;}
    private void releaseSprint(){
        Minecraft mc=Minecraft.getMinecraft();
        if(ownedSprint&&player!=null&&(mc==null||mc.gameSettings==null||!mc.gameSettings.keyBindSprint.isKeyDown()))player.setSprinting(false);
        ownedSprint=false;
    }
    private void release(){
        if(path!=null)path.requestWalking(false,true,false);
        releaseSprint();driving=jump=rotated=false;world=null;player=null;terrain=null;route=null;waypoint=0;
    }
    public boolean isDriving(){return driving;}
    public String getStatus(){return status;}
}

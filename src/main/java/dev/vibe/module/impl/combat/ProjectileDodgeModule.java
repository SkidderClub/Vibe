/*
 * AutoDodge adapted from LiquidBounce, Copyright (c) 2015-2026 CCBlueX.
 * GPL-3.0-or-later. See LICENSES/LIQUIDBOUNCE-AUTODODGE.md.
 */
package dev.vibe.module.impl.combat;

import dev.vibe.features.*;
import dev.vibe.features.dodge.*;
import dev.vibe.module.Category;
import dev.vibe.module.impl.movement.MoveFixModule;
import dev.vibe.setting.*;
import java.util.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.*;
import net.minecraft.util.*;

public final class ProjectileDodgeModule extends FeatureModule {
    public final BooleanSetting assist=addSetting(new BooleanSetting("Movement Assistance",true));
    public final BooleanSetting respectMovement=addSetting(new BooleanSetting("Respect Movement Input",false));
    public final BooleanSetting allowRotation=addSetting(new BooleanSetting("Allow Rotation Change",false));
    public final BooleanSetting allowJump=addSetting(new BooleanSetting("Allow Jump",true,allowRotation::isEnabled));
    public final NumberSetting ticks=addSetting(new NumberSetting("Prediction Ticks",80,5,100,1));
    public final NumberSetting range=addSetting(new NumberSetting("Range",32,6,96,1));
    public final BooleanSetting preview=addSetting(new BooleanSetting("Preview",true));
    private final WorldStyle style=new WorldStyle(this::addSetting);
    private MovementForecast.Path escape;
    private Object planWorld;
    private boolean dodging;

    public ProjectileDodgeModule(){super("Projectile Dodge","Evade incoming projectiles using trajectory danger zones and supported escape paths",Category.COMBAT);}

    @Override public void tick(){if(!controlling())reset();}

    @Override public void input(MovementInput input){
        dodging=false;escape=null;
        MoveFixModule fix=module(MoveFixModule.class);
        if(fix!=null)fix.clearForcedMovement(getId());
        if(!controlling()||!MovementForecast.loaded(mc.thePlayer.getEntityBoundingBox().expand(1,1,1))||mc.thePlayer.isOnLadder()||mc.thePlayer.isInWater()||mc.thePlayer.isInLava()||mc.thePlayer.isUsingItem()||input.sneak
                ||fix!=null&&fix.hasForcedMovement()||dev.vibe.module.impl.world.ScaffoldModule.isActive()){
            releaseRotation();return;
        }
        if(respectMovement.isEnabled()&&(input.moveForward!=0||input.moveStrafe!=0)){releaseRotation();return;}

        List<Entity> threats=new ArrayList<>();
        for(Entity entity:mc.theWorld.loadedEntityList){
            if((entity instanceof EntityArrow||entity instanceof EntitySnowball||entity instanceof EntityEgg||entity instanceof EntityPotion)
                    &&DodgeTrajectory.flying(entity)&&ProjectileForecast.owner(entity)!=mc.thePlayer
                    &&mc.thePlayer.getDistanceSqToEntity(entity)<=range.getDouble()*range.getDouble())threats.add(entity);
        }
        if(threats.isEmpty()){releaseRotation();return;}
        threats.sort(Comparator.comparingDouble(e->mc.thePlayer.getDistanceSqToEntity(e)));
        List<DodgeTrajectory> trajectories=new ArrayList<>();
        for(int i=0;i<Math.min(32,threats.size());i++)trajectories.add(DodgeTrajectory.trace(mc.theWorld,threats.get(i),ticks.getInt()));

        MovementInput predicted=corrected(input,fix);
        float movementYaw=MoveFixModule.movementYawHook(mc.thePlayer,mc.thePlayer.rotationYaw);
        MovementForecast.Path baseline=MovementForecast.simulate(predicted,ticks.getInt(),movementYaw);
        Hit hit=firstHit(baseline,trajectories,DodgePlanner.SAFE_DISTANCE_WITH_PADDING);
        if(hit==null){releaseRotation();return;}

        float inputYaw=fix!=null&&fix.getEffectiveCorrection().equalsIgnoreCase("Silent")?mc.thePlayer.rotationYaw:movementYaw;
        Vec3 player=mc.thePlayer.getPositionVector(),velocity=new Vec3(mc.thePlayer.motionX,mc.thePlayer.motionY,mc.thePlayer.motionZ);
        List<DodgePlanner.Plan> plans=DodgePlanner.plans(player,velocity,inputYaw,hit.previous,hit.velocity,hit.tick,this::walkableDistance);
        for(DodgePlanner.Plan plan:plans){
            // Escalation uses the same owner/forced-movement path as Vibe's other producers.
            boolean rotate=allowRotation.isEnabled()&&plan.escalate&&fix!=null&&!fix.hasOtherRotationOwner(getId());
            boolean jump=rotate&&allowJump.isEnabled()&&plan.jump&&mc.thePlayer.onGround;
            MovementInput candidate=MovementForecast.copy(input);candidate.moveForward=rotate?1:plan.forward;candidate.moveStrafe=rotate?0:plan.strafe;candidate.jump|=jump;
            MovementInput simulation=rotate?MovementForecast.copy(candidate):corrected(candidate,fix);
            float yaw=rotate?plan.yaw:movementYaw;
            DodgePlanner.Line line=new DodgePlanner.Line(hit.previous,hit.velocity);
            Vec3 nearestStart=line.nearest(player);
            double side=(player.xCoord-nearestStart.xCoord)*plan.relative.xCoord+(player.zCoord-nearestStart.zCoord)*plan.relative.zCoord;
            final boolean[] stopped={false};
            MovementForecast.Path path=MovementForecast.simulate(simulation,ticks.getInt(),yaw,(sim,tick)->{
                Vec3 position=sim.getPos(),nearest=line.nearest(position);
                double away=(position.xCoord-nearest.xCoord)*plan.relative.xCoord+(position.zCoord-nearest.zCoord)*plan.relative.zCoord;
                if(tick>0&&line.distance(position)>=DodgePlanner.SAFE_DISTANCE_WITH_PADDING&&away>side)stopped[0]=true;
                if(stopped[0]){sim.movementInput.moveForward=sim.movementInput.moveStrafe=0;sim.movementInput.jump=false;}
            });
            if(!path.loaded||!path.safe||path.points.size()<2)continue;
            Vec3 end=path.points.get(path.points.size()-1).subtract(player);
            if(end.xCoord*plan.relative.xCoord+end.zCoord*plan.relative.zCoord<.05)continue;
            escape=path;planWorld=mc.theWorld;
            if(!assist.isEnabled()){releaseRotation();return;}
            if(rotate){fix.setFakeRotation(getId(),plan.yaw,mc.thePlayer.rotationPitch,"Silent");fix.setForcedMovement(getId(),1,0,candidate.jump);}
            else releaseRotation();
            input.moveForward=candidate.moveForward;input.moveStrafe=candidate.moveStrafe;input.jump=candidate.jump;dodging=true;return;
        }
        releaseRotation();
    }

    private MovementInput corrected(MovementInput input,MoveFixModule fix){MovementInput copy=MovementForecast.copy(input);if(fix!=null)fix.correctInjectedInput(copy);return copy;}

    /** Two rays at the original planner's feet/chest heights, against actual collision shapes. */
    private double walkableDistance(Vec3 from,Vec3 to){
        double distance=Math.hypot(to.xCoord-from.xCoord,to.zCoord-from.zCoord);
        for(double height:new double[]{.6,1.6}){
            Vec3 start=new Vec3(from.xCoord,mc.thePlayer.posY+height,from.zCoord),end=new Vec3(to.xCoord,mc.thePlayer.posY+height,to.zCoord);
            if(!MovementForecast.loaded(new AxisAlignedBB(start.xCoord,start.yCoord,start.zCoord,end.xCoord,end.yCoord,end.zCoord)))return 0;
            MovingObjectPosition hit=mc.theWorld.rayTraceBlocks(start,end,false,true,false);if(hit!=null)distance=Math.min(distance,start.distanceTo(hit.hitVec));
        }return distance;
    }

    private static Hit firstHit(MovementForecast.Path player,List<DodgeTrajectory> projectiles,double expansion){
        for(int tick=1;tick<player.bodies.size();tick++)for(DodgeTrajectory projectile:projectiles){
            if(tick>=projectile.positions.size())continue;Vec3 previous=projectile.positions.get(tick-1),position=projectile.positions.get(tick);AxisAlignedBB box=player.bodies.get(tick).expand(expansion,expansion,expansion);
            if(box.isVecInside(previous)||box.calculateIntercept(previous,position)!=null)return new Hit(tick-1,previous,projectile.velocities.get(tick));
        }return null;
    }
    private static final class Hit {final int tick;final Vec3 previous,velocity;Hit(int tick,Vec3 previous,Vec3 velocity){this.tick=tick;this.previous=previous;this.velocity=velocity;}}
    private void releaseRotation(){MoveFixModule fix=module(MoveFixModule.class);if(fix!=null){fix.clearForcedMovement(getId());fix.clearFakeRotation(getId());}}
    private void reset(){escape=null;dodging=false;planWorld=null;releaseRotation();}
    @Override public void render(float partial){if(preview.isEnabled()&&escape!=null&&planWorld==mc.theWorld)WorldDraw.lines(escape.points,style.color(0,1,0),style.width.getFloat(),style.glow.isEnabled());}
    public boolean dodging(){return dodging;}
    public MovementForecast.Path escapePath(){return escape;}
    @Override protected void onDisable(){reset();}
}

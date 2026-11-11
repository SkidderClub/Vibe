/*
 * Adapted from LiquidBounce's DodgePlanner.kt, Copyright (c) 2015-2026 CCBlueX.
 * GPL-3.0-or-later. See LICENSES/LIQUIDBOUNCE-AUTODODGE.md and GPL-3.0.txt.
 */
package dev.vibe.features.dodge;

import java.util.*;
import net.minecraft.util.*;

/** LiquidBounce's perpendicular danger-zone planner, independent of rendering and live input. */
public final class DodgePlanner {
    public static final double SAFE_DISTANCE=1.5*.3+1.5*.5;
    public static final double SAFE_DISTANCE_WITH_PADDING=.3*5;
    public interface Walkable {double distance(Vec3 from,Vec3 to);}
    public static final class Line {
        public final Vec3 origin,direction;
        public Line(Vec3 origin,Vec3 velocity){this.origin=new Vec3(origin.xCoord,0,origin.zCoord);double length=Math.hypot(velocity.xCoord,velocity.zCoord);direction=length<1E-8?new Vec3(0,0,0):new Vec3(velocity.xCoord/length,0,velocity.zCoord/length);}
        public Vec3 nearest(Vec3 point){double projection=(point.xCoord-origin.xCoord)*direction.xCoord+(point.zCoord-origin.zCoord)*direction.zCoord;return origin.addVector(direction.xCoord*projection,0,direction.zCoord*projection);}
        public double distance(Vec3 point){Vec3 n=nearest(point);return Math.hypot(point.xCoord-n.xCoord,point.zCoord-n.zCoord);}
    }
    public static final class Plan {
        public final Vec3 target,relative;public final float forward,strafe;public final boolean escalate,jump;public final float yaw;
        private Plan(Vec3 target,Vec3 player,float inputYaw,Vec3 velocity,int impactTicks,double lineDistance){this.target=target;relative=target.subtract(new Vec3(player.xCoord,0,player.zCoord));float[] keys=keys(relative,inputYaw);forward=keys[0];strafe=keys[1];
            Vec3 actual=worldDirection(forward,strafe,inputYaw);double similarity=dot(actual,relative)/Math.max(1E-8,length(actual)*length(relative));double distance=length(relative)-(SAFE_DISTANCE_WITH_PADDING-SAFE_DISTANCE);
            double travel=distance/Math.max(1E-8,similarity*.11);escalate=lineDistance<=SAFE_DISTANCE&&impactTicks+1<=travel;
            double effective=dot(velocity,relative)/Math.max(1E-8,length(relative));jump=escalate&&impactTicks+1<distance/.13&&effective>.11;
            yaw=(float)Math.toDegrees(Math.atan2(-relative.xCoord,relative.zCoord));
        }
    }
    private DodgePlanner(){}
    /** Preferred border includes two ticks of current momentum. The other border remains a safety fallback. */
    public static List<Plan> plans(Vec3 player,Vec3 velocity,float inputYaw,Vec3 arrowStart,Vec3 arrowVelocity,int impactTicks,Walkable walkable){
        if(Math.hypot(arrowVelocity.xCoord,arrowVelocity.zCoord)<1E-8)return Collections.emptyList();Line line=new Line(arrowStart,arrowVelocity);double distance=line.distance(player);if(distance>SAFE_DISTANCE_WITH_PADDING)return Collections.emptyList();
        Vec3 after=player.addVector(velocity.xCoord*2,0,velocity.zCoord*2),nearest=line.nearest(after),normal=new Vec3(-line.direction.zCoord,0,line.direction.xCoord);
        Vec3 a=nearest.addVector(-normal.xCoord*SAFE_DISTANCE_WITH_PADDING,0,-normal.zCoord*SAFE_DISTANCE_WITH_PADDING),b=nearest.addVector(normal.xCoord*SAFE_DISTANCE_WITH_PADDING,0,normal.zCoord*SAFE_DISTANCE_WITH_PADDING),center=line.nearest(player);
        double wa=walkable.distance(center,a),wb=walkable.distance(center,b);boolean first=wa>=SAFE_DISTANCE&&(wb<SAFE_DISTANCE||horizontalDistance(a,after)<horizontalDistance(b,after)-.05);
        List<Plan> result=new ArrayList<>(2);if(first){if(wa>=SAFE_DISTANCE)result.add(new Plan(a,player,inputYaw,velocity,impactTicks,distance));if(wb>=SAFE_DISTANCE)result.add(new Plan(b,player,inputYaw,velocity,impactTicks,distance));}
        else{if(wb>=SAFE_DISTANCE)result.add(new Plan(b,player,inputYaw,velocity,impactTicks,distance));if(wa>=SAFE_DISTANCE)result.add(new Plan(a,player,inputYaw,velocity,impactTicks,distance));}return result;
    }
    /** Same 20-degree dead zones as LiquidBounce's directional-input helper. */
    public static float[] keys(Vec3 toward,float yaw){double degrees=MathHelper.wrapAngleTo180_double(Math.toDegrees(Math.atan2(-toward.xCoord,toward.zCoord))-yaw);return new float[]{Math.abs(degrees)<70?1:Math.abs(degrees)>110?-1:0,degrees< -20&&degrees> -160?1:degrees>20&&degrees<160?-1:0};}
    public static Vec3 worldDirection(float forward,float strafe,float yaw){double a=Math.toRadians(yaw);return new Vec3(strafe*Math.cos(a)-forward*Math.sin(a),0,forward*Math.cos(a)+strafe*Math.sin(a));}
    private static double dot(Vec3 a,Vec3 b){return a.xCoord*b.xCoord+a.zCoord*b.zCoord;}
    private static double length(Vec3 v){return Math.hypot(v.xCoord,v.zCoord);}
    private static double horizontalDistance(Vec3 a,Vec3 b){return Math.hypot(a.xCoord-b.xCoord,a.zCoord-b.zCoord);}
}

package dev.vibe.features;

import dev.vibe.module.impl.world.RavenSimulatedPlayer;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.util.*;

/** Uses Vibe's existing copy of 1.8.9 player physics, never changes the real player. */
public final class MovementForecast {
    public static final class Path {public final List<Vec3> points=new ArrayList<>();public final List<AxisAlignedBB> bodies=new ArrayList<>();public boolean loaded=true,safe=true;}
    private MovementForecast(){}
    public interface Controller {void beforeTick(RavenSimulatedPlayer player,int tick);}
    public static Path simulate(MovementInput input,int ticks,float yaw){return simulate(input,ticks,yaw,null);}
    public static Path simulate(MovementInput input,int ticks,float yaw,Controller controller){Minecraft mc=Minecraft.getMinecraft();Path path=new Path();if(mc.thePlayer==null||mc.theWorld==null){path.loaded=path.safe=false;return path;}
        RavenSimulatedPlayer sim=RavenSimulatedPlayer.fromClientPlayer(input);sim.rotationYaw=yaw;sim.setSprintRequested(mc.thePlayer.isSprinting()||mc.gameSettings.keyBindSprint.isKeyDown());
        path.points.add(sim.getPos());path.bodies.add(sim.box);
        for(int i=0;i<Math.max(1,Math.min(100,ticks));i++){
            if(controller!=null)controller.beforeTick(sim,i);
            AxisAlignedBB probe=sim.box.expand(1,1,1).addCoord(sim.motionX,sim.motionY,sim.motionZ);
            if(!loaded(probe)){path.loaded=path.safe=false;break;}sim.tick();path.points.add(sim.getPos());path.bodies.add(sim.box);
            if(!supported(sim.box,4)||sim.fallDistance>3)path.safe=false;
        }return path;
    }
    public static boolean loaded(AxisAlignedBB b){Minecraft mc=Minecraft.getMinecraft();if(mc.theWorld==null)return false;
        for(int x=MathHelper.floor_double(b.minX)>>4;x<=MathHelper.floor_double(b.maxX)>>4;x++)for(int z=MathHelper.floor_double(b.minZ)>>4;z<=MathHelper.floor_double(b.maxZ)>>4;z++)if(!mc.theWorld.isBlockLoaded(new BlockPos(x<<4,64,z<<4)))return false;return true;}
    public static boolean supported(AxisAlignedBB b,double drop){Minecraft mc=Minecraft.getMinecraft();AxisAlignedBB below=new AxisAlignedBB(b.minX+.06,b.minY-drop,b.minZ+.06,b.maxX-.06,b.minY+.02,b.maxZ-.06);return loaded(below)&&!mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer,below).isEmpty();}
    public static MovementInput copy(MovementInput source){MovementInput i=new MovementInput();i.moveForward=source.moveForward;i.moveStrafe=source.moveStrafe;i.jump=source.jump;i.sneak=source.sneak;return i;}
}

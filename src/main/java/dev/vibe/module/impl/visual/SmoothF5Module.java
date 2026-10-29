package dev.vibe.module.impl.visual;

import dev.vibe.Vibe;
import dev.vibe.camera.PerspectiveTransition;
import dev.vibe.module.*;
import dev.vibe.module.impl.movement.FreecamModule;
import dev.vibe.setting.*;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.*;
import net.minecraft.world.World;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;

/** Only the view matrix moves. Player rotations, input and packets retain vanilla behavior. */
public final class SmoothF5Module extends dev.vibe.module.Module {
    public final NumberSetting speed=addSetting(new NumberSetting("Speed",4,1,12,.25));
    public final ModeSetting easing=addSetting(new ModeSetting("Easing","Sine","Linear","Sine","Cubic","Quintic"));
    public final ModeSetting direction=addSetting(new ModeSetting("Direction","Top","Top","Left","Right"));
    private final PerspectiveTransition transition=new PerspectiveTransition();
    private World world;private Entity entity;
    private double[] frame;
    public SmoothF5Module(){super("SmoothF5","Smooth perspective transitions around the player",Category.VISUAL,Keyboard.KEY_NONE);}
    @Override protected void onDisable(){world=null;entity=null;frame=null;transition.reset(Minecraft.getMinecraft().gameSettings.thirdPersonView);}
    public void frameTick(float partial){
        Minecraft mc=Minecraft.getMinecraft();Entity view=mc.getRenderViewEntity();
        if(!isEnabled()||view==null||mc.theWorld==null||FreecamModule.cameraActiveHook()||mc.gameSettings.debugCamEnable||(view instanceof net.minecraft.entity.player.EntityPlayer&&((net.minecraft.entity.player.EntityPlayer)view).isPlayerSleeping())){
            world=null;entity=null;frame=null;transition.reset(mc.gameSettings.thirdPersonView);return;
        }
        if(world!=mc.theWorld||entity!=view){world=mc.theWorld;entity=view;transition.reset(mc.gameSettings.thirdPersonView);}
        transition.update(mc.gameSettings.thirdPersonView,System.nanoTime(),speed.getDouble(),easing.getValue(),direction.getValue());
        frame=transition.frame(view.prevRotationYaw+(view.rotationYaw-view.prevRotationYaw)*partial,view.prevRotationPitch+(view.rotationPitch-view.prevRotationPitch)*partial);
    }
    private boolean transitioning(){return isEnabled()&&frame!=null&&entity==Minecraft.getMinecraft().getRenderViewEntity()&&world==Minecraft.getMinecraft().theWorld&&transition.active(System.nanoTime());}
    private static SmoothF5Module active(){Vibe vibe=Vibe.getInstance();if(vibe==null||vibe.getModuleManager()==null)return null;SmoothF5Module module=vibe.getModuleManager().getModule(SmoothF5Module.class);return module!=null&&module.transitioning()?module:null;}
    public static boolean orientCameraHook(float partial){
        Vibe vibe=Vibe.getInstance();if(vibe==null||vibe.getModuleManager()==null)return false;
        SmoothF5Module module=vibe.getModuleManager().getModule(SmoothF5Module.class);if(module==null||!module.isEnabled())return false;
        module.frameTick(partial);if(!module.transitioning())return false;
        Entity player=module.entity;double[] f=module.frame;float eye=player.getEyeHeight();
        double x=player.lastTickPosX+(player.posX-player.lastTickPosX)*partial,y=player.lastTickPosY+(player.posY-player.lastTickPosY)*partial+eye,z=player.lastTickPosZ+(player.posZ-player.lastTickPosZ)*partial;
        Vec3 origin=new Vec3(x,y,z);double fraction=1,length=module.transition.distance();
        if(length>.001)for(int i=0;i<8;i++){
            double dx=((i&1)==0?-.1:.1),dy=((i&2)==0?-.1:.1),dz=((i&4)==0?-.1:.1);
            Vec3 begin=origin.addVector(dx,dy,dz);MovingObjectPosition hit=module.world.rayTraceBlocks(begin,begin.addVector(f[0],f[1],f[2]));
            if(hit!=null)fraction=Math.min(fraction,Math.max(0,(hit.hitVec.distanceTo(begin)-.15)/length));
        }
        double cx=f[0]*fraction,cy=eye+f[1]*fraction,cz=f[2]*fraction;
        GL11.glTranslated(0,0,-.1*Math.max(0,1-length/4));
        GLU.gluLookAt((float)cx,(float)cy,(float)cz,(float)(cx+f[3]),(float)(cy+f[4]),(float)(cz+f[5]),(float)f[6],(float)f[7],(float)f[8]);
        return true;
    }
    public static int thirdPersonHook(int vanilla){SmoothF5Module m=active();return m==null?vanilla:m.transition.distance()>.3?1:0;}
    public static float yawHook(Object view,float vanilla){SmoothF5Module m=active();return m==null||view!=m.entity?vanilla:(float)Math.toDegrees(Math.atan2(-m.frame[3],m.frame[5]));}
    public static float pitchHook(Object view,float vanilla){SmoothF5Module m=active();return m==null||view!=m.entity?vanilla:(float)-Math.toDegrees(Math.asin(Math.max(-1,Math.min(1,m.frame[4]))));}
    public static boolean frontViewHook(boolean vanilla){return active()==null&&vanilla;}
}

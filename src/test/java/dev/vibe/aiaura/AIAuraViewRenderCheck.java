package dev.vibe.aiaura;

import dev.vibe.event.ClientEvents;
import dev.vibe.module.Module;
import dev.vibe.module.impl.combat.AIAuraModule;
import dev.vibe.path.PathTestFixture;
import java.nio.FloatBuffer;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

/** The real Forge camera event and native view matrix must point along the controlled view. */
public final class AIAuraViewRenderCheck {
    public static void main(String[] args) throws Exception {
        Pbuffer buffer=new Pbuffer(320,240,new PixelFormat(8,24,8),null,null);buffer.makeCurrent();
        try(PathTestFixture f=new PathTestFixture()){
            AIAuraModule ai=new AIAuraModule();f.modules.registerDynamic(ai);
            PathTestFixture.set(Module.class,ai,"enabled",true);ai.lockFov.setEnabled(true);
            PathTestFixture.set(AIAuraModule.class,ai,"cameraFollowing",true);
            PathTestFixture.set(AIAuraModule.class,ai,"cameraPlayer",f.player);
            PathTestFixture.set(AIAuraModule.class,ai,"cameraYaw",90F);
            PathTestFixture.set(AIAuraModule.class,ai,"cameraPitch",30F);
            ClientEvents events=PathTestFixture.allocate(ClientEvents.class);
            for(int perspective=0;perspective<3;perspective++){
                f.mc.gameSettings.thirdPersonView=perspective;
                f.player.rotationYaw=-130;f.player.rotationPitch=-45;
                EntityViewRenderEvent.CameraSetup view=new EntityViewRenderEvent.CameraSetup(null,f.player,null,.5,12,-40,0);
                events.onCameraSetup(view);
                check(Math.abs(view.yaw-225)<.0001&&Math.abs(view.pitch-15)<.0001,"Forge camera event ignored locked yaw/pitch");
                check(f.player.rotationYaw==90&&f.player.prevRotationYaw==0,"Vanilla camera endpoints drifted");
                GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glLoadIdentity();
                GL11.glRotatef(view.roll,0,0,1);GL11.glRotatef(view.pitch,1,0,0);GL11.glRotatef(view.yaw,0,1,0);
                FloatBuffer matrix=BufferUtils.createFloatBuffer(16);GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX,matrix);
                double yaw=Math.toRadians(45),pitch=Math.toRadians(15);
                double dx=-Math.sin(yaw)*Math.cos(pitch),dy=-Math.sin(pitch),dz=Math.cos(yaw)*Math.cos(pitch);
                double x=matrix.get(0)*dx+matrix.get(4)*dy+matrix.get(8)*dz;
                double y=matrix.get(1)*dx+matrix.get(5)*dy+matrix.get(9)*dz;
                double z=matrix.get(2)*dx+matrix.get(6)*dy+matrix.get(10)*dz;
                check(Math.abs(x)<.00001&&Math.abs(y)<.00001&&Math.abs(z+1)<.00001,"Native camera matrix faces a different direction from AIAura");
            }
            ai.lockFov.setEnabled(false);
            EntityViewRenderEvent.CameraSetup unlocked=new EntityViewRenderEvent.CameraSetup(null,f.player,null,.5,12,-40,3);
            events.onCameraSetup(unlocked);check(unlocked.yaw==12&&unlocked.pitch==-40&&unlocked.roll==3,"Disabled lock still controls the camera");
            ai.setEnabled(false);check(GL11.glGetError()==GL11.GL_NO_ERROR,"Camera lock left a GL error");
            System.out.println("AIAura LockView passed: Forge camera events, native view direction in all perspectives, synchronized endpoints and disabled fallback.");
        }finally{buffer.destroy();}
    }
    private static void check(boolean valid,String message){if(!valid)throw new AssertionError(message);}
}

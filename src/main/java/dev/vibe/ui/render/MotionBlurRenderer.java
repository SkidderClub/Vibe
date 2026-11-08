package dev.vibe.ui.render;

import dev.vibe.module.impl.visual.MotionBlurModule;
import dev.vibe.ui.effect.EffectState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;
import org.lwjgl.opengl.*;

/** One reusable history texture; composited and copied before any HUD pixels are drawn. */
public final class MotionBlurRenderer implements AutoCloseable {
    private int texture,width,height;private boolean valid,drawn;private long previous;
    private Object world,view;private double x,y,z;private float yaw,pitch;
    public void beginFrame(){drawn=false;}
    public void render(MotionBlurModule module){
        if(drawn)return;drawn=true;Minecraft mc=Minecraft.getMinecraft();Entity camera=mc.getRenderViewEntity();
        if(module==null||!module.isEnabled()||module.strength.getDouble()<=0||mc.theWorld==null||camera==null||mc.currentScreen!=null||mc.isGamePaused()) {reset();return;}
        long now=System.nanoTime();double dt=previous==0?1/60D:(now-previous)/1e9;
        boolean cut=world!=mc.theWorld||view!=camera||dt>.25||square(camera.posX-x)+square(camera.posY-y)+square(camera.posZ-z)>64||Math.abs(MathHelper.wrapAngleTo180_float(camera.rotationYaw-yaw))>100;
        float rotation=Math.abs(MathHelper.wrapAngleTo180_float(camera.rotationYaw-yaw))+Math.abs(camera.rotationPitch-pitch);
        world=mc.theWorld;view=camera;x=camera.posX;y=camera.posY;z=camera.posZ;yaw=camera.rotationYaw;pitch=camera.rotationPitch;previous=now;
        float weight=MotionBlurModule.historyWeight(module.strength.getDouble(),dt);
        if(module.turningOnly.isEnabled()&&rotation<module.turnThreshold.getDouble())weight=0;
        composite(mc.displayWidth,mc.displayHeight,cut?0:weight);
    }
    /** Public for the native-driver regression fixture; alpha never affects the depth buffer. */
    public void composite(int w,int h,float weight){
        if(w<=0||h<=0)return;int mode=GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        try(EffectState ignored=new EffectState()){
            GL20.glUseProgram(0);for(int unit=1;unit<=2;unit++){GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);GL11.glDisable(GL11.GL_TEXTURE_2D);}GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glTexEnvi(GL11.GL_TEXTURE_ENV,GL11.GL_TEXTURE_ENV_MODE,GL11.GL_MODULATE);
            GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glEnable(GL11.GL_TEXTURE_2D);
            if(texture==0){texture=GL11.glGenTextures();valid=false;}
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);
            if(width!=w||height!=h){width=w;height=h;valid=false;GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA8,w,h,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,(java.nio.ByteBuffer)null);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_LINEAR);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_LINEAR);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,GL12.GL_CLAMP_TO_EDGE);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,GL12.GL_CLAMP_TO_EDGE);}
            if(valid&&weight>.0001F){
                GL11.glMatrixMode(GL11.GL_TEXTURE);GL11.glPushMatrix();GL11.glLoadIdentity();
                GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glPushMatrix();GL11.glLoadIdentity();GL11.glOrtho(0,1,0,1,-1,1);
                GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glPushMatrix();GL11.glLoadIdentity();
                try{GL11.glEnable(GL11.GL_BLEND);GL20.glBlendEquationSeparate(GL14.GL_FUNC_ADD,GL14.GL_FUNC_ADD);GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA,GL11.GL_ZERO,GL11.GL_ONE);GL11.glColor4f(1,1,1,Math.max(0,Math.min(.99F,weight)));
                    GL11.glBegin(GL11.GL_QUADS);GL11.glTexCoord2f(0,0);GL11.glVertex2f(0,0);GL11.glTexCoord2f(1,0);GL11.glVertex2f(1,0);GL11.glTexCoord2f(1,1);GL11.glVertex2f(1,1);GL11.glTexCoord2f(0,1);GL11.glVertex2f(0,1);GL11.glEnd();
                }finally{GL11.glPopMatrix();GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glPopMatrix();GL11.glMatrixMode(GL11.GL_TEXTURE);GL11.glPopMatrix();GL11.glMatrixMode(mode);}
            }
            GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,0,0,w,h);valid=true;
        }
    }
    private static double square(double n){return n*n;}
    public void reset(){valid=false;previous=0;world=view=null;}
    @Override public void close(){if(texture!=0)GL11.glDeleteTextures(texture);texture=width=height=0;reset();}
}

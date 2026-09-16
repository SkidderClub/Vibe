package dev.vibe.ui;

import dev.vibe.ui.effect.EffectProgram;
import dev.vibe.ui.effect.EffectState;
import dev.vibe.ui.effect.SceneTexture;
import java.nio.IntBuffer;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

/** Depth-tested silhouette mask; one separable blur for the whole entity pass. */
final class ChamsGlow {
    private static int mask, horizontal, depth, framebuffer, width, height, destination, vx, vy;
    private static boolean pending, failed;
    private static EffectProgram blur;
    private ChamsGlow() { }

    static void capture(Runnable geometry) {
        if (failed || !OpenGlHelper.framebufferSupported) return;
        try (EffectState state=new EffectState()) {
            IntBuffer viewport=BufferUtils.createIntBuffer(16); GL11.glGetInteger(GL11.GL_VIEWPORT,viewport);
            int w=viewport.get(2), h=viewport.get(3);
            if(w<=0||h<=0)return;
            if(pending&&(w!=width||h!=height||destination!=state.destination()||vx!=viewport.get(0)||vy!=viewport.get(1)))finish();
            if(blur==null) {
                try { blur=new EffectProgram("ChamsHalo.vert","ChamsHalo.frag"); }
                catch(Exception e){failed=true;org.apache.logging.log4j.LogManager.getLogger("Vibe").warn("Chams halo shader unavailable",e);return;}
            }
            int skin=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            if(width!=w||height!=h||framebuffer==0) {
                release();width=w;height=h;
                mask=SceneTexture.allocate(w,h,false);horizontal=SceneTexture.allocate(w,h,false);depth=SceneTexture.allocate(w,h,true);
                framebuffer=OpenGlHelper.glGenFramebuffers();
            }
            destination=state.destination();vx=viewport.get(0);vy=viewport.get(1);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,depth);
            GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,vx,vy,w,h);
            target(mask,true);
            if(OpenGlHelper.glCheckFramebufferStatus(OpenGlHelper.GL_FRAMEBUFFER)!=OpenGlHelper.GL_FRAMEBUFFER_COMPLETE){failed=true;release();return;}
            GL11.glViewport(0,0,w,h);
            if(!pending){GL11.glClearColor(0,0,0,0);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);}
            GL11.glEnable(GL11.GL_DEPTH_TEST);GL11.glDepthMask(false);
            GL11.glEnable(GL11.GL_CULL_FACE);GL11.glCullFace(GL11.GL_BACK);
            GL11.glEnable(GL11.GL_BLEND);GL14.glBlendFuncSeparate(GL11.GL_ONE,GL11.GL_ONE_MINUS_SRC_ALPHA,GL11.GL_ONE,GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,skin);
            geometry.run();pending=true;
        }
    }

    static void finish() {
        if(!pending)return;
        pending=false;
        try(EffectState state=new EffectState()) {
            GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glDisable(GL11.GL_BLEND);
            GL11.glViewport(0,0,width,height);
            blur.bind();blur.integer("Source",0);blur.integer("Mask",1);
            GL13.glActiveTexture(GL13.GL_TEXTURE1);GL11.glBindTexture(GL11.GL_TEXTURE_2D,mask);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glBindTexture(GL11.GL_TEXTURE_2D,mask);
            target(horizontal,false);blur.integer("Composite",0);blur.vec2("Step",1F/width,0);quad();
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER,destination);
            GL11.glViewport(vx,vy,width,height);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,horizontal);
            GL11.glEnable(GL11.GL_BLEND);GL20.glBlendEquationSeparate(GL14.GL_FUNC_ADD,GL14.GL_FUNC_ADD);
            GL14.glBlendFuncSeparate(GL11.GL_ONE,GL11.GL_ONE,GL11.GL_ZERO,GL11.GL_ONE);
            blur.integer("Composite",1);blur.vec2("Step",0,1F/height);quad();
        }
    }
    private static void target(int texture,boolean withDepth) {
        OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER,framebuffer);
        OpenGlHelper.glFramebufferTexture2D(OpenGlHelper.GL_FRAMEBUFFER,OpenGlHelper.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,texture,0);
        OpenGlHelper.glFramebufferTexture2D(OpenGlHelper.GL_FRAMEBUFFER,OpenGlHelper.GL_DEPTH_ATTACHMENT,GL11.GL_TEXTURE_2D,withDepth?depth:0,0);
    }
    private static void quad(){GL11.glBegin(GL11.GL_QUADS);GL11.glVertex2f(-1,-1);GL11.glVertex2f(1,-1);GL11.glVertex2f(1,1);GL11.glVertex2f(-1,1);GL11.glEnd();}
    private static void release(){
        if(mask!=0)GL11.glDeleteTextures(mask);if(horizontal!=0)GL11.glDeleteTextures(horizontal);if(depth!=0)GL11.glDeleteTextures(depth);
        if(framebuffer!=0)OpenGlHelper.glDeleteFramebuffers(framebuffer);
        mask=horizontal=depth=framebuffer=0;pending=false;
    }
}

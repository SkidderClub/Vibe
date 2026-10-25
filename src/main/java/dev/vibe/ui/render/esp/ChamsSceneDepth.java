package dev.vibe.ui.render.esp;

import dev.vibe.ui.effect.EffectProgram;
import dev.vibe.ui.effect.EffectState;
import dev.vibe.ui.effect.SceneTexture;
import java.nio.IntBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

/** Opaque world depth before any player body/armor layers have written to it. */
final class ChamsSceneDepth {
    private static int texture,width,height,x,y;
    private static boolean valid;
    private ChamsSceneDepth() { }
    static void invalidate() {valid=false;}
    static void capture() {
        valid=false;
        try(EffectState ignored=new EffectState()) {
            IntBuffer viewport=BufferUtils.createIntBuffer(16);GL11.glGetInteger(GL11.GL_VIEWPORT,viewport);
            int w=viewport.get(2),h=viewport.get(3);if(w<=0||h<=0)return;
            GL13.glActiveTexture(GL13.GL_TEXTURE2);
            if(texture==0||width!=w||height!=h) {
                if(texture!=0)GL11.glDeleteTextures(texture);
                texture=SceneTexture.allocate(w,h,true);width=w;height=h;
            }
            x=viewport.get(0);y=viewport.get(1);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);
            GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,x,y,w,h);valid=true;
        }
    }
    static boolean bind(EffectProgram shader,boolean enabled) {
        enabled&=valid;
        shader.integer("SceneSide",-1);
        if(enabled) {
            GL13.glActiveTexture(GL13.GL_TEXTURE2);GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);GL13.glActiveTexture(GL13.GL_TEXTURE0);
            shader.integer("SceneDepth",2);shader.vec2("SceneSize",width,height);origin(shader,false);
        }
        return enabled;
    }
    static void origin(EffectProgram shader,boolean mask) {shader.vec2("SceneOrigin",mask?0:x,mask?0:y);}
}

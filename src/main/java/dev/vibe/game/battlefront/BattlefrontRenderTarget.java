package dev.vibe.game.battlefront;

import java.nio.ByteBuffer;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.opengl.GL11;

/** Supersamples the 3D view only; the HUD retains Minecraft's native scaled resolution. */
final class BattlefrontRenderTarget {
    private int framebuffer,texture,depth,width,height,attachmentLimit;
    private boolean unavailable;

    boolean prepare(int displayWidth,int displayHeight) {
        if(unavailable||displayWidth<=0||displayHeight<=0||!OpenGlHelper.isFramebufferEnabled())return false;
        double factor=Math.max(1,Math.min(1.5,Math.sqrt(3686400.0/Math.max(1,(double)displayWidth*displayHeight))));
        if(attachmentLimit==0)attachmentLimit=Math.min(GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE),GL11.glGetInteger(0x84E8));
        int limit=attachmentLimit;
        factor=Math.min(factor,Math.min(limit/(double)displayWidth,limit/(double)displayHeight));
        if(factor<1.05)return false;
        int w=(int)Math.ceil(displayWidth*factor),h=(int)Math.ceil(displayHeight*factor);
        if(framebuffer!=0&&width==w&&height==h)return true;
        close();width=w;height=h;
        framebuffer=OpenGlHelper.glGenFramebuffers();texture=GL11.glGenTextures();depth=OpenGlHelper.glGenRenderbuffers();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA8,w,h,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,(ByteBuffer)null);
        OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER,framebuffer);
        OpenGlHelper.glFramebufferTexture2D(OpenGlHelper.GL_FRAMEBUFFER,OpenGlHelper.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,texture,0);
        OpenGlHelper.glBindRenderbuffer(OpenGlHelper.GL_RENDERBUFFER,depth);
        OpenGlHelper.glRenderbufferStorage(OpenGlHelper.GL_RENDERBUFFER,org.lwjgl.opengl.GL14.GL_DEPTH_COMPONENT24,w,h);
        OpenGlHelper.glFramebufferRenderbuffer(OpenGlHelper.GL_FRAMEBUFFER,OpenGlHelper.GL_DEPTH_ATTACHMENT,OpenGlHelper.GL_RENDERBUFFER,depth);
        if(OpenGlHelper.glCheckFramebufferStatus(OpenGlHelper.GL_FRAMEBUFFER)!=OpenGlHelper.GL_FRAMEBUFFER_COMPLETE) {
            close();unavailable=true;return false;
        }
        return true;
    }
    void bind() { OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER,framebuffer);GL11.glViewport(0,0,width,height); }
    void present(int destination,int displayWidth,int displayHeight) {
        OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER,destination);GL11.glViewport(0,0,displayWidth,displayHeight);
        GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glDisable(GL11.GL_FOG);GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glDisable(GL11.GL_BLEND);GL11.glEnable(GL11.GL_TEXTURE_2D);GL11.glDepthMask(false);
        GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glLoadIdentity();GL11.glOrtho(0,1,0,1,-1,1);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glLoadIdentity();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);GL11.glColor4f(1,1,1,1);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0,0);GL11.glVertex2f(0,0);GL11.glTexCoord2f(1,0);GL11.glVertex2f(1,0);
        GL11.glTexCoord2f(1,1);GL11.glVertex2f(1,1);GL11.glTexCoord2f(0,1);GL11.glVertex2f(0,1);
        GL11.glEnd();
    }
    void close() {
        if(framebuffer!=0)OpenGlHelper.glDeleteFramebuffers(framebuffer);
        if(depth!=0)OpenGlHelper.glDeleteRenderbuffers(depth);
        if(texture!=0)GL11.glDeleteTextures(texture);
        framebuffer=texture=depth=0;
    }
}

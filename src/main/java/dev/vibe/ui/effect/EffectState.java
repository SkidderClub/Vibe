package dev.vibe.ui.effect;

import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.opengl.*;

/** Raw GL only: restores actual state without invalidating Minecraft's cached state. */
public final class EffectState implements AutoCloseable {
    private final int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
    private final int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
    private final int framebuffer = OpenGlHelper.framebufferSupported ? GL11.glGetInteger(0x8CA6) : 0;
    private final int[] textures = new int[3];
    public EffectState() {
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        for (int i = 0; i < textures.length; i++) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + i); textures[i] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        }
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glDisable(GL11.GL_ALPHA_TEST); GL11.glDisable(GL11.GL_LIGHTING); GL11.glDisable(GL11.GL_FOG);
        GL11.glDisable(GL11.GL_CULL_FACE); GL11.glDisable(GL11.GL_SCISSOR_TEST); GL11.glDisable(GL11.GL_STENCIL_TEST);
        GL11.glColorMask(true, true, true, true); GL11.glDepthMask(false);
    }
    public int destination() { return framebuffer; }
    public void close() {
        if (OpenGlHelper.framebufferSupported) OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, framebuffer);
        GL20.glUseProgram(program);
        for (int i = 0; i < textures.length; i++) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + i); GL11.glBindTexture(GL11.GL_TEXTURE_2D, textures[i]);
        }
        GL13.glActiveTexture(active); GL11.glPopAttrib();
    }
}

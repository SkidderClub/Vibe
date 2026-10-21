package dev.vibe.phone;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

/** Supersamples the phone and hand together, then filters their coverage when tilted/scaled. */
final class PhoneCanvas {
    static final int LEFT = -72, TOP = -16, WIDTH = 336, HEIGHT = 584;
    private int framebuffer, texture, depth, width, height;
    private boolean unavailable;
    private final IntBuffer viewport = BufferUtils.createIntBuffer(16);
    private final FloatBuffer clear = BufferUtils.createFloatBuffer(16);

    boolean draw(Runnable contents) {
        if (unavailable || !OpenGlHelper.isFramebufferEnabled()) return false;
        int destination = GL11.glGetInteger(0x8CA6); // GL_FRAMEBUFFER_BINDING, also on EXT FBOs.
        int renderbuffer = GL11.glGetInteger(0x8CA7);
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int matrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        viewport.clear(); GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);
        clear.clear(); GL11.glGetFloat(GL11.GL_COLOR_CLEAR_VALUE, clear);
        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        boolean matrices = false, ready = false;
        try {
            if (!prepare()) return false;
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, framebuffer);
            GL11.glViewport(0, 0, width, height); GL11.glDisable(GL11.GL_SCISSOR_TEST); GL20.glUseProgram(0);
            GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glPushMatrix(); GL11.glLoadIdentity();
            GL11.glOrtho(LEFT, LEFT + WIDTH, TOP + HEIGHT, TOP, -1000, 1000);
            GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glPushMatrix(); GL11.glLoadIdentity(); matrices = true;
            GlStateManager.clearColor(0, 0, 0, 0); GlStateManager.depthMask(true);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
            contents.run();
            ready = true;
        } finally {
            if (matrices) {
                GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glPopMatrix();
                GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glPopMatrix();
            }
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, destination);
            OpenGlHelper.glBindRenderbuffer(OpenGlHelper.GL_RENDERBUFFER, renderbuffer);
            GL11.glViewport(viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3));
            GlStateManager.clearColor(clear.get(0), clear.get(1), clear.get(2), clear.get(3));
            GL11.glMatrixMode(matrixMode);
            if (scissor) GL11.glEnable(GL11.GL_SCISSOR_TEST);
            if (!ready) { GlStateManager.bindTexture(previousTexture); GL20.glUseProgram(program); }
        }
        try {
            GlStateManager.bindTexture(texture);
            generateMipmaps();
            GlStateManager.enableTexture2D(); GlStateManager.enableBlend(); GlStateManager.disableAlpha();
            GlStateManager.disableDepth(); GlStateManager.depthMask(false); GlStateManager.color(1, 1, 1, 1);
            // Filtering premultiplied color prevents dark fringes around the phone and text.
            GlStateManager.tryBlendFuncSeparate(1, 771, 1, 771);
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2f(0, 1); GL11.glVertex2f(LEFT, TOP);
            GL11.glTexCoord2f(0, 0); GL11.glVertex2f(LEFT, TOP + HEIGHT);
            GL11.glTexCoord2f(1, 0); GL11.glVertex2f(LEFT + WIDTH, TOP + HEIGHT);
            GL11.glTexCoord2f(1, 1); GL11.glVertex2f(LEFT + WIDTH, TOP);
            GL11.glEnd();
            dev.vibe.ui.render.ItemGlowRenderer.phone(texture, LEFT, TOP, WIDTH, HEIGHT);
        } finally {
            GlStateManager.bindTexture(previousTexture); GL20.glUseProgram(program);
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        }
        return true;
    }

    static void generateMipmaps() {
        ContextCapabilities capabilities = GLContext.getCapabilities();
        if (capabilities.OpenGL30) GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
        else if (capabilities.GL_ARB_framebuffer_object) ARBFramebufferObject.glGenerateMipmap(GL11.GL_TEXTURE_2D);
        else if (capabilities.GL_EXT_framebuffer_object) EXTFramebufferObject.glGenerateMipmapEXT(GL11.GL_TEXTURE_2D);
        else GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
    }

    private boolean prepare() {
        if (framebuffer != 0) return true;
        int limit = Math.min(GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE), GL11.glGetInteger(0x84E8));
        float samples = Math.min(4, limit / (float) HEIGHT);
        if (samples < 1) { unavailable = true; return false; }
        width = (int) (WIDTH * samples); height = (int) (HEIGHT * samples);
        framebuffer = OpenGlHelper.glGenFramebuffers(); texture = GL11.glGenTextures(); depth = OpenGlHelper.glGenRenderbuffers();
        GlStateManager.bindTexture(texture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, framebuffer);
        OpenGlHelper.glFramebufferTexture2D(OpenGlHelper.GL_FRAMEBUFFER, OpenGlHelper.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, texture, 0);
        OpenGlHelper.glBindRenderbuffer(OpenGlHelper.GL_RENDERBUFFER, depth);
        OpenGlHelper.glRenderbufferStorage(OpenGlHelper.GL_RENDERBUFFER, GL14.GL_DEPTH_COMPONENT24, width, height);
        OpenGlHelper.glFramebufferRenderbuffer(OpenGlHelper.GL_FRAMEBUFFER, OpenGlHelper.GL_DEPTH_ATTACHMENT, OpenGlHelper.GL_RENDERBUFFER, depth);
        if (OpenGlHelper.glCheckFramebufferStatus(OpenGlHelper.GL_FRAMEBUFFER) == OpenGlHelper.GL_FRAMEBUFFER_COMPLETE) return true;
        close(); unavailable = true; return false;
    }

    void close() {
        if (framebuffer != 0) OpenGlHelper.glDeleteFramebuffers(framebuffer);
        if (depth != 0) OpenGlHelper.glDeleteRenderbuffers(depth);
        if (texture != 0) GlStateManager.deleteTexture(texture);
        framebuffer = texture = depth = 0;
    }
}

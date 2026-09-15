package dev.vibe.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.client.shader.ShaderGroup;
import net.minecraft.util.ResourceLocation;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/** Multi-pass post-processing backdrop with a safe legacy fallback. */
public final class KawaseBlur {

    private static ShaderGroup shader;
    private static int framebufferWidth = -1;
    private static int framebufferHeight = -1;
    private static boolean unavailable;
    private static boolean roundedCompositeUnavailable;
    private static Framebuffer roundedFramebuffer;
    private static ShaderGroup roundedShader;
    private static int roundedWidth = -1;
    private static int roundedHeight = -1;

    private KawaseBlur() {
    }

    public static void drawBackdrop(int width, int height, int passes, float partialTicks) {
        if (!unavailable) {
            try {
                Minecraft minecraft = Minecraft.getMinecraft();
                if (shader == null || framebufferWidth != minecraft.displayWidth || framebufferHeight != minecraft.displayHeight) {
                    shader = new ShaderGroup(minecraft.getTextureManager(), minecraft.getResourceManager(), minecraft.getFramebuffer(),
                            new ResourceLocation("minecraft", "shaders/post/blur.json"));
                    shader.createBindFramebuffers(minecraft.displayWidth, minecraft.displayHeight);
                    framebufferWidth = minecraft.displayWidth;
                    framebufferHeight = minecraft.displayHeight;
                }
                int safePasses = Math.max(1, Math.min(3, passes / 3));
                FloatBuffer clearColor = BufferUtils.createFloatBuffer(16);
                GL11.glGetFloat(GL11.GL_COLOR_CLEAR_VALUE, clearColor);
                GL11.glClearColor(1.0F, 1.0F, 1.0F, 1.0F);
                for (int pass = 0; pass < safePasses; pass++) {
                    shader.loadShaderGroup(partialTicks);
                }
                GL11.glClearColor(clearColor.get(0), clearColor.get(1), clearColor.get(2), clearColor.get(3));
                // ShaderGroup renders through intermediate framebuffers. Rebind the main
                // framebuffer before drawing the ClickGUI itself, then restore
                // GuiScreen's scaled orthographic projection.  Shader passes
                // render in physical framebuffer pixels and otherwise leave
                // menus at the wrong size on GUI Scale 2+.
                restoreGuiProjection(minecraft);
                GlStateManager.enableTexture2D();
                GlStateManager.enableBlend();
                Gui.drawRect(0, 0, width, height, 0x1807101F);
                return;
            } catch (Exception ignored) {
                unavailable = true;
            }
        }
        for (int pass = Math.max(1, Math.min(8, passes)); pass >= 1; pass--) {
            Gui.drawRect(pass - 1, pass - 1, width - pass + 1, height - pass + 1, (7 + pass * 3) << 24 | 0x07101F);
        }
        Gui.drawRect(0, 0, width, height, 0x3007101F);
    }

    /** Draw the same multipass shader as the ClickGUI, clipped to one HUD widget. */
    public static void drawRegion(int left, int top, int right, int bottom, int passes, float partialTicks) {
        if (right <= left || bottom <= top) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        ScaledResolution resolution = new ScaledResolution(minecraft);
        int scale = resolution.getScaleFactor();
        int x = Math.max(0, left * scale);
        int y = Math.max(0, minecraft.displayHeight - bottom * scale);
        int width = Math.min(minecraft.displayWidth - x, Math.max(0, (right - left) * scale));
        int height = Math.min(minecraft.displayHeight - y, Math.max(0, (bottom - top) * scale));
        if (width <= 0 || height <= 0) {
            return;
        }
        boolean hadScissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        IntBuffer previousScissor = BufferUtils.createIntBuffer(16);
        GL11.glGetInteger(GL11.GL_SCISSOR_BOX, previousScissor);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(x, y, width, height);
        try {
            drawBackdrop(resolution.getScaledWidth(), resolution.getScaledHeight(), passes, partialTicks);
        } finally {
            if (hadScissor) {
                GL11.glScissor(previousScissor.get(0), previousScissor.get(1), previousScissor.get(2), previousScissor.get(3));
            } else {
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
            }
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    /**
     * Blurs a copy of the active screen, then composites only the requested
     * rounded silhouette back onto the real framebuffer.  The post shader
     * never writes to Minecraft's live framebuffer, so it cannot clear the
     * corners black or darken the entire HUD.
     */
    public static void drawRoundedRegion(int left, int top, int right, int bottom, float radius,
                                         int passes, float partialTicks) {
        if (right <= left || bottom <= top) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (unavailable || roundedCompositeUnavailable) {
            return;
        }
        try {
            ensureRoundedShader(minecraft);
            copyFramebuffer(minecraft.getFramebuffer(), roundedFramebuffer);
            int safePasses = Math.max(1, Math.min(2, passes / 2));
            for (int pass = 0; pass < safePasses; pass++) {
                roundedShader.loadShaderGroup(partialTicks);
            }
            restoreGuiProjection(minecraft);
            drawRoundedTexture(roundedFramebuffer, left, top, right, bottom, radius);
        } catch (Exception ignored) {
            // A missing reflective pass list only disables the rounded HUD
            // composite. The full-screen GUI backdrop remains valid.
            roundedCompositeUnavailable = true;
        } finally {
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.enableTexture2D();
        }
    }

    private static void ensureShader(Minecraft minecraft) throws Exception {
        if (shader == null || framebufferWidth != minecraft.displayWidth || framebufferHeight != minecraft.displayHeight) {
            shader = new ShaderGroup(minecraft.getTextureManager(), minecraft.getResourceManager(), minecraft.getFramebuffer(),
                    new ResourceLocation("minecraft", "shaders/post/blur.json"));
            shader.createBindFramebuffers(minecraft.displayWidth, minecraft.displayHeight);
            framebufferWidth = minecraft.displayWidth;
            framebufferHeight = minecraft.displayHeight;
        }
    }

    private static void ensureRoundedShader(Minecraft minecraft) throws Exception {
        if (roundedShader != null && roundedWidth == minecraft.displayWidth && roundedHeight == minecraft.displayHeight) {
            return;
        }
        if (roundedShader != null) {
            roundedShader.deleteShaderGroup();
            roundedShader = null;
        }
        if (roundedFramebuffer != null) {
            roundedFramebuffer.deleteFramebuffer();
            roundedFramebuffer = null;
        }
        roundedFramebuffer = new Framebuffer(minecraft.displayWidth, minecraft.displayHeight, false);
        roundedShader = new ShaderGroup(minecraft.getTextureManager(), minecraft.getResourceManager(), roundedFramebuffer,
                new ResourceLocation("minecraft", "shaders/post/blur.json"));
        roundedShader.createBindFramebuffers(minecraft.displayWidth, minecraft.displayHeight);
        roundedWidth = minecraft.displayWidth;
        roundedHeight = minecraft.displayHeight;
    }

    /** Copies the current rendered frame into the private post-process input. */
    private static void copyFramebuffer(Framebuffer source, Framebuffer destination) {
        GL11.glColorMask(true, true, true, true);
        destination.framebufferClear();
        // framebufferClear unbinds its FBO in 1.8.9. Rebind it before the
        // fullscreen copy; otherwise the copy lands on Minecraft's live
        // framebuffer and the blur input remains empty.
        destination.bindFramebuffer(true);
        source.framebufferRenderExt(destination.framebufferWidth, destination.framebufferHeight, true);
        source.unbindFramebufferTexture();
    }

    /** Restore the scaled GUI transform after a framebuffer/post-process pass. */
    private static void restoreGuiProjection(Minecraft minecraft) {
        minecraft.getFramebuffer().bindFramebuffer(true);
        minecraft.entityRenderer.setupOverlayRendering();
    }

    /** Draws a section of the blur target in a true pixel-rounded silhouette. */
    private static void drawRoundedTexture(Framebuffer source,
                                           int left, int top, int right, int bottom, float radius) {
        if (source == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        ScaledResolution resolution = new ScaledResolution(minecraft);
        int scale = resolution.getScaleFactor();
        int rounded = Math.max(1, Math.min(Math.round(radius), Math.min(right - left, bottom - top) / 2));
        double square = rounded * rounded;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT);
        try {
            GL11.glDisable(GL11.GL_STENCIL_TEST);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GlStateManager.enableTexture2D();
            GlStateManager.enableBlend();
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            source.bindFramebufferTexture();
            for (int y = top; y < bottom; y++) {
                double edge = Math.min(y - top + 0.5D, bottom - y - 0.5D);
                int inset = 0;
                if (edge < rounded) {
                    double chord = Math.sqrt(Math.max(0.0D, square - (rounded - edge) * (rounded - edge)));
                    inset = Math.max(0, rounded - (int) Math.ceil(chord));
                }
                int start = left + inset;
                int end = right - inset;
                if (end <= start) {
                    continue;
                }
                // Framebuffer textures use an inverted V axis relative to a
                // scaled GUI coordinate system.
                float u = start * scale;
                float v = source.framebufferTextureHeight - (y + 1) * scale;
                Gui.drawModalRectWithCustomSizedTexture(start, y, u, v, end - start, 1,
                        source.framebufferTextureWidth, source.framebufferTextureHeight);
            }
            source.unbindFramebufferTexture();
        } finally {
            GL11.glPopAttrib();
        }
    }
}

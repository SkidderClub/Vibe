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
    /** One source capture and blur is shared by every HUD surface in a frame. */
    private static long hudFrame;
    private static long preparedRoundedFrame = Long.MIN_VALUE;
    private static int preparedRoundedPasses;

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
        // The former path ran a full-screen ShaderGroup for every small HUD
        // widget, even though scissoring only happened after the expensive
        // work. Use the cached rounded compositor as a rectangular mask.
        drawRoundedRegion(left, top, right, bottom, 0.0F, passes, partialTicks);
    }

    /** Called once before Vibe's HUD widgets paint in an overlay frame. */
    public static void beginHudFrame() { hudFrame++; }

    /** Prepares one blurred framebuffer which can be composited many times. */
    public static void prepareRoundedFrame(int passes, float partialTicks) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (unavailable || roundedCompositeUnavailable) return;
        int safePasses = Math.max(1, Math.min(1, passes / 2));
        if (preparedRoundedFrame == hudFrame && preparedRoundedPasses >= safePasses) return;
        try {
            ensureRoundedShader(minecraft);
            copyFramebuffer(minecraft.getFramebuffer(), roundedFramebuffer);
            // A single Kawase iteration has the visual softness intended for
            // compact HUDs while avoiding a second full-resolution pass.
            roundedShader.loadShaderGroup(partialTicks);
            preparedRoundedFrame = hudFrame;
            preparedRoundedPasses = safePasses;
        } catch (Exception failure) {
            roundedCompositeUnavailable = true;
            org.apache.logging.log4j.LogManager.getLogger("Vibe").warn("Rounded HUD blur unavailable", failure);
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
        if (unavailable || roundedCompositeUnavailable) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        try {
            prepareRoundedFrame(passes, partialTicks);
            if (roundedCompositeUnavailable || roundedFramebuffer == null) return;
            restoreGuiProjection(minecraft);
            drawRoundedTexture(roundedFramebuffer, left, top, right, bottom, radius);
        } finally {
            // Shader initialization can fail after binding an intermediate FBO.
            // Always return to the HUD target so the fallback surface stays visible.
            restoreGuiProjection(minecraft);
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
        // Vanilla's framebuffer blit deliberately masks alpha writes. An alpha-zero
        // clear makes the blurred copy invisible when composited back into the HUD.
        destination.setFramebufferColor(0, 0, 0, 1);
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
        int rounded = Math.max(0, Math.min(Math.round(radius), Math.min(right - left, bottom - top) / 2));
        if (rounded == 0) {
            GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT);
            try {
                GlStateManager.enableTexture2D(); GlStateManager.enableBlend();
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
                source.bindFramebufferTexture();
                Gui.drawScaledCustomSizeModalRect(left, top, left * scale, source.framebufferTextureHeight - top * scale,
                        (right - left) * scale, -(bottom - top) * scale, right - left, bottom - top,
                        source.framebufferTextureWidth, source.framebufferTextureHeight);
                source.unbindFramebufferTexture();
            } finally { GL11.glPopAttrib(); }
            return;
        }
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
                float v = source.framebufferTextureHeight - y * scale;
                Gui.drawScaledCustomSizeModalRect(start, y, u, v, (end - start) * scale, -scale,
                        end - start, 1, source.framebufferTextureWidth, source.framebufferTextureHeight);
            }
            source.unbindFramebufferTexture();
        } finally {
            GL11.glPopAttrib();
        }
    }
}

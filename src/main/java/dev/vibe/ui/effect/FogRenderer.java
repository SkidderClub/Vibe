/* Copyright (c) 2024. Schizoid. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * ModuleToggleableBlur.kt fog pass port; modified for Vibe on 2026-09-09.
 */
package dev.vibe.ui.effect;

import dev.vibe.Vibe;
import dev.vibe.module.impl.FogModule;
import java.nio.IntBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import org.apache.logging.log4j.LogManager;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

/** Runs before hands and HUD; samples a copy so no pass reads its own render target. */
public final class FogRenderer implements AutoCloseable {
    private final SceneTexture scene = new SceneTexture();
    private final IntBuffer viewport = BufferUtils.createIntBuffer(16);
    private EffectProgram tint, kawase, gaussian, depth;
    private final int[] buffers = new int[2], textures = new int[2];
    private int width, height;
    private boolean failed;
    private final long started = System.nanoTime();
    public static FogModule module() {
        if (dev.vibe.module.impl.HypixelModule.visualsSuppressed()) return null;
        return Vibe.getInstance() == null || Vibe.getInstance().getModuleManager() == null ? null
                : Vibe.getInstance().getModuleManager().getModule(FogModule.class);
    }
    public static boolean replacesVanillaFog() {
        FogModule m = module(); return m != null && m.isEnabled() && !m.vanilla.isEnabled() && supported();
    }
    public static boolean supported() { return OpenGlHelper.framebufferSupported && GLContext.getCapabilities().OpenGL20; }
    public boolean hasFailed() { return failed; }
    public void renderMinecraft() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.getRenderViewEntity() == null) return;
        render(module(), .05f, mc.gameSettings.renderDistanceChunks * 16 * 2,
                mc.getRenderViewEntity().rotationYaw, mc.getRenderViewEntity().rotationPitch);
    }
    public void render(FogModule module, float near, float far, float yaw, float pitch) {
        if (module == null || !module.isEnabled() || failed || !supported()) return;
        viewport.clear(); GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);
        int x = viewport.get(0), y = viewport.get(1), w = viewport.get(2), h = viewport.get(3);
        if (w < 1 || h < 1) return;
        try (EffectState state = new EffectState()) {
            GL11.glDisable(GL11.GL_DEPTH_TEST); GL11.glDisable(GL11.GL_BLEND);
            prepare(w, h);
            // Allocation may bind a temporary FBO; capture from the original target.
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, state.destination());
            scene.capture(x, y, w, h, true);
            target(0); tint.bind(); texture(0, scene.color);
            tint.integer("Tex0", 0); tint.integer("RGBPuke", module.tint.is("Rainbow") ? 1 : 0);
            tint.vec3("Color", module.color.getRed()/255f, module.color.getGreen()/255f, module.color.getBlue()/255f);
            tint.scalar("Opacity", module.tint.is("Scene") ? 0 : module.tintOpacity.getFloat()/100);
            tint.vec2("SV", module.saturation.getFloat()/100, module.brightness.getFloat()/100);
            tint.scalar("Time", (System.nanoTime()-started)/1e9f * module.speed.getFloat());
            tint.scalar("Yaw", yaw); tint.scalar("Pitch", pitch); tint.integer("Alpha", 0); quad();
            int output = 0;
            if (module.blur.isEnabled()) {
                if (module.method.is("Gaussian")) {
                    float sigma = module.strength.getFloat();
                    gaussian.bind(); gaussian.integer("Tex0", 0); gaussian.integer("Alpha", 0);
                    gaussian.vec2("TexelSize", 1f/w, 1f/h); gaussian.integer("Support", (int)Math.ceil(sigma*3));
                    gaussian.integer("LinearSampling", 1);
                    gaussian.vec3("Gaussian", (float)(1/Math.sqrt(2*Math.PI)/sigma), (float)Math.exp(-.5/(sigma*sigma)), (float)Math.exp(-1/(sigma*sigma)));
                    target(1); texture(0, textures[0]); gaussian.vec2("Direction", 1, 0); quad();
                    target(0); texture(0, textures[1]); gaussian.vec2("Direction", 0, 1); quad();
                } else {
                    kawase.bind(); kawase.integer("Texture", 0); kawase.integer("Alpha", 0); kawase.vec2("TexelSize", 1f/w, 1f/h);
                    int passes = Math.min(8, 1 + module.strength.getInt()/3);
                    for (int i = 0; i < passes; i++) {
                        int next = 1-output; target(next); texture(0, textures[output]);
                        kawase.scalar("Size", (i+1)*module.strength.getFloat()/passes); quad(); output = next;
                    }
                }
            }
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, state.destination()); GL11.glViewport(x, y, w, h);
            GL11.glEnable(GL11.GL_BLEND); GL14.glBlendEquation(GL14.GL_FUNC_ADD);
            GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ZERO, GL11.GL_ONE);
            depth.bind(); texture(0, textures[output]); texture(1, scene.depth);
            depth.integer("Tex0", 0); depth.integer("Tex1", 1); depth.scalar("Near", near); depth.scalar("Far", far);
            depth.scalar("MinThreshold", module.start.getFloat()/far); depth.scalar("MaxThreshold", module.endDistance()/far);
            depth.scalar("Opacity", module.opacity.getFloat()/100); depth.integer("AffectSky", module.sky.isEnabled()?1:0); quad();
        } catch (Exception e) {
            failed = true; close(); LogManager.getLogger("Vibe").warn("Fog renderer unavailable", e);
        }
    }
    private void prepare(int w, int h) throws Exception {
        if (tint == null) {
            tint = new EffectProgram("fullscreen.vert", "Tint.frag");
            kawase = new EffectProgram("fullscreen.vert", "Kawase.frag");
            gaussian = new EffectProgram("fullscreen.vert", "Gaussian.frag");
            depth = new EffectProgram("fullscreen.vert", "Depth.frag");
        }
        if (width == w && height == h) return;
        releaseTargets(); width = w; height = h;
        for (int i = 0; i < 2; i++) {
            textures[i] = SceneTexture.allocate(w, h, false); buffers[i] = OpenGlHelper.glGenFramebuffers();
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, buffers[i]);
            OpenGlHelper.glFramebufferTexture2D(OpenGlHelper.GL_FRAMEBUFFER, OpenGlHelper.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, textures[i], 0);
            if (OpenGlHelper.glCheckFramebufferStatus(OpenGlHelper.GL_FRAMEBUFFER) != OpenGlHelper.GL_FRAMEBUFFER_COMPLETE)
                throw new IllegalStateException("Incomplete fog framebuffer");
        }
    }
    private void target(int i) { OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, buffers[i]); GL11.glViewport(0, 0, width, height); }
    public static void texture(int unit, int id) { GL13.glActiveTexture(GL13.GL_TEXTURE0+unit); GL11.glBindTexture(GL11.GL_TEXTURE_2D, id); }
    public static void quad() {
        GL11.glBegin(GL11.GL_QUADS);
        GL13.glMultiTexCoord2f(GL13.GL_TEXTURE0, 0, 0); GL11.glVertex2f(-1, -1);
        GL13.glMultiTexCoord2f(GL13.GL_TEXTURE0, 1, 0); GL11.glVertex2f(1, -1);
        GL13.glMultiTexCoord2f(GL13.GL_TEXTURE0, 1, 1); GL11.glVertex2f(1, 1);
        GL13.glMultiTexCoord2f(GL13.GL_TEXTURE0, 0, 1); GL11.glVertex2f(-1, 1);
        GL11.glEnd();
    }
    private void releaseTargets() {
        for (int i = 0; i < 2; i++) {
            if (buffers[i] != 0) OpenGlHelper.glDeleteFramebuffers(buffers[i]); if (textures[i] != 0) GL11.glDeleteTextures(textures[i]);
            buffers[i] = textures[i] = 0;
        }
        width = height = 0;
    }
    public void close() {
        scene.close(); releaseTargets();
        if (tint != null) tint.close(); if (kawase != null) kawase.close(); if (gaussian != null) gaussian.close(); if (depth != null) depth.close();
        tint = kawase = gaussian = depth = null;
    }
}

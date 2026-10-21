package dev.vibe.ui.render;

import dev.vibe.module.impl.visual.ItemGlowModule;
import dev.vibe.ui.effect.*;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

/** Reuses PhoneCanvas coverage: no extra phone render, scene copy, full-screen mask or depth attachment. */
final class PhoneItemGlow {
    private static final PhoneItemGlow INSTANCE = new PhoneItemGlow();
    private final FloatBuffer matrix = BufferUtils.createFloatBuffer(16);
    private final IntBuffer viewport = BufferUtils.createIntBuffer(16), clip = BufferUtils.createIntBuffer(16);
    private int horizontal, vertical, framebuffer, width, height;
    private EffectProgram blur, composite;
    private boolean failed;
    private int allocations;

    static void draw(ItemGlowModule module, int source, int left, int top, int sourceWidth, int sourceHeight) {
        if (INSTANCE.failed) return;
        INSTANCE.render(module, source, left, top, sourceWidth, sourceHeight);
    }
    static void closeActive() {
        if (INSTANCE.framebuffer != 0 || INSTANCE.blur != null)
            try (EffectState state = new EffectState()) { INSTANCE.close(); }
        INSTANCE.failed = false;
    }
    private void render(ItemGlowModule module, int source, int left, int top, int sourceWidth, int sourceHeight) {
        viewport.clear(); clip.clear();
        GL11.glGetInteger(GL11.GL_VIEWPORT, viewport); GL11.glGetInteger(GL11.GL_SCISSOR_BOX, clip);
        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        matrix.clear(); GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, matrix);
        float localScale = (float) Math.sqrt(matrix.get(0) * matrix.get(0) + matrix.get(1) * matrix.get(1));
        if (localScale < .0001F) return;
        float radius = (float) Math.ceil(module.radius.getFloat() / localScale * 4) / 4;
        int pad = (int) Math.ceil(radius * 1.25F + 4), extentWidth = sourceWidth + pad * 2, extentHeight = sourceHeight + pad * 2;
        int guiScale = new ScaledResolution(Minecraft.getMinecraft()).getScaleFactor();
        float support = module.quality.is("Performance") ? 4 : module.quality.is("High") ? 8 : 6;
        float divisor = Math.max(module.downsample() / (localScale * guiScale), radius / support);
        divisor = Math.max(divisor, Math.max(extentWidth, extentHeight) / (float) module.blurLimit());
        int bw = Math.max(1, Math.round(extentWidth / divisor)), bh = Math.max(1, Math.round(extentHeight / divisor));
        try (EffectState state = new EffectState()) {
            prepare(bw, bh);
            GL11.glDisable(GL11.GL_DEPTH_TEST); GL11.glDisable(GL11.GL_BLEND);
            for (int i = 0; i < 6; i++) GL11.glDisable(GL11.GL_CLIP_PLANE0 + i);
            blur.bind(); blur.integer("Source", 0); blur.integer("AlphaMask", 1);
            blur.vec2("MaskScale", extentWidth / (float) sourceWidth, extentHeight / (float) sourceHeight);
            blur.vec2("MaskOffset", -pad / (float) sourceWidth, -pad / (float) sourceHeight);
            blur.scalar("CoverageY", .25F / bh); blur.vec2("Step", radius / (6 * extentWidth), 0);
            target(horizontal); FogRenderer.texture(0, source); FogRenderer.quad();
            target(vertical); FogRenderer.texture(0, horizontal); blur.integer("AlphaMask", 0);
            blur.vec2("Step", 0, radius / (6 * extentHeight)); FogRenderer.quad();
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, state.destination());
            GL11.glViewport(viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3));
            if (scissor) { GL11.glEnable(GL11.GL_SCISSOR_TEST); GL11.glScissor(clip.get(0), clip.get(1), clip.get(2), clip.get(3)); }
            composite.bind(); FogRenderer.texture(0, source); FogRenderer.texture(1, vertical);
            composite.integer("Mask", 0); composite.integer("Halo", 1);
            composite.vec2("MaskScale", extentWidth / (float) sourceWidth, extentHeight / (float) sourceHeight);
            composite.vec2("MaskOffset", -pad / (float) sourceWidth, -pad / (float) sourceHeight);
            double seconds = module.seconds(); int first = module.colorAt(seconds, 0), second = module.colorAt(seconds, 1);
            composite.vec3("Primary", (first >> 16 & 255) / 255F, (first >> 8 & 255) / 255F, (first & 255) / 255F);
            composite.vec3("Secondary", (second >> 16 & 255) / 255F, (second >> 8 & 255) / 255F, (second & 255) / 255F);
            composite.vec2("ColorAlpha", (first >>> 24) / 255F, (second >>> 24) / 255F);
            composite.scalar("Strength", module.intensity.getFloat() * module.opacity.getFloat() * module.fadeAt(seconds));
            composite.scalar("Falloff", module.falloff.getFloat()); composite.scalar("InnerGlow", module.innerGlow.getFloat());
            GL11.glEnable(GL11.GL_BLEND); GL20.glBlendEquationSeparate(GL14.GL_FUNC_ADD, GL14.GL_FUNC_ADD);
            GL14.glBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_COLOR, GL11.GL_ZERO, GL11.GL_ONE);
            int x = left - pad, y = top - pad;
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2f(0, 1); GL11.glVertex2f(x, y);
            GL11.glTexCoord2f(0, 0); GL11.glVertex2f(x, y + extentHeight);
            GL11.glTexCoord2f(1, 0); GL11.glVertex2f(x + extentWidth, y + extentHeight);
            GL11.glTexCoord2f(1, 1); GL11.glVertex2f(x + extentWidth, y);
            GL11.glEnd();
        } catch (Exception failure) {
            failed = true;
            try (EffectState state = new EffectState()) { close(); }
            org.apache.logging.log4j.LogManager.getLogger("Vibe").warn("Phone ItemGlow unavailable; retaining the original phone", failure);
        }
    }
    private void prepare(int w, int h) throws Exception {
        if (blur == null) {
            blur = new EffectProgram("fullscreen.vert", "ItemGlowBlur.frag");
            composite = new EffectProgram("ItemGlowPhone.vert", "ItemGlowComposite.frag");
        }
        if (width == w && height == h && horizontal != 0) return;
        releaseTargets(); width = w; height = h;
        horizontal = SceneTexture.allocate(w, h, false); vertical = SceneTexture.allocate(w, h, false);
        framebuffer = OpenGlHelper.glGenFramebuffers(); allocations++; target(horizontal);
        if (OpenGlHelper.glCheckFramebufferStatus(OpenGlHelper.GL_FRAMEBUFFER) != OpenGlHelper.GL_FRAMEBUFFER_COMPLETE)
            throw new IllegalStateException("Incomplete Phone glow framebuffer");
    }
    private void target(int texture) {
        OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, framebuffer);
        OpenGlHelper.glFramebufferTexture2D(OpenGlHelper.GL_FRAMEBUFFER, OpenGlHelper.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, texture, 0);
        GL11.glViewport(0, 0, width, height);
    }
    private void releaseTargets() {
        if (horizontal != 0) GL11.glDeleteTextures(horizontal); if (vertical != 0) GL11.glDeleteTextures(vertical);
        if (framebuffer != 0) OpenGlHelper.glDeleteFramebuffers(framebuffer);
        horizontal = vertical = framebuffer = width = height = 0;
    }
    private void close() { releaseTargets(); if (blur != null) blur.close(); if (composite != null) composite.close(); blur = composite = null; }
}

package dev.vibe.ui.render;

import dev.vibe.Vibe;
import dev.vibe.module.impl.meme.HypixelModule;
import dev.vibe.module.impl.visual.ItemGlowModule;
import dev.vibe.ui.effect.EffectProgram;
import dev.vibe.ui.effect.EffectState;
import dev.vibe.ui.effect.FogRenderer;
import dev.vibe.ui.effect.SceneTexture;
import java.nio.IntBuffer;
import java.nio.ByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

/** One geometry draw writes the real scene and a coverage attachment together; only the mask is blurred. */
public final class ItemGlowRenderer {
    private static final ItemGlowRenderer INSTANCE = new ItemGlowRenderer();
    private static volatile boolean releaseRequested;
    private final IntBuffer viewport = BufferUtils.createIntBuffer(16);
    private final IntBuffer clip = BufferUtils.createIntBuffer(16);
    private final IntBuffer bothBuffers = BufferUtils.createIntBuffer(2), maskBuffer = BufferUtils.createIntBuffer(1), singleBuffer = BufferUtils.createIntBuffer(1);
    private final ByteBuffer writeMask = BufferUtils.createByteBuffer(16);
    private int capture, horizontal, vertical, framebuffer, width, height, blurWidth, blurHeight;
    private int allocations;
    private EffectProgram blur, composite;
    private boolean failed, drawing;

    private ItemGlowRenderer() {
        bothBuffers.put(GL30.GL_COLOR_ATTACHMENT0).put(GL30.GL_COLOR_ATTACHMENT1).flip();
        maskBuffer.put(GL30.GL_COLOR_ATTACHMENT1).flip();
        singleBuffer.put(GL30.GL_COLOR_ATTACHMENT0).flip();
    }

    public static void held(Runnable geometry) { render(geometry); }
    public static void phone(int texture, int left, int top, int width, int height) {
        if (releaseRequested) closeActive();
        Vibe vibe = Vibe.getInstance();
        ItemGlowModule module = vibe == null || vibe.getModuleManager() == null ? null : vibe.getModuleManager().getModule(ItemGlowModule.class);
        if (module != null && module.isEnabled() && module.phone.isEnabled() && module.opacity.getFloat() > 0
                && module.intensity.getFloat() > 0 && !HypixelModule.visualsSuppressed())
            PhoneItemGlow.draw(module, texture, left, top, width, height);
    }
    public static void requestRelease() { releaseRequested = true; }
    public static void closeActive() {
        if (INSTANCE.capture != 0 || INSTANCE.blur != null || INSTANCE.composite != null)
            try (EffectState state = new EffectState()) { INSTANCE.close(); }
        INSTANCE.failed = false;
        PhoneItemGlow.closeActive();
        releaseRequested = false;
    }

    private static void render(Runnable geometry) {
        if (releaseRequested) closeActive();
        Vibe vibe = Vibe.getInstance();
        ItemGlowModule module = vibe == null || vibe.getModuleManager() == null ? null : vibe.getModuleManager().getModule(ItemGlowModule.class);
        Minecraft mc = Minecraft.getMinecraft();
        boolean selected = module != null && module.isEnabled() && module.heldItem.isEnabled();
        if (selected) selected = mc.thePlayer != null && mc.gameSettings.thirdPersonView == 0
                && !mc.gameSettings.hideGUI && (module.emptyHand.isEnabled() || mc.thePlayer.getHeldItem() != null);
        if (!selected || module.intensity.getFloat() <= 0 || module.opacity.getFloat() <= 0 || HypixelModule.visualsSuppressed()
                || !OpenGlHelper.isFramebufferEnabled() || INSTANCE.failed || INSTANCE.drawing) {
            geometry.run(); return;
        }
        INSTANCE.draw(module, geometry);
    }

    private void draw(ItemGlowModule module, Runnable geometry) {
        Minecraft mc = Minecraft.getMinecraft();
        Framebuffer main = mc.getFramebuffer();
        int destination = GL11.glGetInteger(0x8CA6);
        viewport.clear(); GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);
        int w = viewport.get(2), h = viewport.get(3);
        float radius = module.radius.getFloat() * new ScaledResolution(mc).getScaleFactor();
        // Other renderers can own a differently sized/depth-tested target. Leave their pass alone.
        if (main == null || main.framebufferObject != destination || viewport.get(0) != 0 || viewport.get(1) != 0
                || w != main.framebufferWidth || h != main.framebufferHeight || w <= 0 || h <= 0
                || GL11.glIsEnabled(GL11.GL_STENCIL_TEST)) {
            geometry.run(); return;
        }
        try (EffectState state = new EffectState()) {
            prepare(w, h, module, radius);
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, framebuffer);
            OpenGlHelper.glFramebufferTexture2D(OpenGlHelper.GL_FRAMEBUFFER, OpenGlHelper.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, main.framebufferTexture, 0);
            OpenGlHelper.glFramebufferTexture2D(OpenGlHelper.GL_FRAMEBUFFER, OpenGlHelper.GL_COLOR_ATTACHMENT0 + 1, GL11.GL_TEXTURE_2D, capture, 0);
            OpenGlHelper.glFramebufferRenderbuffer(OpenGlHelper.GL_FRAMEBUFFER, OpenGlHelper.GL_DEPTH_ATTACHMENT, OpenGlHelper.GL_RENDERBUFFER, Math.max(0, main.depthBuffer));
            GL20.glDrawBuffers(bothBuffers);
            if (OpenGlHelper.glCheckFramebufferStatus(OpenGlHelper.GL_FRAMEBUFFER) != OpenGlHelper.GL_FRAMEBUFFER_COMPLETE)
                throw new IllegalStateException("Incomplete ItemGlow capture target");
            // Clear only the coverage attachment. The original scene/depth remain untouched.
            // Fixed-function/gl_FragColor output broadcasts to both buffers in this GL compatibility pipeline.
            GL20.glDrawBuffers(maskBuffer);
            GL11.glClearColor(0, 0, 0, 0); GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
            GL20.glDrawBuffers(bothBuffers);
        } catch (Exception failure) {
            unavailable(failure); geometry.run(); return;
        }
        OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, framebuffer);
        drawing = true;
        try { geometry.run(); }
        finally {
            drawing = false;
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, destination);
            GL11.glViewport(0, 0, w, h);
        }
        // Geometry runs outside EffectState so Minecraft's cached state stays synchronized.
        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        clip.clear(); GL11.glGetInteger(GL11.GL_SCISSOR_BOX, clip);
        writeMask.clear(); GL11.glGetBoolean(GL11.GL_COLOR_WRITEMASK, writeMask);
        try (EffectState state = new EffectState()) {
            GL11.glDisable(GL11.GL_DEPTH_TEST); GL11.glDisable(GL11.GL_BLEND);
            for (int i = 0; i < 6; i++) GL11.glDisable(GL11.GL_CLIP_PLANE0 + i);
            blur.bind(); blur.integer("Source", 0);
            blur.vec2("MaskScale", 1, 1); blur.vec2("MaskOffset", 0, 0);
            target(horizontal, -1); GL11.glViewport(0, 0, blurWidth, blurHeight);
            FogRenderer.texture(0, capture); blur.integer("AlphaMask", 1); blur.scalar("CoverageY", .25F / blurHeight);
            blur.vec2("Step", radius / (6 * width), 0); FogRenderer.quad();
            target(vertical, -1); FogRenderer.texture(0, horizontal);
            blur.integer("AlphaMask", 0); blur.vec2("Step", 0, radius / (6 * height)); FogRenderer.quad();
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, destination); GL11.glViewport(0, 0, width, height);
            if (scissor) { GL11.glEnable(GL11.GL_SCISSOR_TEST); GL11.glScissor(clip.get(0), clip.get(1), clip.get(2), clip.get(3)); }
            GL11.glColorMask(writeMask.get(0) != 0, writeMask.get(1) != 0, writeMask.get(2) != 0, writeMask.get(3) != 0);
            GL11.glEnable(GL11.GL_BLEND);
            GL20.glBlendEquationSeparate(GL14.GL_FUNC_ADD, GL14.GL_FUNC_ADD);
            GL14.glBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_COLOR, GL11.GL_ZERO, GL11.GL_ONE);
            composite.bind(); FogRenderer.texture(0, capture); FogRenderer.texture(1, vertical);
            composite.integer("Mask", 0); composite.integer("Halo", 1);
            composite.vec2("MaskScale", 1, 1); composite.vec2("MaskOffset", 0, 0);
            double seconds = module.seconds();
            colors(module.colorAt(seconds, 0), module.colorAt(seconds, 1));
            composite.scalar("Strength", module.intensity.getFloat() * module.opacity.getFloat() * module.fadeAt(seconds));
            composite.scalar("Falloff", module.falloff.getFloat()); composite.scalar("InnerGlow", module.innerGlow.getFloat());
            FogRenderer.quad();
        } catch (Exception failure) {
            // The real source was already drawn into Minecraft's own color/depth attachments.
            unavailable(failure);
        }
    }

    private void colors(int first, int second) {
        composite.vec3("Primary", (first >> 16 & 255) / 255F, (first >> 8 & 255) / 255F, (first & 255) / 255F);
        composite.vec3("Secondary", (second >> 16 & 255) / 255F, (second >> 8 & 255) / 255F, (second & 255) / 255F);
        composite.vec2("ColorAlpha", (first >>> 24) / 255F, (second >>> 24) / 255F);
    }
    private void prepare(int w, int h, ItemGlowModule module, float radius) throws Exception {
        if (blur == null) {
            if (GL11.glGetInteger(GL20.GL_MAX_DRAW_BUFFERS) < 2) throw new IllegalStateException("ItemGlow requires two draw buffers");
            blur = new EffectProgram("fullscreen.vert", "ItemGlowBlur.frag");
            composite = new EffectProgram("fullscreen.vert", "ItemGlowComposite.frag");
        }
        float divisor = Math.max(module.downsample(), Math.max(w, h) / (float) module.blurLimit());
        // Keep taps close enough to filter continuously instead of showing spaced rings at wide radii.
        float support = module.quality.is("Performance") ? 4 : module.quality.is("High") ? 8 : 6;
        divisor = Math.max(divisor, radius / support);
        int bw = Math.max(1, Math.round(w / divisor)), bh = Math.max(1, Math.round(h / divisor));
        if (width == w && height == h && blurWidth == bw && blurHeight == bh && capture != 0) return;
        releaseTargets(); width = w; height = h; blurWidth = bw; blurHeight = bh;
        capture = SceneTexture.allocate(w, h, false);
        horizontal = SceneTexture.allocate(bw, bh, false); vertical = SceneTexture.allocate(bw, bh, false);
        framebuffer = OpenGlHelper.glGenFramebuffers(); allocations++;
        target(horizontal, -1);
        if (OpenGlHelper.glCheckFramebufferStatus(OpenGlHelper.GL_FRAMEBUFFER) != OpenGlHelper.GL_FRAMEBUFFER_COMPLETE)
            throw new IllegalStateException("Incomplete ItemGlow blur target");
    }
    private void target(int texture, int depth) {
        OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, framebuffer);
        OpenGlHelper.glFramebufferTexture2D(OpenGlHelper.GL_FRAMEBUFFER, OpenGlHelper.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, texture, 0);
        OpenGlHelper.glFramebufferTexture2D(OpenGlHelper.GL_FRAMEBUFFER, OpenGlHelper.GL_COLOR_ATTACHMENT0 + 1, GL11.GL_TEXTURE_2D, 0, 0);
        OpenGlHelper.glFramebufferRenderbuffer(OpenGlHelper.GL_FRAMEBUFFER, OpenGlHelper.GL_DEPTH_ATTACHMENT, OpenGlHelper.GL_RENDERBUFFER, Math.max(0, depth));
        GL20.glDrawBuffers(singleBuffer);
    }
    private void unavailable(Exception failure) {
        failed = true;
        try (EffectState state = new EffectState()) { close(); }
        org.apache.logging.log4j.LogManager.getLogger("Vibe").warn("ItemGlow unavailable; retaining normal item rendering", failure);
    }
    private void releaseTargets() {
        if (capture != 0) GL11.glDeleteTextures(capture);
        if (horizontal != 0) GL11.glDeleteTextures(horizontal);
        if (vertical != 0) GL11.glDeleteTextures(vertical);
        if (framebuffer != 0) OpenGlHelper.glDeleteFramebuffers(framebuffer);
        capture = horizontal = vertical = framebuffer = width = height = blurWidth = blurHeight = 0;
    }
    private void close() {
        releaseTargets();
        if (blur != null) blur.close(); if (composite != null) composite.close();
        blur = composite = null;
    }
}

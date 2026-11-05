package dev.vibe.ui.effect;

import dev.vibe.ui.menu.LavaLampMotion;
import dev.vibe.ui.menu.MenuGlass;
import dev.vibe.ui.menu.MenuPalette;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import net.minecraft.client.Minecraft;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

/** The menu's luminous wax, composited over a refractive HUD backdrop. */
public final class LavaLampRenderer implements AutoCloseable {
    private static final long EPOCH = System.nanoTime();
    private final LiquidGlassRenderer glass;
    private final FloatBuffer model = BufferUtils.createFloatBuffer(16);
    private final FloatBuffer projection = BufferUtils.createFloatBuffer(16);
    private final IntBuffer viewport = BufferUtils.createIntBuffer(16);
    private final IntBuffer clip = BufferUtils.createIntBuffer(16);
    private final FloatBuffer drops = BufferUtils.createFloatBuffer(LavaLampMotion.DROPS * 4);
    private final FloatBuffer forms = BufferUtils.createFloatBuffer(LavaLampMotion.DROPS * 4);
    private EffectProgram program;
    private boolean failed;
    private long sampled = Long.MIN_VALUE;

    public LavaLampRenderer(LiquidGlassRenderer glass) { this.glass = glass; }

    /** Bounds are in the caller's local GUI space, including editor, scale and pop animations. */
    public boolean draw(float x, float y, float width, float height, float radius,
                        float blur, float refraction, float opacity, int tint) {
        return draw(x, y, width, height, radius, blur, refraction, opacity, tint,
                (System.nanoTime() - EPOCH) / 1e9, MenuGlass.currentPalette());
    }

    boolean draw(float x, float y, float width, float height, float radius, float blur, float refraction,
                 float opacity, int tint, double time, MenuPalette palette) {
        if (width <= 0 || height <= 0 || opacity <= 0) return true;
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, model);
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, projection);
        GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);
        float x1 = project(x, 0), x2 = project(x + width, 0);
        float y1 = project(y, 1), y2 = project(y + height, 1);
        float left = Math.min(x1, x2), bottom = Math.min(y1, y2);
        float w = Math.abs(x2 - x1), h = Math.abs(y2 - y1);
        if (w < .1F || h < .1F) return true;
        float scale = Math.min(w / width, h / height);
        float r = Math.min(radius * scale, Math.min(w, h) / 2);
        int screenHeight = Minecraft.getMinecraft().displayHeight;
        int color = dev.vibe.ui.RenderUtils.blend(palette.surface, tint | 0xFF000000, .12F);
        boolean drawn = glass.drawPixels(left, screenHeight - bottom - h, left + w, screenHeight - bottom,
                r, scale, blur, refraction, opacity, color);
        if (failed) return drawn;

        int a = Math.max(0, (int) Math.floor(left)), b = Math.min(Minecraft.getMinecraft().displayWidth, (int) Math.ceil(left + w));
        int c = Math.max(0, (int) Math.floor(bottom)), d = Math.min(screenHeight, (int) Math.ceil(bottom + h));
        if (GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)) {
            GL11.glGetInteger(GL11.GL_SCISSOR_BOX, clip);
            a = Math.max(a, clip.get(0)); b = Math.min(b, clip.get(0) + clip.get(2));
            c = Math.max(c, clip.get(1)); d = Math.min(d, clip.get(1) + clip.get(3));
        }
        if (b <= a || d <= c) return true;
        try (EffectState state = new EffectState()) {
            if (program == null) program = new EffectProgram("fullscreen.vert", "MenuGlass.frag");
            program.bind();
            program.vec2("Origin", left, bottom); program.vec2("Size", w, h);
            program.vec2("LavaOrigin", left, bottom); program.vec2("LavaSize", w, h);
            program.scalar("Radius", r); program.scalar("Scale", scale); program.scalar("Time", (float) time);
            program.scalar("Window", 1); program.scalar("Hover", 0); program.scalar("Emphasis", 0);
            program.scalar("Fade", 1 - Math.max(0, Math.min(1, opacity)));
            rgb("Accent", palette.accent); rgb("Wax", palette.lava); rgb("Light", palette.light);
            long sample = (long) (time * 60);
            if (sample != sampled) {
                LavaLampMotion.sample(time, drops, forms);
                sampled = sample;
            }
            GL11.glViewport(0, 0, Minecraft.getMinecraft().displayWidth, screenHeight);
            GL11.glDisable(GL11.GL_DEPTH_TEST); GL11.glEnable(GL11.GL_BLEND); GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL14.glBlendEquation(GL14.GL_FUNC_ADD);
            GL14.glBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            program.scalar("Pass", 1);
            region(a, c, b, Math.min(d, (int) Math.ceil(bottom + h * .29F)));
            region(a, Math.max(c, (int) Math.floor(bottom + h * .75F)), b, d);
            program.scalar("Pass", 2);
            for (int i = 0; i < LavaLampMotion.DROPS; i++) {
                float dx = drops.get(i * 4), dy = drops.get(i * 4 + 1), size = drops.get(i * 4 + 2), stretch = drops.get(i * 4 + 3);
                if (size <= 0) continue;
                float longest = Math.max(stretch, forms.get(i * 4)), shortest = Math.min(stretch, forms.get(i * 4));
                float extent = size * h * (longest * 1.8F + 1) + h * .052F * 2.85F * longest / shortest;
                float cx = left + dx * w, cy = bottom + dy * h;
                program.vec4("Drop", dx, dy, size, stretch);
                program.vec4("Form", forms.get(i * 4), forms.get(i * 4 + 1), forms.get(i * 4 + 2), forms.get(i * 4 + 3));
                program.scalar("Depth", LavaLampMotion.depth(i));
                region(Math.max(a, (int) Math.floor(cx - extent)), Math.max(c, (int) Math.floor(cy - extent)),
                        Math.min(b, (int) Math.ceil(cx + extent)), Math.min(d, (int) Math.ceil(cy + extent)));
            }
            GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            program.scalar("Pass", 3);
            region(a, c, b, d);
            return true;
        } catch (Exception failure) {
            failed = true;
            close();
            org.apache.logging.log4j.LogManager.getLogger("Vibe").warn("HUD lava lamp unavailable", failure);
            return drawn;
        }
    }

    private float project(float coordinate, int axis) {
        float value = model.get(axis * 5) * coordinate + model.get(12 + axis);
        float normalized = projection.get(axis * 5) * value + projection.get(12 + axis);
        float homogeneous = projection.get(15) + projection.get(11) * model.get(14);
        return viewport.get(axis) + (normalized / homogeneous + 1) * viewport.get(axis + 2) * .5F;
    }

    private void region(int left, int bottom, int right, int top) {
        if (right <= left || top <= bottom) return;
        GL11.glScissor(left, bottom, right - left, top - bottom);
        FogRenderer.quad();
    }

    private void rgb(String name, int color) {
        program.vec3(name, (color >> 16 & 255) / 255F, (color >> 8 & 255) / 255F, (color & 255) / 255F);
    }

    @Override public void close() {
        if (program != null) program.close();
        program = null;
    }
}

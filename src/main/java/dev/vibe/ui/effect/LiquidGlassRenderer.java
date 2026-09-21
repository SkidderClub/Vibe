package dev.vibe.ui.effect;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.opengl.GL11;

/** Clear HUD glass with a curved, refractive rim in physical framebuffer pixels. */
public final class LiquidGlassRenderer implements AutoCloseable {
    private final SceneTexture scene = new SceneTexture();
    private EffectProgram program;
    private boolean failed;

    public boolean draw(int left, int top, int right, int bottom, float radius) {
        return draw(left, top, right, bottom, radius, 2.0F, 4.0F, .72F, 0xFFFFFFFF);
    }

    public boolean draw(int left, int top, int right, int bottom, float radius, float blur, float refraction, float opacity) {
        return draw(left, top, right, bottom, radius, blur, refraction, opacity, 0xFFFFFFFF);
    }

    public boolean draw(int left, int top, int right, int bottom, float radius, float blur, float refraction, float opacity, int tint) {
        if (failed || right <= left || bottom <= top) return false;
        Minecraft minecraft = Minecraft.getMinecraft();
        ScaledResolution scaled = new ScaledResolution(minecraft);
        int scale = scaled.getScaleFactor();
        try (EffectState state = new EffectState()) {
            int width = minecraft.displayWidth, height = minecraft.displayHeight;
            scene.capture(0, 0, width, height, false);
            if (program == null) program = new EffectProgram("fullscreen.vert", "LiquidGlass.frag");
            program.bind();
            FogRenderer.texture(0, scene.color);
            program.integer("Tex0", 0);
            program.vec2("Origin", left * scale, height - bottom * scale);
            program.vec2("Size", (right - left) * scale, (bottom - top) * scale);
            program.scalar("Radius", Math.max(0, Math.min(radius, Math.min(right - left, bottom - top) * .5F)) * scale);
            program.scalar("Scale", scale);
            program.vec2("Texel", 1.0F / width, 1.0F / height);
            program.scalar("Blur", Math.max(0.0F, Math.min(8.0F, blur)));
            program.scalar("Refraction", Math.max(0.0F, Math.min(10.0F, refraction)));
            program.scalar("Opacity", Math.max(.15F, Math.min(1.0F, opacity)));
            program.vec3("Tint", ((tint >> 16) & 255) / 255.0F, ((tint >> 8) & 255) / 255.0F, (tint & 255) / 255.0F);
            GL11.glViewport(0, 0, width, height);
            // The vertex shader already uses clip coordinates. Restrict shading
            // to this pane instead of running the glass shader over the screen.
            int x = Math.max(0, left * scale), y = Math.max(0, height - bottom * scale);
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor(x, y, Math.max(0, Math.min(width, right * scale) - x),
                    Math.max(0, Math.min(height, height - top * scale) - y));
            GL11.glDisable(GL11.GL_DEPTH_TEST); GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            FogRenderer.quad();
            return true;
        } catch (Exception failure) {
            failed = true;
            close();
            org.apache.logging.log4j.LogManager.getLogger("Vibe").warn("LiquidGlass renderer unavailable", failure);
            return false;
        }
    }

    @Override public void close() { scene.close(); if (program != null) program.close(); program = null; }
}

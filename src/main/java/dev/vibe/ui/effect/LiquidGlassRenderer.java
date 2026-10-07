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
            float blurStrength = Math.max(0.0F, Math.min(8.0F, blur));
            float refractionStrength = Math.max(0.0F, Math.min(10.0F, refraction));
            // The vertex shader already uses clip coordinates. Restrict shading
            // to this pane instead of running the glass shader over the screen.
            int x = Math.max(0, left * scale), y = Math.max(0, height - bottom * scale);
            int scissorWidth = Math.max(0, Math.min(width, right * scale) - x);
            int scissorHeight = Math.max(0, Math.min(height, height - top * scale) - y);
            captureSampledRegion(width, height, x, y, scissorWidth, scissorHeight, scale, blurStrength, refractionStrength);
            if (program == null) program = new EffectProgram("fullscreen.vert", "LiquidGlass.frag");
            program.bind();
            FogRenderer.texture(0, scene.color);
            program.integer("Tex0", 0);
            program.vec2("Origin", left * scale, height - bottom * scale);
            program.vec2("Size", (right - left) * scale, (bottom - top) * scale);
            program.scalar("Radius", Math.max(0, Math.min(radius, Math.min(right - left, bottom - top) * .5F)) * scale);
            program.scalar("Scale", scale);
            program.vec2("Texel", 1.0F / width, 1.0F / height);
            program.scalar("Blur", blurStrength);
            program.scalar("Refraction", refractionStrength);
            program.scalar("Opacity", Math.max(.15F, Math.min(1.0F, opacity)));
            program.vec3("Tint", ((tint >> 16) & 255) / 255.0F, ((tint >> 8) & 255) / 255.0F, (tint & 255) / 255.0F);
            GL11.glViewport(0, 0, width, height);
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor(x, y, scissorWidth, scissorHeight);
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

    /**
     * LiquidGlass.frag only shades pixels inside the scissor box. Each one reads the scene at
     * most Refraction * Scale * 2.8 pixels away, plus Blur * Scale for the tent taps and one
     * texel of linear filtering, and points beyond the screen clamp to its edge. Copying that
     * reach (with a spare pixel) gives the shader exactly the pixels a full-screen copy would,
     * while each pane no longer copies the whole framebuffer.
     */
    private void captureSampledRegion(int width, int height, int x, int y, int scissorWidth, int scissorHeight,
                                      int scale, float blur, float refraction) {
        float reach = refraction * scale * 2.8F + blur * scale;
        if (!(reach >= 0.0F && reach < width + height)) {
            scene.capture(0, 0, width, height, false);
            return;
        }
        int margin = (int) Math.ceil(reach) + 2;
        int regionLeft = Math.max(0, x - margin), regionBottom = Math.max(0, y - margin);
        int regionRight = Math.min(width, x + scissorWidth + margin), regionTop = Math.min(height, y + scissorHeight + margin);
        scene.captureRegion(width, height, regionLeft, regionBottom,
                scissorWidth > 0 && scissorHeight > 0 ? regionRight - regionLeft : 0, regionTop - regionBottom);
    }

    @Override public void close() { scene.close(); if (program != null) program.close(); program = null; }
}

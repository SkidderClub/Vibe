package dev.vibe.ui.effect;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

/** Torus-effect shader infrastructure applied to a refractive, pixel-perfect HUD glass mask. */
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
            float originX = left * scale / (float) width;
            float originY = 1.0F - bottom * scale / (float) height;
            float sizeX = (right - left) * scale / (float) width;
            float sizeY = (bottom - top) * scale / (float) height;
            program.bind();
            FogRenderer.texture(0, scene.color);
            program.integer("Tex0", 0);
            program.vec2("Origin", originX, originY);
            program.vec2("Size", sizeX, sizeY);
            program.scalar("Radius", Math.min(radius * scale / (float) height, Math.min(sizeX, sizeY) * .5F));
            program.vec2("Texel", 1.0F / width, 1.0F / height);
            program.scalar("Time", (float) ((System.nanoTime() / 1000000000L) % 10000L));
            program.scalar("Blur", Math.max(0.0F, Math.min(8.0F, blur)));
            program.scalar("Refraction", Math.max(0.0F, Math.min(10.0F, refraction)));
            program.scalar("Opacity", Math.max(.15F, Math.min(1.0F, opacity)));
            program.vec3("Tint", ((tint >> 16) & 255) / 255.0F, ((tint >> 8) & 255) / 255.0F, (tint & 255) / 255.0F);
            GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glPushMatrix(); GL11.glLoadIdentity();
            GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glPushMatrix(); GL11.glLoadIdentity();
            try {
                GL11.glDisable(GL11.GL_DEPTH_TEST); GL11.glEnable(GL11.GL_BLEND);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                FogRenderer.quad();
            } finally {
                GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glPopMatrix();
                GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glPopMatrix();
                GL11.glMatrixMode(GL11.GL_MODELVIEW);
            }
            return true;
        } catch (Exception ignored) {
            failed = true;
            close();
            return false;
        }
    }

    @Override public void close() { scene.close(); if (program != null) program.close(); program = null; }
}

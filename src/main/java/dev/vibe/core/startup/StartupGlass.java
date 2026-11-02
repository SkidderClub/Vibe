package dev.vibe.core.startup;

import dev.vibe.ui.effect.EffectProgram;
import dev.vibe.ui.menu.LavaLampMotion;
import dev.vibe.ui.menu.MenuPalette;
import java.awt.image.BufferedImage;
import java.nio.FloatBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

/** The menu's actual glass/wax shader, without Minecraft framebuffers or resource initialization. */
final class StartupGlass implements AutoCloseable {
    private final MenuPalette palette;
    private final FloatBuffer drops = BufferUtils.createFloatBuffer(LavaLampMotion.DROPS * 4);
    private final FloatBuffer forms = BufferUtils.createFloatBuffer(LavaLampMotion.DROPS * 4);
    private EffectProgram program;
    private int backdrop, backdropWidth, backdropHeight;
    private boolean failed;
    private long started = -1;

    StartupGlass(MenuPalette palette) { this.palette = palette; }

    void draw(int width, int height, float scale, float x, float y, float w, float h, long millis) {
        if (started < 0) started = millis;
        if (backdrop == 0 || backdropWidth != width || backdropHeight != height) createBackdrop(width, height);
        GL11.glEnable(GL11.GL_TEXTURE_2D); GL11.glBindTexture(GL11.GL_TEXTURE_2D, backdrop);
        GL11.glColor4f(1, 1, 1, 1);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0, 1); GL11.glVertex2f(0, 0);
        GL11.glTexCoord2f(0, 0); GL11.glVertex2f(0, height / scale);
        GL11.glTexCoord2f(1, 0); GL11.glVertex2f(width / scale, height / scale);
        GL11.glTexCoord2f(1, 1); GL11.glVertex2f(width / scale, 0); GL11.glEnd();
        if (!failed && GLContext.getCapabilities().OpenGL20) {
            try {
                if (program == null) program = new EffectProgram("fullscreen.vert", "MenuGlass.frag");
                lamp(width, height, scale, x * scale, height - (y + h) * scale, w * scale, h * scale,
                        Math.max(0, millis - started) / 1000F);
                return;
            } catch (Exception | LinkageError error) {
                failed = true;
                System.err.println("[Vibe] Startup glass shader unavailable; using the themed fallback: " + error);
                if (program != null) { program.close(); program = null; }
            } finally {
                GL20.glUseProgram(0); GL11.glDisable(GL11.GL_SCISSOR_TEST);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            }
        }
        rounded(x, y, w, h, 22, 0xD0000000 | palette.surface & 0xFFFFFF);
    }

    private void lamp(int width, int height, float scale, float x, float y, float w, float h, float time) {
        program.bind(); program.integer("Scene", 0);
        program.vec2("Resolution", width, height); program.vec2("Origin", x, y); program.vec2("Size", w, h);
        program.vec2("LavaOrigin", x, y); program.vec2("LavaSize", w, h);
        program.scalar("Radius", Math.min(24 * scale, Math.min(w, h) * .5F)); program.scalar("Scale", scale);
        program.scalar("Time", time); program.scalar("Window", 1); program.scalar("Emphasis", 0);
        program.scalar("Hover", 0); program.vec2("Pointer", .5F, .5F);
        rgb("Tint", palette.background); rgb("Accent", palette.accent); rgb("Wax", palette.lava); rgb("Light", palette.light);
        LavaLampMotion.sample(time, drops, forms);
        int left = Math.max(0, (int)Math.floor(x - 12 * scale)), right = Math.min(width, (int)Math.ceil(x + w + 12 * scale));
        int bottom = Math.max(0, (int)Math.floor(y - 12 * scale)), top = Math.min(height, (int)Math.ceil(y + h + 12 * scale));
        GL11.glEnable(GL11.GL_SCISSOR_TEST); GL11.glEnable(GL11.GL_BLEND);
        GL14.glBlendEquation(GL14.GL_FUNC_ADD);
        GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        pass(0, left, bottom, right, top);
        GL14.glBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        pass(1, left, bottom, right, Math.min(top, (int)Math.ceil(y + h * .29F)));
        pass(1, left, Math.max(bottom, (int)Math.floor(y + h * .75F)), right, top);
        for (int i = 0; i < LavaLampMotion.DROPS; i++) {
            float radius = drops.get(i * 4 + 2); if (radius <= 0) continue;
            float stretch = drops.get(i * 4 + 3), longest = Math.max(stretch, forms.get(i * 4)), shortest = Math.min(stretch, forms.get(i * 4));
            float extent = radius * h * (longest * 1.8F + 1) + h * .052F * 2.85F * longest / shortest;
            float cx = x + drops.get(i * 4) * w, cy = y + drops.get(i * 4 + 1) * h;
            program.vec4("Drop", drops.get(i * 4), drops.get(i * 4 + 1), radius, stretch);
            program.vec4("Form", forms.get(i * 4), forms.get(i * 4 + 1), forms.get(i * 4 + 2), forms.get(i * 4 + 3));
            program.scalar("Depth", LavaLampMotion.depth(i));
            pass(2, Math.max(left, (int)Math.floor(cx - extent)), Math.max(bottom, (int)Math.floor(cy - extent)),
                    Math.min(right, (int)Math.ceil(cx + extent)), Math.min(top, (int)Math.ceil(cy + extent)));
        }
        GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        pass(3, left, bottom, right, top);
    }

    private void pass(int pass, int left, int bottom, int right, int top) {
        if (right <= left || top <= bottom) return;
        program.scalar("Pass", pass); GL11.glScissor(left, bottom, right - left, top - bottom);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(-1, -1); GL11.glVertex2f(1, -1); GL11.glVertex2f(1, 1); GL11.glVertex2f(-1, 1); GL11.glEnd();
    }

    private void rgb(String name, int color) { program.vec3(name, (color >> 16 & 255) / 255F, (color >> 8 & 255) / 255F, (color & 255) / 255F); }

    /** A small, already diffuse backdrop: no full-size capture, blur passes or Minecraft FBOs at startup. */
    private void createBackdrop(int width, int height) {
        BufferedImage image = new BufferedImage(192, 128, BufferedImage.TYPE_INT_ARGB);
        double aspect = width / (double)height;
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
            double px = (x / 191D - .5) * aspect, py = y / 127D - .5;
            double a = Math.exp(-((px + .48) * (px + .48) + (py - .32) * (py - .32)) * 3.8);
            double b = Math.exp(-((px - .55) * (px - .55) + (py + .4) * (py + .4)) * 4.5);
            double haze = .025 + a * .14 + b * .095;
            int r = channel(palette.background, palette.accent, 16, haze);
            int g = channel(palette.background, palette.accent, 8, haze);
            int blue = channel(palette.background, palette.accent, 0, haze);
            image.setRGB(x, y, 0xFF000000 | r << 16 | g << 8 | blue);
        }
        if (backdrop != 0) GL11.glDeleteTextures(backdrop);
        backdrop = GhostArt.upload(image); backdropWidth = width; backdropHeight = height;
    }
    private static int channel(int base, int accent, int shift, double haze) {
        return Math.min(255, (int)((base >> shift & 255) * .72 + (accent >> shift & 255) * haze));
    }

    static void rounded(float x, float y, float width, float height, float radius, int color) {
        radius = Math.min(radius, Math.min(width, height) * .5F);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f((color >> 16 & 255) / 255F, (color >> 8 & 255) / 255F, (color & 255) / 255F, (color >>> 24) / 255F);
        GL11.glBegin(GL11.GL_TRIANGLE_FAN); GL11.glVertex2f(x + width / 2, y + height / 2);
        for (int corner = 0; corner < 4; corner++) {
            float cx = x + (corner == 0 || corner == 3 ? width - radius : radius);
            float cy = y + (corner < 2 ? height - radius : radius);
            for (int step = 0; step <= 12; step++) {
                double angle = Math.toRadians(corner * 90 + step * 7.5);
                GL11.glVertex2f(cx + (float)Math.cos(angle) * radius, cy + (float)Math.sin(angle) * radius);
            }
        }
        GL11.glVertex2f(x + width, y + height - radius); GL11.glEnd();
    }

    @Override public void close() {
        if (program != null) { program.close(); program = null; }
        if (backdrop != 0) { GL11.glDeleteTextures(backdrop); backdrop = 0; }
    }
}

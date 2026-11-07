package dev.vibe.core.startup;

import dev.vibe.ui.effect.EffectProgram;
import dev.vibe.ui.menu.MenuPalette;
import java.nio.FloatBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

/** A small, single-pass liquid progress surface usable before Minecraft initializes. */
final class StartupProgressBar implements AutoCloseable {
    private EffectProgram program;
    private boolean failed;
    private long started = -1;
    private final StartupDrips motion = new StartupDrips();
    private final FloatBuffer drops = BufferUtils.createFloatBuffer(StartupDrips.COUNT * 4);
    private final FloatBuffer forms = BufferUtils.createFloatBuffer(StartupDrips.COUNT * 4);
    private final FloatBuffer necks = BufferUtils.createFloatBuffer(StartupDrips.COUNT * 4);

    void draw(int viewportWidth, int viewportHeight, float scale, float x, float y, float width, float height,
            float progress, boolean preparing, float floor, float pool, long millis, MenuPalette palette) {
        if (started < 0) started = millis;
        float time = Math.max(0, millis - started) / 1000F;
        float fillWidth = width * (preparing ? .18F : Math.max(0, Math.min(1, progress)));
        float fillStart = preparing ? (width - fillWidth) * (.5F + .5F * (float)Math.sin(time / .65F)) : 0;
        motion.sample(time, width, height, fillStart, fillWidth, floor);
        if (!failed && GLContext.getCapabilities().OpenGL20) {
            try {
                if (program == null) program = new EffectProgram("fullscreen.vert", "StartupProgress.frag");
                program.bind();
                program.vec2("Origin", x * scale, viewportHeight - y * scale);
                program.vec2("Size", width, height);
                program.vec2("Fill", fillStart, fillWidth);
                program.scalar("Scale", scale); program.scalar("Time", time);
                program.scalar("Floor", floor);
                program.scalar("Pool", pool);
                drops.clear(); drops.put(motion.drops).flip(); program.vec4Array("Drops[0]", drops);
                forms.clear(); forms.put(motion.forms).flip(); program.vec4Array("Forms[0]", forms);
                necks.clear(); necks.put(motion.necks).flip(); program.vec4Array("Necks[0]", necks);
                rgb("Accent", palette.accent); rgb("Wax", palette.lava); rgb("Light", palette.light);
                int left = Math.max(0, (int)Math.floor((x - 24) * scale));
                int right = Math.min(viewportWidth, (int)Math.ceil((x + width + 24) * scale));
                int bottom = Math.max(0, (int)Math.floor(viewportHeight - (y + floor + 6) * scale));
                int top = Math.min(viewportHeight, (int)Math.ceil(viewportHeight - (y - 13) * scale));
                GL11.glEnable(GL11.GL_SCISSOR_TEST); GL11.glScissor(left, bottom, right - left, top - bottom);
                GL11.glEnable(GL11.GL_BLEND); GL14.glBlendEquation(GL14.GL_FUNC_ADD);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                GL11.glBegin(GL11.GL_QUADS);
                GL11.glVertex2f(-1, -1); GL11.glVertex2f(1, -1); GL11.glVertex2f(1, 1); GL11.glVertex2f(-1, 1); GL11.glEnd();
                return;
            } catch (Exception | LinkageError error) {
                failed = true;
                System.err.println("[Vibe] Liquid loading bar unavailable; using the themed fallback: " + error);
                close();
            } finally {
                GL20.glUseProgram(0); GL11.glDisable(GL11.GL_SCISSOR_TEST);
            }
        }
        fallback(x, y, width, height, fillStart, fillWidth, pool, palette);
    }

    /** Legacy drivers keep the same measured progress and a bounded, softly glowing drip. */
    private void fallback(float x, float y, float width, float height, float start, float filled, float pool, MenuPalette palette) {
        StartupGlass.rounded(x - 1, y - 1, width + 2, height + 2, height, 0x48000000 | palette.accent & 0xFFFFFF);
        StartupGlass.rounded(x, y, width, height, height / 2, 0xDD080D14);
        if (filled > 0) {
        for (int i = 4; i >= 1; i--) StartupGlass.rounded(x + start - i * 2, y - i * 2, filled + i * 4, height + i * 4,
                height / 2 + i * 2, (5 + (4 - i) * 3) << 24 | palette.accent & 0xFFFFFF);
        StartupGlass.rounded(x + start, y, filled, height, height / 2, palette.lava);
        StartupGlass.rounded(x + start + 1, y + 1, Math.max(0, filled - 2), height * .45F, height / 3, 0xB0000000 | palette.light & 0xFFFFFF);
        }
        for (int i = 0; i < StartupDrips.COUNT; i++) {
            int at = i * 4;
            float rx = motion.drops[at + 2], ry = motion.drops[at + 3], cx = x + motion.drops[at], cy = y + motion.drops[at + 1];
            if (rx < .05F) continue;
            float opacity = Math.max(0, Math.min(1, (pool + 8 - motion.drops[at + 1]) / 14));
            if (opacity <= 0) continue;
            GL11.glPushMatrix(); GL11.glTranslatef(cx, cy, 0);
            GL11.glRotatef((float)Math.toDegrees(Math.atan2(motion.forms[at + 1], motion.forms[at])), 0, 0, 1);
            GL11.glScalef(1, ry / rx, 1);
            StartupGlass.rounded(-rx - 4, -rx - 4, rx * 2 + 8, rx * 2 + 8, rx + 4,
                    (int)(35 * opacity) << 24 | palette.accent & 0xFFFFFF);
            StartupGlass.rounded(-rx, -rx, rx * 2, rx * 2, rx, (int)(255 * opacity) << 24 | palette.light & 0xFFFFFF);
            GL11.glPopMatrix();
        }
    }
    private void rgb(String name, int color) {
        program.vec3(name, (color >> 16 & 255) / 255F, (color >> 8 & 255) / 255F, (color & 255) / 255F);
    }
    @Override public void close() { if (program != null) { program.close(); program = null; } }
}

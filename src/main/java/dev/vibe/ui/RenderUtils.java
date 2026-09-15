package dev.vibe.ui;

import java.awt.Color;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

public final class RenderUtils {

    public static final int INK = 0xEE0A1020;
    public static final int SURFACE = 0xF0141E34;
    public static final int SURFACE_HOVER = 0xFF1C2A45;
    public static final int TEXT = 0xFFF1F7FF;
    public static final int MUTED = 0xFF8FA5C4;

    private RenderUtils() {
    }

    public static int accent(float phase) {
        float time = (System.currentTimeMillis() % 5500L) / 5500.0F;
        float blend = (float) ((Math.sin((time + phase) * Math.PI * 2.0D) + 1.0D) * 0.5D);
        return blend(0xFF2DE2C2, 0xFFA855F7, blend);
    }

    public static int blend(int first, int second, float amount) {
        amount = Math.max(0.0F, Math.min(1.0F, amount));
        int alpha = (int) (((first >>> 24) & 255) + (((second >>> 24) & 255) - ((first >>> 24) & 255)) * amount);
        int red = (int) (((first >>> 16) & 255) + (((second >>> 16) & 255) - ((first >>> 16) & 255)) * amount);
        int green = (int) (((first >>> 8) & 255) + (((second >>> 8) & 255) - ((first >>> 8) & 255)) * amount);
        int blue = (int) ((first & 255) + ((second & 255) - (first & 255)) * amount);
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    public static int alpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }

    public static void panel(int left, int top, int right, int bottom, int accent) {
        Gui.drawRect(left + 2, top, right - 2, bottom, INK);
        Gui.drawRect(left, top + 2, right, bottom - 2, INK);
        Gui.drawRect(left + 2, top + 1, right - 2, top + 2, alpha(accent, 225));
        Gui.drawRect(left + 2, bottom - 2, right - 2, bottom - 1, alpha(accent, 75));
        Gui.drawRect(left + 1, top + 2, left + 2, bottom - 2, alpha(accent, 130));
        Gui.drawRect(right - 2, top + 2, right - 1, bottom - 2, alpha(accent, 130));
    }

    public static void roundedRect(int left, int top, int right, int bottom, float radius, int color) {
        if (right <= left || bottom <= top) {
            return;
        }
        // Minecraft's 1.8 HUD state does not reliably accept immediate-mode
        // triangle fans after a ShaderGroup pass. Build the rounded silhouette
        // from one non-overlapping Gui row per pixel instead: it works with
        // the normal GUI batch, retains source alpha exactly once, and still
        // produces a genuine circular corner at every GUI scale.
        int r = Math.max(1, Math.min(Math.round(radius), Math.min(right - left, bottom - top) / 2));
        double radiusSquared = r * r;
        for (int y = top; y < bottom; y++) {
            double distanceFromCorner = Math.min(y - top + 0.5D, bottom - y - 0.5D);
            int inset = 0;
            if (distanceFromCorner < r) {
                double horizontal = Math.sqrt(Math.max(0.0D, radiusSquared - (r - distanceFromCorner) * (r - distanceFromCorner)));
                inset = Math.max(0, r - (int) Math.ceil(horizontal));
            }
            Gui.drawRect(left + inset, y, right - inset, y + 1, color);
        }
    }

    /** A rounded fill whose lower edge remains square, useful for panel headers. */
    public static void roundedTopRect(int left, int top, int right, int bottom, float radius, int color) {
        roundedRect(left, top, right, bottom, radius, color);
        int r = Math.max(1, Math.min(Math.round(radius), Math.min(right - left, bottom - top) / 2));
        Gui.drawRect(left, top + r, right, bottom, color);
    }

    /** Anti-aliased rounded border without filling the enclosed content. */
    public static void roundedOutline(int left, int top, int right, int bottom, float radius, float width, int color) {
        if (right <= left || bottom <= top) {
            return;
        }
        float r = Math.max(1.0F, Math.min(radius, Math.min(right - left, bottom - top) * 0.5F));
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_LINE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        try {
            GlStateManager.disableTexture2D();
            GlStateManager.enableBlend();
            GL11.glEnable(GL11.GL_LINE_SMOOTH);
            GL11.glLineWidth(Math.max(1.0F, width));
            GlStateManager.color(((color >>> 16) & 255) / 255.0F, ((color >>> 8) & 255) / 255.0F,
                    (color & 255) / 255.0F, ((color >>> 24) & 255) / 255.0F);
            GL11.glBegin(GL11.GL_LINE_LOOP);
            roundedOutlineCorner(right - r, top + r, r, -90.0F, 0.0F);
            roundedOutlineCorner(right - r, bottom - r, r, 0.0F, 90.0F);
            roundedOutlineCorner(left + r, bottom - r, r, 90.0F, 180.0F);
            roundedOutlineCorner(left + r, top + r, r, 180.0F, 270.0F);
            GL11.glEnd();
        } finally {
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.enableTexture2D();
            GL11.glPopAttrib();
        }
    }

    private static void roundedOutlineCorner(float centerX, float centerY, float radius, float start, float end) {
        for (int step = 0; step <= 6; step++) {
            double angle = Math.toRadians(start + (end - start) * step / 6.0F);
            GL11.glVertex2f(centerX + (float) Math.cos(angle) * radius,
                    centerY + (float) Math.sin(angle) * radius);
        }
    }

    public static void tacticalCorners(int left, int top, int right, int bottom, int color) {
        tacticalCorners(left, top, right, bottom, color, 2);
    }

    /** Small checkerboard used behind alpha-capable colour previews. */
    public static void transparencyGrid(int left, int top, int right, int bottom, int cell) {
        int size = Math.max(2, cell);
        for (int y = top; y < bottom; y += size) {
            for (int x = left; x < right; x += size) {
                boolean dark = ((x - left) / size + (y - top) / size) % 2 == 0;
                Gui.drawRect(x, y, Math.min(right, x + size), Math.min(bottom, y + size),
                        dark ? 0xFF111B2D : 0xFF32425D);
            }
        }
    }

    public static void tacticalCorners(int left, int top, int right, int bottom, int color, int thickness) {
        thickness = Math.max(1, Math.min(5, thickness));
        int shortSide = Math.max(1, Math.min(right - left, bottom - top));
        int maximumLength = Math.max(thickness, (shortSide - thickness) / 2);
        int length = Math.max(thickness, Math.min(maximumLength, Math.max(4, thickness * 4)));
        tacticalCorners(left, top, right, bottom, color, thickness, length);
    }

    /** Draw non-overlapping tactical corners sized for this projected box. */
    public static void tacticalCorners(int left, int top, int right, int bottom, int color, int thickness, int length) {
        thickness = Math.max(1, Math.min(5, thickness));
        int shortSide = Math.max(1, Math.min(right - left, bottom - top));
        int maximumLength = Math.max(thickness, (shortSide - thickness) / 2);
        length = Math.max(thickness, Math.min(maximumLength, length));
        Gui.drawRect(left, top, left + length, top + thickness, color);
        Gui.drawRect(left, top, left + thickness, top + length, color);
        Gui.drawRect(right - length, top, right, top + thickness, color);
        Gui.drawRect(right - thickness, top, right, top + length, color);
        Gui.drawRect(left, bottom - thickness, left + length, bottom, color);
        Gui.drawRect(left, bottom - length, left + thickness, bottom, color);
        Gui.drawRect(right - length, bottom - thickness, right, bottom, color);
        Gui.drawRect(right - thickness, bottom - length, right, bottom, color);
    }

    public static int healthColor(float health) {
        float ratio = Math.max(0.0F, Math.min(1.0F, health / 20.0F));
        return Color.HSBtoRGB(ratio * 0.31F, 0.72F, 1.0F) | 0xFF000000;
    }
}

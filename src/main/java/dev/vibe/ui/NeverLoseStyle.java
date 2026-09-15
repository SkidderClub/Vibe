package dev.vibe.ui;

import dev.vibe.module.Category;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

/** Quiet graphite surfaces, blue controls and small outline navigation icons. */
final class NeverLoseStyle {
    static final int BACKGROUND = 0xFF0D1015, SIDEBAR = 0xFF101419, CARD = 0xFF111318;
    static final int BORDER = 0xFF1D2027, CONTROL = 0xFF1B1E26, TEXT = 0xFFD3D7DF;
    static final int MUTED = 0xFF7B828E, DIM = 0xFF555D69, ACCENT = 0xFF3984FF;
    private NeverLoseStyle() { }

    static void rect(int x, int y, int w, int h, int radius, int color) {
        MenuRoundedRenderer.rect(x, y, w, h, radius, color);
    }
    static void surface(int x, int y, int w, int h, int radius, int fill) {
        rect(x, y, w, h, radius, BORDER); rect(x + 1, y + 1, w - 2, h - 2, radius - 1, fill);
    }
    static void text(String label, float x, float y, int color) { NeverLoseFont.REGULAR.draw(label, x, y, color); }
    static void label(String label, float x, float y, float available, int color) {
        text(NeverLoseFont.REGULAR.fit(label, available), x, y, color);
    }
    static void small(String label, int x, int y, int color) {
        GlStateManager.pushMatrix(); GlStateManager.translate(x, y, 0); GlStateManager.scale(.8F, .8F, 1);
        text(label, 0, 0, color); GlStateManager.popMatrix();
    }
    static void toggle(int x, int y, boolean on, float progress) {
        rect(x, y, 25, 13, 7, dev.vibe.ui.RenderUtils.blend(0xFF090C10, 0xFF214478, progress));
        rect(x + 2 + Math.round(12 * progress), y + 2, 9, 9, 5, on ? ACCENT : 0xFF7D8792);
    }
    static void chevron(int x, int y, boolean up, int color) {
        line(color, x, y + (up ? 3 : 0), x + 3, y + (up ? 0 : 3), x + 6, y + (up ? 3 : 0));
    }
    static void line(int color, float... points) {
        boolean smooth = GL11.glIsEnabled(GL11.GL_LINE_SMOOTH);
        float previousWidth = GL11.glGetFloat(GL11.GL_LINE_WIDTH);
        GlStateManager.disableTexture2D(); GlStateManager.enableBlend();
        GL11.glEnable(GL11.GL_LINE_SMOOTH); GL11.glLineWidth(1.2F);
        GlStateManager.color((color >> 16 & 255) / 255F, (color >> 8 & 255) / 255F, (color & 255) / 255F, (color >>> 24) / 255F);
        GL11.glBegin(GL11.GL_LINE_STRIP);
        for (int i = 0; i < points.length; i += 2) GL11.glVertex2f(points[i], points[i + 1]);
        GL11.glEnd(); GL11.glLineWidth(previousWidth);
        if (!smooth) GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GlStateManager.enableTexture2D(); GlStateManager.color(1, 1, 1, 1);
    }
    static void circle(int x, int y, int radius, int color) {
        float[] points = new float[42];
        for (int i = 0; i <= 20; i++) {
            double a = i * Math.PI / 10;
            points[i * 2] = x + (float) Math.cos(a) * radius; points[i * 2 + 1] = y + (float) Math.sin(a) * radius;
        }
        line(color, points);
    }
    static void search(int x, int y, int color) { circle(x + 4, y + 4, 4, color); line(color, x + 7, y + 7, x + 10, y + 10); }
    static void icon(Category category, int x, int y, int color) {
        switch (category) {
            case COMBAT:
                circle(x + 5, y + 5, 4, color); line(color, x + 5, y - 1, x + 5, y + 2);
                line(color, x + 5, y + 8, x + 5, y + 11); line(color, x - 1, y + 5, x + 2, y + 5); line(color, x + 8, y + 5, x + 11, y + 5); break;
            case VISUAL:
                line(color, x - 1, y + 5, x + 2, y + 2, x + 8, y + 2, x + 11, y + 5, x + 8, y + 8, x + 2, y + 8, x - 1, y + 5); circle(x + 5, y + 5, 2, color); break;
            case MOVEMENT:
                line(color, x + 6, y, x + 2, y + 6, x + 6, y + 6, x + 4, y + 11, x + 10, y + 4, x + 6, y + 4, x + 6, y); break;
            case WORLD:
                line(color, x, y + 3, x + 5, y, x + 10, y + 3, x + 10, y + 9, x + 5, y + 12, x, y + 9, x, y + 3, x + 5, y + 6, x + 10, y + 3); line(color, x + 5, y + 6, x + 5, y + 12); break;
            case MEME:
                circle(x + 5, y + 5, 5, color);
                // Filled pupils stay readable at the small sidebar scale.
                rect(x + 2, y + 3, 2, 2, 1, color); rect(x + 7, y + 3, 2, 2, 1, color);
                line(color, x + 2, y + 6, x + 4, y + 8, x + 6, y + 8, x + 8, y + 6); break;
            case SCRIPTS:
                line(color, x + 3, y + 1, x, y + 5, x + 3, y + 9); line(color, x + 7, y + 1, x + 10, y + 5, x + 7, y + 9); break;
            default:
                for (int i = 0; i < 3; i++) { line(color, x, y + i * 4, x + 10, y + i * 4); rect(x + (i == 1 ? 6 : 2), y + i * 4 - 1, 2, 3, 1, color); }
        }
    }
}

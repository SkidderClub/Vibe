package dev.vibe.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.lwjgl.opengl.GL11;

/** Lazy glyph pages, rasterized at twice the GUI resolution. No per-label textures. */
final class NeverLoseFont {
    static final NeverLoseFont REGULAR = new NeverLoseFont(Font.PLAIN);
    static final NeverLoseFont BOLD = new NeverLoseFont(Font.BOLD);
    private static final int CELL = 32, SIZE = CELL * 16;
    private final Font font;
    private final FontMetrics metrics;
    private final Map<Integer, Page> pages = new HashMap<Integer, Page>();
    private Font fallback;

    private NeverLoseFont(int style) {
        this(style, 20);
    }

    NeverLoseFont(int style, int rasterSize) {
        this(Font.SANS_SERIF, style, rasterSize);
    }

    NeverLoseFont(String family, int style, int rasterSize) {
        font = new Font(family, style, rasterSize);
        BufferedImage sample = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = sample.createGraphics();
        g.setFont(font); metrics = g.getFontMetrics(); g.dispose();
    }

    float width(String text) {
        float width = 0;
        for (int i = 0; i < text.length(); i++) width += advance(text.charAt(i));
        return width;
    }
    void close(){for(Page page:pages.values())page.texture.deleteGlTexture();pages.clear();}

    String fit(String text, float available) {
        if (width(text) <= available) return text;
        float used = width("...");
        if (used > available) return "";
        int end = 0;
        while (end < text.length() && used + advance(text.charAt(end)) <= available) used += advance(text.charAt(end++));
        return text.substring(0, end) + "...";
    }

    private float advance(char c) {
        return font.canDisplay(c) ? metrics.charWidth(c) * .5F : page(c >> 8).advances[c & 255];
    }

    void draw(String text, float x, float y, int color) {
        if (text.isEmpty()) return;
        GlStateManager.enableTexture2D(); GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        boolean alpha = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        GlStateManager.disableAlpha();
        GlStateManager.color((color >> 16 & 255) / 255F, (color >> 8 & 255) / 255F, (color & 255) / 255F, (color >>> 24) / 255F);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            Page page = page(c >> 8);
            GlStateManager.bindTexture(page.texture.getGlTextureId());
            int index = c & 255;
            float u = (index % 16) / 16F, v = (index / 16) / 16F;
            float left = x - 1, top = y - 1;
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2f(u, v); GL11.glVertex2f(left, top);
            GL11.glTexCoord2f(u, v + 1 / 16F); GL11.glVertex2f(left, top + CELL / 2F);
            GL11.glTexCoord2f(u + 1 / 16F, v + 1 / 16F); GL11.glVertex2f(left + CELL / 2F, top + CELL / 2F);
            GL11.glTexCoord2f(u + 1 / 16F, v); GL11.glVertex2f(left + CELL / 2F, top);
            GL11.glEnd();
            x += page.advances[index];
        }
        GlStateManager.color(1, 1, 1, 1);
        if (alpha) GlStateManager.enableAlpha();
    }

    private Page page(int number) {
        Page page = pages.get(number);
        if (page != null) return page;
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        float[] advances = new float[256];
        for (int i = 0; i < 256; i++) {
            char c = (char) ((number << 8) | i);
            Font selected = font.canDisplay(c) ? font : fallback();
            g.setFont(selected);
            advances[i] = g.getFontMetrics().charWidth(c) * .5F;
            int cellX = (i % 16) * CELL, cellY = (i / 16) * CELL;
            // Keep a transparent gutter on every side. Descenders must not spill
            // into the next cell, and linear filtering must only sample padding
            // at cell boundaries (not a fragment of the neighbouring glyph).
            g.setClip(cellX + 1, cellY + 1, CELL - 2, CELL - 2);
            if (!Character.isISOControl(c)) g.drawString(String.valueOf(c), cellX + 2, cellY + 2 + metrics.getAscent());
        }
        g.dispose();
        DynamicTexture texture = new DynamicTexture(image);
        texture.setBlurMipmap(true, false);
        page = new Page(texture, advances); pages.put(number, page);
        return page;
    }

    private Font fallback() {
        if (fallback == null) {
            fallback = font;
            try (InputStream input = NeverLoseFont.class.getResourceAsStream("/assets/vibe/fonts/NotoSansCJKsc-Regular.otf")) {
                if (input != null) fallback = Font.createFont(Font.TRUETYPE_FONT, input).deriveFont(font.getSize2D());
            } catch (Exception ignored) { /* Logical fonts still cover Latin and Cyrillic. */ }
        }
        return fallback;
    }

    private static final class Page {
        final DynamicTexture texture;
        final float[] advances;
        Page(DynamicTexture texture, float[] advances) { this.texture = texture; this.advances = advances; }
    }
}

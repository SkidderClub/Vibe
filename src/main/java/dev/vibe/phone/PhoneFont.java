package dev.vibe.phone;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.LineMetrics;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

/** Four samples per logical pixel, fractional advances, and enough room for every glyph. */
final class PhoneFont {
    private static final int SAMPLES = 4, PADDING = 4, CACHE_LIMIT = 96;
    private static final FontRenderContext CONTEXT = new FontRenderContext(null, true, true);
    private final Font font;
    private final LinkedHashMap<String, Label> labels = new LinkedHashMap<String, Label>(32, .75F, true);

    PhoneFont(int style, float size) {
        font = new Font("Segoe UI", style, 12).deriveFont(size * SAMPLES);
    }

    float width(String value) { return (float) font.getStringBounds(value, CONTEXT).getWidth() / SAMPLES; }

    String fit(String value, float available) {
        if (width(value) <= available) return value;
        String suffix = "...";
        if (width(suffix) > available) return "";
        int end = value.length();
        while (end > 0 && width(value.substring(0, end) + suffix) > available) end--;
        return value.substring(0, end) + suffix;
    }

    void draw(String value, float x, float y, int color) {
        if (value.isEmpty()) return;
        Label label = labels.get(value);
        if (label == null) {
            label = rasterize(value);
            labels.put(value, label);
            if (labels.size() > CACHE_LIMIT) {
                Map.Entry<String, Label> oldest = labels.entrySet().iterator().next();
                oldest.getValue().texture.deleteGlTexture();
                labels.remove(oldest.getKey());
            }
        }
        GlStateManager.enableTexture2D(); GlStateManager.enableBlend(); GlStateManager.disableAlpha();
        // The offscreen phone stores premultiplied color and source-over coverage.
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 771);
        GlStateManager.bindTexture(label.texture.getGlTextureId());
        GlStateManager.color((color >> 16 & 255) / 255F, (color >> 8 & 255) / 255F,
                (color & 255) / 255F, (color >>> 24) / 255F);
        float left = x + label.left, top = y - PADDING / (float) SAMPLES;
        float right = left + label.width / (float) SAMPLES, bottom = top + label.height / (float) SAMPLES;
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0, 0); GL11.glVertex2f(left, top);
        GL11.glTexCoord2f(0, 1); GL11.glVertex2f(left, bottom);
        GL11.glTexCoord2f(1, 1); GL11.glVertex2f(right, bottom);
        GL11.glTexCoord2f(1, 0); GL11.glVertex2f(right, top);
        GL11.glEnd(); GlStateManager.color(1, 1, 1, 1);
    }

    private Label rasterize(String value) {
        Rectangle2D ink = font.createGlyphVector(CONTEXT, value).getVisualBounds();
        LineMetrics metrics = font.getLineMetrics(value, CONTEXT);
        int left = Math.min(0, (int) Math.floor(ink.getMinX())) - PADDING;
        int width = (int) Math.ceil(Math.max(ink.getMaxX(), width(value) * SAMPLES)) - left + PADDING;
        int height = (int) Math.ceil(Math.max(metrics.getHeight(), metrics.getAscent() + ink.getMaxY())) + PADDING * 2;
        BufferedImage image = new BufferedImage(Math.max(1, width), Math.max(1, height), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        graphics.setFont(font); graphics.setColor(Color.WHITE);
        graphics.drawString(value, -left, PADDING + metrics.getAscent()); graphics.dispose();
        // Transparent texels must also be white: filtering coverage must not dim thin strokes.
        int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
        for (int i = 0; i < pixels.length; i++) pixels[i] |= 0x00FFFFFF;
        image.setRGB(0, 0, width, height, pixels, 0, width);
        DynamicTexture texture = new DynamicTexture(image);
        texture.setBlurMipmap(true, true);
        GlStateManager.bindTexture(texture.getGlTextureId());
        PhoneCanvas.generateMipmaps();
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        return new Label(texture, width, height, left / (float) SAMPLES);
    }

    void close() {
        for (Label label : labels.values()) label.texture.deleteGlTexture();
        labels.clear();
    }

    private static final class Label {
        final DynamicTexture texture;
        final int width, height;
        final float left;
        Label(DynamicTexture texture, int width, int height, float left) {
            this.texture = texture; this.width = width; this.height = height; this.left = left;
        }
    }
}

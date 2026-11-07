package dev.vibe.core.startup;

import dev.vibe.ui.menu.MenuPalette;
import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.LineMetrics;
import java.awt.font.TextAttribute;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.lwjgl.opengl.*;

/** Early loading UI, independent of Minecraft fonts, resource packs and GL state caches. */
public final class GhostLoadingRenderer implements AutoCloseable {
    private static final int FOREGROUND = 0xFFEDEEF0, MUTED = 0xFF828D9C;
    private static final int TEXT_SCALE = 3, TEXT_CACHE_LIMIT = 24;
    private static final FontRenderContext METRICS = new FontRenderContext(null, true, true);
    private static final Font TITLE = font(44, Font.BOLD), SMALL_TITLE = font(32, Font.BOLD);
    private static final Font LABEL = font(13, Font.PLAIN), DETAIL = font(11, Font.PLAIN), META = font(10, Font.PLAIN);

    private final GhostArt ghost = new GhostArt();
    private final MenuPalette palette;
    private final StartupGlass glass;
    private final StartupProgressBar bar = new StartupProgressBar();
    private final Map<String, Text> textCache = new LinkedHashMap<String, Text>(32, .75F, true);
    private String progressStage = "";
    private float displayedProgress;
    private long lastFrame = -1;

    public GhostLoadingRenderer() { this(MenuPalette.OCEAN); }
    public GhostLoadingRenderer(MenuPalette palette) { this.palette = palette; glass = new StartupGlass(palette); }

    public void draw(int width, int height, List<GhostStartup.Stage> stages, long millis) {
        MenuPalette palette=this.palette.sample(System.currentTimeMillis());
        int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        boolean shaders = GLContext.getCapabilities().OpenGL20;
        int program = shaders ? GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM) : 0;
        int matrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glPushMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glPushMatrix();
        try {
            if (shaders) GL20.glUseProgram(0);
            for (int i = 1; i < Math.min(3, GL11.glGetInteger(GL13.GL_MAX_TEXTURE_UNITS)); i++) {
                GL13.glActiveTexture(GL13.GL_TEXTURE0 + i); GL11.glDisable(GL11.GL_TEXTURE_2D);
            }
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glDisable(GL11.GL_DEPTH_TEST); GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_ALPHA_TEST); GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_FOG); GL11.glDisable(GL11.GL_STENCIL_TEST);
            GL11.glDisable(GL11.GL_SCISSOR_TEST); GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_MODULATE);
            GL11.glViewport(0, 0, width, height);
            float scale = Math.max(1F, Math.min(2F, Math.min(width / 960F, height / 600F)));
            float w = width / scale, h = height / scale;
            GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glLoadIdentity(); GL11.glOrtho(0, w, h, 0, -1, 1);
            GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glLoadIdentity();

            boolean compact = h < 360 || w < 480;
            float margin = compact ? 16 : 40, padding = compact ? 20 : 48;
            float cardWidth = Math.min(640, w - margin * 2), cardHeight = Math.min(compact ? 208 : 330, h - margin * 2);
            float cardLeft = snap((w - cardWidth) / 2, scale), cardTop = snap((h - cardHeight) / 2, scale);
            glass.draw(width, height, scale, cardLeft, cardTop, cardWidth, cardHeight, millis);
            float left = cardLeft + padding, contentWidth = cardWidth - padding * 2, right = left + contentWidth;
            boolean tight = compact && cardHeight < 190;
            float top = snap(cardTop + (compact ? tight ? 24 : 28 : 60), scale);
            float icon = compact ? tight ? 44 : 52 : 80, gap = compact ? 14 : 20;
            Font title = compact ? SMALL_TITLE : TITLE;
            ghost.draw(left, top, icon, icon, true, false, false, false, 0, 1, millis, 12);
            float titleY = snap(top + (icon - lineHeight(title)) / 2 - (compact ? 0 : 10), scale);
            text("Vibe", title, left + icon + gap, titleY, FOREGROUND);
            if (!compact) text("Loading Minecraft", LABEL, left + icon + gap, titleY + lineHeight(title) + 2, palette.muted);

            float y = snap(cardTop + cardHeight - (compact ? 84 : 125), scale);
            StartupGlass.rounded(left - 14, y - 14, contentWidth + 28, 90, 12, 0xC4000000 | palette.background & 0xFFFFFF);
            GhostStartup.Stage current = stages.isEmpty() ? null : stages.get(stages.size() - 1);
            boolean determinate = current != null && current.steps > 0;
            String titleText = current == null || current.title.isEmpty() ? "Preparing Minecraft" : current.title;
            String percent = determinate ? Math.round(current.ratio() * 100) + "%" : "";
            text(fit(titleText, LABEL, contentWidth - measure(percent, LABEL) - 24), LABEL, left, y, FOREGROUND);
            text(percent, LABEL, right - measure(percent, LABEL), y, palette.muted);

            String message = current == null ? "" : current.message;
            String count = determinate ? current.step + " of " + current.steps : "";
            text(fit(message, DETAIL, contentWidth - measure(count, DETAIL) - 20), DETAIL, left, y + 19, palette.muted);
            text(count, DETAIL, right - measure(count, DETAIL), y + 19, MUTED);
            float barY = snap(y + 38, scale), barHeight = 9;
            float dropFloor = cardTop + cardHeight - 6 - barY;
            float poolSurface = cardTop + cardHeight * .90F - barY;
            if (determinate) {
                String identity = stages.get(0).title + '\n' + current.title + '\n' + current.steps;
                float amount = progress(identity, current.ratio(), millis);
                bar.draw(width, height, scale, left, barY, contentWidth, barHeight, amount, false, dropFloor, poolSurface, millis, palette);
            } else {
                progressStage = ""; lastFrame = millis;
                bar.draw(width, height, scale, left, barY, contentWidth, barHeight, 0, true, dropFloor, poolSurface, millis, palette);
            }

            if (!compact) text("Minecraft 1.8.9", META, margin, h - 32, MUTED);
        } finally {
            GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glPopMatrix(); GL11.glMatrixMode(matrixMode);
            if (shaders) GL20.glUseProgram(program);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture); GL13.glActiveTexture(active); GL11.glPopAttrib();
        }
    }

    /** A brief catch-up to the measured Forge step; never advances beyond that step. */
    private float progress(String stage, float actual, long millis) {
        if (!stage.equals(progressStage) || lastFrame < 0 || actual < displayedProgress) displayedProgress = actual;
        else {
            double elapsed = Math.max(0, Math.min(250, millis - lastFrame));
            displayedProgress += (actual - displayedProgress) * (1 - (float)Math.exp(-elapsed / 100));
        }
        progressStage = stage; lastFrame = millis;
        return displayedProgress;
    }

    private static Font font(int size, int weight) {
        Font base = new Font("Segoe UI", weight, size);
        return base.deriveFont(Collections.singletonMap(TextAttribute.KERNING, TextAttribute.KERNING_ON));
    }
    private static float lineHeight(Font font) { return font.getLineMetrics("Vibe", METRICS).getHeight(); }
    private static float measure(String value, Font font) { return value.isEmpty() ? 0 : (float)font.getStringBounds(value, METRICS).getWidth(); }
    private static float snap(float value, float scale) { return Math.round(value * scale) / scale; }
    private static String fit(String value, Font font, float width) {
        if (measure(value, font) <= width) return value;
        String ellipsis = "\u2026";
        if (measure(ellipsis, font) > width) return "";
        int low = 0, high = value.length();
        while (low < high) {
            int middle = (low + high + 1) / 2;
            if (measure(value.substring(0, middle) + ellipsis, font) <= width) low = middle;
            else high = middle - 1;
        }
        if (low > 0 && Character.isHighSurrogate(value.charAt(low - 1))) low--;
        return value.substring(0, low) + ellipsis;
    }

    /** Cache complete, kerned text runs at 3x resolution instead of enlarging a tiny glyph atlas. */
    private void text(String value, Font font, float x, float y, int color) {
        if (value.isEmpty()) return;
        String key = font.getStyle() + ":" + font.getSize() + ":" + value;
        Text text = textCache.get(key);
        if (text == null) {
            text = new Text(value, font); textCache.put(key, text);
            if (textCache.size() > TEXT_CACHE_LIMIT) {
                java.util.Iterator<Text> oldest = textCache.values().iterator();
                Text evicted = oldest.next(); oldest.remove(); evicted.close();
            }
        }
        GL11.glEnable(GL11.GL_TEXTURE_2D); GL11.glBindTexture(GL11.GL_TEXTURE_2D, text.texture); color(color);
        float a = x - 2, b = y - 2, right = a + text.width, bottom = b + text.height;
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0, 0); GL11.glVertex2f(a, b);
        GL11.glTexCoord2f(0, 1); GL11.glVertex2f(a, bottom);
        GL11.glTexCoord2f(1, 1); GL11.glVertex2f(right, bottom);
        GL11.glTexCoord2f(1, 0); GL11.glVertex2f(right, b); GL11.glEnd();
    }
    private static final class Text implements AutoCloseable {
        final int texture;
        final float width, height;
        Text(String value, Font font) {
            LineMetrics metrics = font.getLineMetrics(value, METRICS);
            int w = Math.max(1, (int)Math.ceil((measure(value, font) + 4) * TEXT_SCALE));
            int h = Math.max(1, (int)Math.ceil((metrics.getHeight() + 4) * TEXT_SCALE));
            BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();
            try {
                graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
                graphics.setFont(font.deriveFont(font.getSize2D() * TEXT_SCALE)); graphics.setColor(Color.WHITE);
                graphics.drawString(value, 2 * TEXT_SCALE, (2 + metrics.getAscent()) * TEXT_SCALE);
            } finally { graphics.dispose(); }
            // Keep RGB white even in transparent padding so filtering preserves the glyph's coverage.
            int[] pixels = ((DataBufferInt)image.getRaster().getDataBuffer()).getData();
            for (int i = 0; i < pixels.length; i++) pixels[i] |= 0x00FFFFFF;
            texture = GhostArt.upload(image); width = w / (float)TEXT_SCALE; height = h / (float)TEXT_SCALE;
        }
        @Override public void close() { GL11.glDeleteTextures(texture); }
    }
    private static void color(int c) { GL11.glColor4f((c >> 16 & 255) / 255F, (c >> 8 & 255) / 255F, (c & 255) / 255F, (c >>> 24) / 255F); }
    @Override public void close() { bar.close(); glass.close(); ghost.close(); for (Text text : textCache.values()) text.close(); textCache.clear(); }
}

/* Copyright (c) 2024. Schizoid. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * Media Player layout/scrolling/blurred-artwork port, modified for Vibe on 2026-09-09.
 * Visual refresh on 2026-09-12: smooth Vibe fonts, antialiased artwork and compact timeline.
 * Uses Vibe's own SMTC/radio snapshots and Minecraft 1.8.9 texture APIs.
 */
package dev.vibe.ui;

import dev.vibe.media.MediaTrack;
import dev.vibe.module.impl.MusicModule;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

/** Schizoid-style album tile, soft artwork backdrop and a compact media timeline. */
public final class MusicHudRenderer {
    private static final int HEIGHT = 64;
    private static final NeverLoseFont TITLE = new NeverLoseFont(Font.BOLD, 24);
    private static final NeverLoseFont ARTIST = NeverLoseFont.REGULAR;
    private static final NeverLoseFont DETAIL = new NeverLoseFont(Font.PLAIN, 18);
    private final Minecraft mc = Minecraft.getMinecraft();
    private BufferedImage image;
    private ResourceLocation surface;
    private int cachedWidth, cachedBackground, cachedAccent;
    private boolean cachedCover, cachedBackdrop, cachedThumbnail;
    private String trackKey = "";
    private long changedAt;

    public void draw(MusicModule module, MediaTrack track, int left, int top, boolean preview) {
        if (preview && track.durationMs == 0 && track.artwork == null)
            track = new MediaTrack("Your music, in Vibe", "Artist / Album", "Media preview", "Playing",
                    true, false, 42000, 180000, null);
        String key = track.title + "\n" + track.artist;
        if (!key.equals(trackKey)) { trackKey = key; changedAt = System.currentTimeMillis(); }

        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST), mask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        boolean alpha = GL11.glIsEnabled(GL11.GL_ALPHA_TEST), blend = GL11.glIsEnabled(GL11.GL_BLEND);
        int sourceRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), destRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int sourceAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), destAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        ScaledResolution resolution = new ScaledResolution(mc);
        float scale = effectiveScale(module, resolution.getScaledWidth(), resolution.getScaledHeight());
        GL11.glPushMatrix();
        GL11.glTranslatef(left, top, 0);
        GL11.glScalef(scale, scale, 1);
        try {
            GlStateManager.disableDepth(); GlStateManager.depthMask(false);
            GlStateManager.disableAlpha(); GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            updateSurface(module, track.artwork);
            mc.getTextureManager().bindTexture(surface);
            GlStateManager.enableTexture2D(); GlStateManager.color(1, 1, 1, 1);
            int width = module.hudWidth.getInt();
            Gui.drawScaledCustomSizeModalRect(0, 0, 0, 0, width * 2, HEIGHT * 2, width, HEIGHT, width * 2, HEIGHT * 2);

            int textX = module.cover.isEnabled() ? HEIGHT + 12 : 14;
            int available = width - textX - 14;
            int text = module.text.getArgb();
            marquee(TITLE, track.title.isEmpty() ? "Unknown title" : track.title, textX, 10, available, text,
                    module.scroll.isEnabled(), left, top, scale);
            String artist = track.artist.isEmpty() ? track.owner : track.artist;
            marquee(ARTIST, artist.isEmpty() ? "Unknown artist" : artist, textX, 26, available, opacity(text, .68f),
                    module.scroll.isEnabled(), left, top, scale);
            timeline(module, track, textX, available);
        } finally {
            GL11.glPopMatrix();
            GlStateManager.enableTexture2D(); GlStateManager.resetColor(); GlStateManager.color(1, 1, 1, 1);
            GlStateManager.depthMask(mask);
            if (depth) GlStateManager.enableDepth(); else GlStateManager.disableDepth();
            if (alpha) GlStateManager.enableAlpha(); else GlStateManager.disableAlpha();
            GlStateManager.tryBlendFuncSeparate(sourceRgb, destRgb, sourceAlpha, destAlpha);
            if (blend) GlStateManager.enableBlend(); else GlStateManager.disableBlend();
        }
    }

    private void timeline(MusicModule module, MediaTrack track, int x, int width) {
        int muted = opacity(module.text.getArgb(), .68f);
        int accent = module.accent.getArgb();
        long position = track.position();
        String timing = track.live ? (track.playing ? "LIVE RADIO" : track.status)
                : track.durationMs > 0 ? time(position) + " / " + time(track.durationMs) : track.status;
        // Keep unknown-duration and connection messages visible; never imply seekable live audio.
        String state = track.durationMs > 0 && !track.live ? (track.playing ? "PLAYING" : "PAUSED") : "";
        float statusWidth = state.isEmpty() ? 8 : DETAIL.width(state) + 13;
        boolean showLabel = !state.isEmpty() && DETAIL.width(timing) + statusWidth + 12 <= width;
        if (!showLabel) { state = ""; statusWidth = 8; }
        DETAIL.draw(DETAIL.fit(timing, width - statusWidth - 8), x, 40, muted);
        int iconX = x + width - (int) Math.ceil(statusWidth);
        int indicator = track.playing ? accent : opacity(module.text.getArgb(), .6f);
        if (track.playing) {
            for (int i = 0; i < 3; i++) {
                int height = i == 1 ? 7 : i == 0 ? 4 : 5;
                MenuRoundedRenderer.rect(iconX + i * 3, 48 - height, 1, height, 0, indicator);
            }
        } else {
            MenuRoundedRenderer.rect(iconX, 41, 2, 6, 1, indicator);
            MenuRoundedRenderer.rect(iconX + 4, 41, 2, 6, 1, indicator);
        }
        if (!state.isEmpty()) DETAIL.draw(state, iconX + 13, 40, muted);
        if (module.progress.isEnabled()) {
            MenuRoundedRenderer.rect(x, 53, width, 2, 1, opacity(module.text.getArgb(), .13f));
            float progress = track.live ? (track.playing ? 1 : 0)
                    : track.durationMs == 0 ? 0 : Math.min(1, position / (float) track.durationMs);
            if (progress > 0) {
                int filled = Math.max(1, Math.min(width, Math.round(width * progress)));
                MenuRoundedRenderer.rect(x, 53, filled, 2, 1, accent);
            }
        }
    }

    public static float effectiveScale(MusicModule module, int width, int height) {
        return Math.max(.1f, Math.min(module.hudScale.getFloat(),
                Math.min((width - 8f) / module.hudWidth.getFloat(), (height - 8f) / HEIGHT)));
    }

    private void marquee(NeverLoseFont font, String text, int x, int y, int width, int color,
                         boolean scroll, int left, int top, float scale) {
        float overflow = font.width(text) - width;
        if (overflow <= 0 || !scroll) { font.draw(font.fit(text, width), x, y, color); return; }
        double duration = Math.max(4, overflow / 22.0), cycle = (System.currentTimeMillis() - changedAt) / 1000.0;
        double t = cycle % (duration * 2 + 2);
        double offset = t < 1 ? 0 : t < 1 + duration ? (t - 1) / duration
                : t < 2 + duration ? 1 : 1 - (t - 2 - duration) / duration;
        int clipX = (int) Math.ceil(left + x * scale), clipY = (int) Math.floor(top + y * scale);
        int clipRight = (int) Math.floor(left + (x + width) * scale);
        int clipBottom = (int) Math.ceil(top + (y + 16) * scale);
        try (GuiClip ignored = new GuiClip(clipX, clipY, clipRight - clipX, clipBottom - clipY)) {
            font.draw(text, x - (float) (Math.max(0, Math.min(1, offset)) * overflow), y, color);
        }
    }

    /** Bake only on artwork/style changes. The 2x mask avoids polygonal corners at HUD scale. */
    private void updateSurface(MusicModule module, BufferedImage next) {
        int width = module.hudWidth.getInt(), background = module.background.getArgb(), accent = module.accent.getArgb();
        boolean cover = module.cover.isEnabled(), backdrop = module.coverBackground.isEnabled();
        boolean thumbnail = next != null && !module.radio.isEnabled();
        if (surface != null && image == next && width == cachedWidth && background == cachedBackground
                && accent == cachedAccent && cover == cachedCover && backdrop == cachedBackdrop && thumbnail == cachedThumbnail) return;
        BufferedImage tile = next == null ? vinyl(accent) : next;
        BufferedImage canvas = new BufferedImage(width * 2, HEIGHT * 2, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = canvas.createGraphics();
        try {
            quality(g); g.scale(2, 2);
            RoundRectangle2D body = new RoundRectangle2D.Float(0, 4, width, HEIGHT - 8, 16, 16);
            g.setColor(new Color(background, true)); g.fill(body); g.setClip(body);
            if (backdrop) {
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_ATOP, thumbnail ? .85f : .48f));
                if (thumbnail) {
                    double scale = Math.max(width / (double) tile.getWidth(), (HEIGHT - 8) / (double) tile.getHeight());
                    int w = (int) Math.ceil(tile.getWidth() * scale), h = (int) Math.ceil(tile.getHeight() * scale);
                    g.drawImage(tile, (width - w) / 2, (HEIGHT - h) / 2, w, h, null);
                } else {
                    BufferedImage blurred = blur(tile);
                    g.drawImage(blurred, 0, 4, width, HEIGHT - 4, 4, 16, 44, 32, null);
                }
                g.setComposite(AlphaComposite.SrcAtop);
                g.setPaint(new GradientPaint(0, 0, new Color(0x08000000, true), width, 0, new Color(0x88070A10, true)));
                g.fill(body);
            }
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_ATOP, .08f));
            g.setPaint(new GradientPaint(0, 4, Color.WHITE, 0, HEIGHT - 4, new Color(0, true)));
            g.setStroke(new BasicStroke(1));
            g.draw(new RoundRectangle2D.Float(.5f, 4.5f, width - 1, HEIGHT - 9, 15, 15));
            g.setComposite(AlphaComposite.SrcOver); g.setClip(null);
            if (cover) {
                RoundRectangle2D outline = new RoundRectangle2D.Float(0, 0, HEIGHT, HEIGHT, 16, 16);
                g.setColor(new Color(0xFF101318, true)); g.fill(outline); g.setClip(outline);
                // Keep the whole video thumbnail visible, including widescreen covers.
                double scale = HEIGHT / (double) Math.max(tile.getWidth(), tile.getHeight());
                int w = Math.max(1, (int) Math.round(tile.getWidth() * scale)), h = Math.max(1, (int) Math.round(tile.getHeight() * scale));
                g.drawImage(tile, (HEIGHT - w) / 2, (HEIGHT - h) / 2, w, h, null);
                g.setPaint(new GradientPaint(0, 0, new Color(0x28FFFFFF, true), HEIGHT, HEIGHT, new Color(0x04FFFFFF, true)));
                g.setStroke(new BasicStroke(.75f));
                g.draw(new RoundRectangle2D.Float(.5f, .5f, HEIGHT - 1, HEIGHT - 1, 15, 15));
            }
        } finally { g.dispose(); }
        DynamicTexture texture = new DynamicTexture(canvas);
        texture.setBlurMipmap(true, false);
        if (surface != null) mc.getTextureManager().deleteTexture(surface);
        surface = mc.getTextureManager().getDynamicTextureLocation("vibe-media-surface", texture);
        image = next; cachedWidth = width; cachedBackground = background; cachedAccent = accent;
        cachedCover = cover; cachedBackdrop = backdrop; cachedThumbnail = thumbnail;
    }

    /** Original code-drawn fallback; no upstream vinyl artwork or font files are bundled. */
    private static BufferedImage vinyl(int accent) {
        BufferedImage result = new BufferedImage(192, 192, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = result.createGraphics();
        try {
            quality(g); g.scale(3, 3);
            g.setPaint(new GradientPaint(0, 0, new Color(0xFF293039, true), 64, 64, new Color(0xFF101419, true)));
            g.fillRect(0, 0, 64, 64);
            g.setColor(new Color(0x70000000, true)); g.fill(new Ellipse2D.Float(6, 8, 54, 54));
            g.setColor(new Color(0xFF0C0F13, true)); g.fill(new Ellipse2D.Float(5, 5, 54, 54));
            g.setStroke(new BasicStroke(.45f));
            for (int radius = 12; radius <= 26; radius += 2) {
                g.setColor(new Color(0xFF242930, true));
                g.draw(new Ellipse2D.Float(32 - radius, 32 - radius, radius * 2, radius * 2));
                g.setColor(new Color(0x305D6874, true));
                g.drawArc(32 - radius, 32 - radius, radius * 2, radius * 2, 38, 62);
                g.drawArc(32 - radius, 32 - radius, radius * 2, radius * 2, 218, 62);
            }
            g.setColor(new Color(accent | 0xFF000000, true)); g.fill(new Ellipse2D.Float(22, 22, 20, 20));
            g.setColor(new Color(0x26000000, true)); g.fill(new Ellipse2D.Float(25, 25, 14, 14));
            g.setColor(new Color(0xFF11161C, true)); g.fill(new Ellipse2D.Float(30, 30, 4, 4));
        } finally { g.dispose(); }
        return result;
    }

    private static void quality(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    }

    /** Small separable blur computed only on artwork/style changes. */
    private static BufferedImage blur(BufferedImage source) {
        BufferedImage small = new BufferedImage(48, 48, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = small.createGraphics();
        quality(graphics); graphics.drawImage(source, 0, 0, 48, 48, null); graphics.dispose();
        int[] pixels = small.getRGB(0, 0, 48, 48, null, 0, 48), tmp = new int[pixels.length];
        for (int pass = 0; pass < 4; pass++) {
            for (int y = 0; y < 48; y++) for (int x = 0; x < 48; x++) {
                int r = 0, g = 0, b = 0;
                for (int k = -5; k <= 5; k++) {
                    int p = pixels[(pass % 2 == 0 ? y : Math.max(0, Math.min(47, y + k))) * 48
                            + (pass % 2 == 0 ? Math.max(0, Math.min(47, x + k)) : x)];
                    r += (p >> 16) & 255; g += (p >> 8) & 255; b += p & 255;
                }
                tmp[y * 48 + x] = 0xFF000000 | ((r / 11) << 16) | ((g / 11) << 8) | (b / 11);
            }
            int[] swap = pixels; pixels = tmp; tmp = swap;
        }
        small.setRGB(0, 0, 48, 48, pixels, 0, 48); return small;
    }

    public void close() {
        if (surface != null) mc.getTextureManager().deleteTexture(surface);
        surface = null; image = null; trackKey = "";
    }

    private static int opacity(int color, float amount) { return RenderUtils.alpha(color, Math.round((color >>> 24) * amount)); }
    private static String time(long ms) {
        long seconds = Math.max(0, ms / 1000);
        return seconds / 60 + ":" + (seconds % 60 < 10 ? "0" : "") + seconds % 60;
    }
}

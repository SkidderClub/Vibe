package dev.vibe.game.gta8;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;

/** Procedural textures generated once per GL context: noise, foliage, signs, terrain heights, signals. */
final class Gta8Textures {
    int noise, foliage, signs, height, signals;
    static final double HEIGHT_MIN = -40, HEIGHT_SPAN = 300, HEIGHT_EXTENT = 1600;
    static final int HEIGHT_SIZE = 1024;
    private final ByteBuffer signalData = ByteBuffer.allocateDirect(11 * 11 * 4).order(ByteOrder.nativeOrder());

    void create(Gta8World world, SignAtlas atlas) {
        if (noise != 0) return;
        noise = upload(noiseImage(), true, true, false);
        foliage = upload(foliageImage(), true, false, true);
        signs = upload(atlas.image, true, false, true);
        height = heightTexture(world);
        signals = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, signals);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, 11, 11, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
    }

    /** Per intersection: vehicle state north-south, east-west, then walk state across each road. */
    void updateSignals(Gta8World world, double time) {
        if (signals == 0) return;
        signalData.clear();
        for (int j = 0; j < 11; j++) for (int i = 0; i < 11; i++) {
            Gta8World.Signal s = world.signalGrid[i][j];
            signalData.put((byte) (world.signalState(s, Gta8World.NORTH, time) * 127));
            signalData.put((byte) (world.signalState(s, Gta8World.EAST, time) * 127));
            signalData.put((byte) (world.walkState(s, true, time) * 127));
            signalData.put((byte) (world.walkState(s, false, time) * 127));
        }
        signalData.flip();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, signals);
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 11, 11, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, signalData);
    }

    /** R, G: white noise (hardware-filtered value noise). B: tileable fbm. A: tileable cellular billows. */
    static BufferedImage noiseImage() {
        BufferedImage image = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
        double[][] fx = new double[8][8], fz = new double[8][8];
        for (int j = 0; j < 8; j++) for (int i = 0; i < 8; i++) { fx[j][i] = (i + Gta8Math.hash01(i, j, 71)) * 32; fz[j][i] = (j + Gta8Math.hash01(i, j, 73)) * 32; }
        for (int y = 0; y < 256; y++) for (int x = 0; x < 256; x++) {
            int r = Gta8Math.hash(x, y, 11) & 255, g = Gta8Math.hash(x, y, 29) & 255;
            double f = 0, amp = .5, norm = 0;
            for (int o = 0; o < 5; o++) { int period = 4 << o; f += periodic(x * period / 256.0, y * period / 256.0, period, 53 + o) * amp; norm += amp; amp *= .5; }
            int b = (int) Gta8Math.clamp(f / norm * 255, 0, 255);
            double best = 1e9;
            for (int dj = -1; dj <= 1; dj++) for (int di = -1; di <= 1; di++) {
                int ci = ((x / 32 + di) % 8 + 8) % 8, cj = ((y / 32 + dj) % 8 + 8) % 8;
                double px = fx[cj][ci] + (x / 32 + di - ci) * 32 - (x / 32 + di < 0 ? 0 : 0), pz = fz[cj][ci] + (y / 32 + dj - cj) * 32;
                double d = Math.hypot(px - x, pz - y);
                best = Math.min(best, d);
            }
            int a = (int) Gta8Math.clamp(255 - best / 32 * 255, 0, 255);
            image.setRGB(x, y, a << 24 | r << 16 | g << 8 | b);
        }
        return image;
    }
    private static double periodic(double x, double y, int period, int seed) {
        int ix = (int) Math.floor(x), iy = (int) Math.floor(y);
        double tx = x - ix, ty = y - iy;
        tx = tx * tx * (3 - 2 * tx); ty = ty * ty * (3 - 2 * ty);
        double a = Gta8Math.hash01(ix % period, iy % period, seed), b = Gta8Math.hash01((ix + 1) % period, iy % period, seed);
        double c = Gta8Math.hash01(ix % period, (iy + 1) % period, seed), d = Gta8Math.hash01((ix + 1) % period, (iy + 1) % period, seed);
        return Gta8Math.lerp(Gta8Math.lerp(a, b, tx), Gta8Math.lerp(c, d, tx), ty);
    }

    /** 2x2 atlas: broadleaf cluster, palm frond, pine bough, shrub. White-ish so vertex colour tints it. */
    static BufferedImage foliageImage() {
        int size = 1024, cell = 512;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Random r = new Random(4);
        // Cell 0: broadleaf cluster.
        for (int i = 0; i < 520; i++) {
            double a = r.nextDouble() * Math.PI * 2, d = Math.sqrt(r.nextDouble()) * 220;
            double x = 256 + Math.cos(a) * d, y = 256 + Math.sin(a) * d * .9;
            leaf(g, x, y, 16 + r.nextDouble() * 14, r.nextDouble() * Math.PI * 2, shade(r, 170, 225));
        }
        // Cell 1: palm frond, drawn along the card's V axis with a curved rib.
        g.translate(cell, 0);
        for (int side = -1; side <= 1; side += 2) for (int i = 0; i < 46; i++) {
            double t = i / 46.0, y = 30 + t * 450;
            double len = 150 * Math.sin(Math.min(1, t * 1.25) * Math.PI) + 20;
            GeneralPath p = new GeneralPath();
            p.moveTo(256, y);
            p.quadTo(256 + side * len * .5, y + 18, 256 + side * len, y + 42 + t * 30);
            p.quadTo(256 + side * len * .55, y + 26, 256, y + 8);
            g.setColor(shade(r, 175, 230));
            g.fill(p);
        }
        g.setColor(new Color(215, 200, 150));
        g.setStroke(new BasicStroke(7));
        g.drawLine(256, 20, 256, 490);
        g.translate(-cell, cell);
        // Cell 2: pine bough with dense needles.
        for (int i = 0; i < 1800; i++) {
            double t = r.nextDouble(), y = 30 + t * 450, spread = 40 + (1 - Math.abs(t - .55)) * 150;
            double x = 256 + (r.nextDouble() - .5) * 2 * spread * (1 - t * .6);
            g.setColor(shade(r, 140, 200));
            g.setStroke(new BasicStroke(3f));
            double a = Math.PI / 2 + (x < 256 ? -.9 : .9) + (r.nextDouble() - .5) * .5;
            g.drawLine((int) x, (int) y, (int) (x + Math.cos(a) * 22), (int) (y + Math.sin(a) * 22));
        }
        g.translate(cell, 0);
        // Cell 3: shrub / hedge clump.
        for (int i = 0; i < 700; i++) {
            double a = r.nextDouble() * Math.PI * 2, d = Math.sqrt(r.nextDouble()) * 230;
            leaf(g, 256 + Math.cos(a) * d, 256 + Math.sin(a) * d, 9 + r.nextDouble() * 8, r.nextDouble() * 6.3, shade(r, 160, 220));
        }
        g.dispose();
        return image;
    }
    private static Color shade(Random r, int lo, int hi) { int v = lo + r.nextInt(hi - lo); return new Color(v, Math.min(255, v + 12), Math.max(0, v - 18)); }
    private static void leaf(Graphics2D g, double x, double y, double size, double angle, Color c) {
        java.awt.geom.AffineTransform old = g.getTransform();
        g.translate(x, y); g.rotate(angle);
        g.setColor(c);
        g.fill(new Ellipse2D.Double(-size / 2, -size / 4, size, size / 2));
        g.setColor(c.darker());
        g.drawLine((int) (-size / 2), 0, (int) (size / 2), 0);
        g.setTransform(old);
    }

    private static int heightTexture(Gta8World world) {
        int n = HEIGHT_SIZE;
        ShortBuffer data = ByteBuffer.allocateDirect(n * n * 2).order(ByteOrder.nativeOrder()).asShortBuffer();
        for (int y = 0; y < n; y++) for (int x = 0; x < n; x++) {
            double wx = -HEIGHT_EXTENT + (x + .5) * HEIGHT_EXTENT * 2 / n, wz = -HEIGHT_EXTENT + (y + .5) * HEIGHT_EXTENT * 2 / n;
            double h = world.baseGround(wx, wz);
            if (Math.abs(wx) < 520 && Math.abs(wz) < 520 && h >= 0) h = 5;
            data.put((short) (int) Math.round(Gta8Math.clamp((h - HEIGHT_MIN) / HEIGHT_SPAN, 0, 1) * 65535));
        }
        data.flip();
        int id = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 2);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_LUMINANCE16, n, n, 0, GL11.GL_LUMINANCE, GL11.GL_UNSIGNED_SHORT, data);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
        return id;
    }

    static int upload(BufferedImage image, boolean mipmaps, boolean repeat, boolean alphaWeighted) {
        int w = image.getWidth(), h = image.getHeight();
        int[] argb = image.getRGB(0, 0, w, h, null, 0, w);
        int id = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, mipmaps ? GL11.GL_LINEAR_MIPMAP_LINEAR : GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, repeat ? GL11.GL_REPEAT : GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, repeat ? GL11.GL_REPEAT : GL12.GL_CLAMP_TO_EDGE);
        int level = 0;
        while (true) {
            ByteBuffer buffer = ByteBuffer.allocateDirect(w * h * 4);
            for (int p : argb) buffer.put((byte) (p >> 16)).put((byte) (p >> 8)).put((byte) p).put((byte) (p >>> 24));
            buffer.flip();
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, level, GL11.GL_RGBA8, w, h, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);
            if (!mipmaps || (w == 1 && h == 1)) break;
            int nw = Math.max(1, w / 2), nh = Math.max(1, h / 2);
            int[] next = new int[nw * nh];
            for (int y = 0; y < nh; y++) for (int x = 0; x < nw; x++) {
                int a = 0, r = 0, g = 0, b = 0, count = 0;
                for (int dy = 0; dy < 2; dy++) for (int dx = 0; dx < 2; dx++) {
                    int sx = Math.min(w - 1, x * 2 + dx), sy = Math.min(h - 1, y * 2 + dy);
                    int p = argb[sy * w + sx], pa = p >>> 24;
                    int wgt = alphaWeighted ? pa : 1;
                    a += pa; r += (p >> 16 & 255) * wgt; g += (p >> 8 & 255) * wgt; b += (p & 255) * wgt; count++;
                }
                int aa = a / count;
                if (!alphaWeighted) { next[y * nw + x] = aa << 24 | (r / count) << 16 | (g / count) << 8 | (b / count); continue; }
                // Alpha-weighted colour keeps foliage edges from darkening in the mip chain.
                next[y * nw + x] = aa << 24 | (a > 0 ? r / a : 0) << 16 | (a > 0 ? g / a : 0) << 8 | (a > 0 ? b / a : 0);
            }
            argb = next; w = nw; h = nh; level++;
        }
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, level);
        if (org.lwjgl.opengl.GLContext.getCapabilities().GL_EXT_texture_filter_anisotropic)
            GL11.glTexParameterf(GL11.GL_TEXTURE_2D, 0x84FE, 8f);
        return id;
    }

    void close() {
        for (int id : new int[]{noise, foliage, signs, height, signals}) if (id != 0) GL11.glDeleteTextures(id);
        noise = foliage = signs = height = signals = 0;
    }

    /** CPU-side sign atlas: every distinct sign text is rasterised once with Java2D. */
    static final class SignAtlas {
        static final int SHOP = 0, NEON = 1, STREET = 2, BILLBOARD = 3, CIVIC = 4, HOTEL = 5, CROSS = 6;
        final BufferedImage image;
        private final Map<String, float[]> rects = new LinkedHashMap<String, float[]>();
        private final Graphics2D g;
        private int x = 2, y = 2, rowHeight;
        static final int SIZE = 2048;

        SignAtlas() {
            image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
            g = image.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        }
        /** Returns {u0, v0, u1, v1, aspect}. */
        float[] get(int kind, String text) {
            String key = kind + ":" + text;
            float[] r = rects.get(key);
            if (r == null) { r = draw(kind, text); rects.put(key, r); }
            return r;
        }
        private float[] draw(int kind, String text) {
            int h = kind == BILLBOARD ? 200 : kind == HOTEL ? 96 : 72;
            Font font = kind == NEON || kind == HOTEL ? new Font(Font.SERIF, Font.BOLD | Font.ITALIC, (int) (h * .72))
                    : kind == STREET ? new Font(Font.SANS_SERIF, Font.BOLD, (int) (h * .62)) : new Font(Font.SANS_SERIF, Font.BOLD, (int) (h * (kind == BILLBOARD ? .4 : .66)));
            g.setFont(font);
            FontMetrics fm = g.getFontMetrics();
            int tw = fm.stringWidth(text);
            int w = kind == CROSS ? h : kind == BILLBOARD ? Math.max(tw + 80, h * 2 + 200) : tw + (int) (h * .7);
            w = Math.min(w, SIZE - 4);
            if (x + w + 2 > SIZE) { x = 2; y += rowHeight + 3; rowHeight = 0; }
            int ox = x, oy = y;
            x += w + 3; rowHeight = Math.max(rowHeight, h);
            java.awt.Shape clip = g.getClip();
            g.setClip(ox, oy, w, h);
            int tx = ox + (w - tw) / 2, ty = oy + (h - fm.getHeight()) / 2 + fm.getAscent();
            Random r = new Random(text.hashCode());
            switch (kind) {
                case STREET:
                    g.setColor(new Color(0x1F6B45)); g.fillRect(ox, oy, w, h);
                    g.setColor(new Color(0xF4F4EE)); g.setStroke(new BasicStroke(3)); g.drawRect(ox + 4, oy + 4, w - 9, h - 9);
                    g.drawString(text, tx, ty);
                    break;
                case NEON: case HOTEL: {
                    Color glow = Color.getHSBColor(r.nextFloat(), .75f, 1f);
                    g.setColor(new Color(0x16181C)); g.fillRect(ox, oy, w, h);
                    // Alpha carries the emissive mask: bright tubes glow at night.
                    g.setColor(new Color(glow.getRed(), glow.getGreen(), glow.getBlue(), 255));
                    g.drawString(text, tx, ty);
                    for (int i = 0; i < w * h; i++) {
                        int px = ox + i % w, py = oy + i / w;
                        int c = image.getRGB(px, py);
                        boolean lit = (c >> 16 & 255) + (c >> 8 & 255) + (c & 255) > 200;
                        image.setRGB(px, py, (lit ? 0xFF000000 : 0x00000000) | (c & 0xFFFFFF));
                    }
                    break;
                }
                case BILLBOARD: {
                    Color a = Color.getHSBColor(r.nextFloat(), .55f, .85f), b = Color.getHSBColor(r.nextFloat(), .7f, .45f);
                    g.setPaint(new GradientPaint(ox, oy, a, ox + w, oy + h, b)); g.fillRect(ox, oy, w, h);
                    g.setColor(new Color(255, 255, 255, 60));
                    for (int i = 0; i < 6; i++) g.fillOval(ox + r.nextInt(w), oy + r.nextInt(h), 40 + r.nextInt(120), 40 + r.nextInt(120));
                    g.setColor(new Color(0, 0, 0, 90)); g.drawString(text, tx + 4, ty + 4);
                    g.setColor(Color.WHITE); g.drawString(text, tx, ty);
                    g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 26));
                    g.drawString("LOS VIBES", ox + 24, oy + h - 22);
                    fillAlpha(ox, oy, w, h, 110);
                    break;
                }
                case CROSS:
                    g.setColor(Color.WHITE); g.fillRect(ox, oy, w, h);
                    g.setColor(new Color(0xD8262E)); g.fillRect(ox + w / 2 - h / 7, oy + h / 6, h * 2 / 7, h * 2 / 3); g.fillRect(ox + w / 6, oy + h / 2 - h / 7, w * 2 / 3, h * 2 / 7);
                    fillAlpha(ox, oy, w, h, 200);
                    break;
                case CIVIC:
                    g.setColor(new Color(0xE8E8E2)); g.fillRect(ox, oy, w, h);
                    g.setColor(new Color(0x23303C)); g.drawString(text, tx, ty);
                    fillAlpha(ox, oy, w, h, 60);
                    break;
                default: {
                    Color bg = Color.getHSBColor(r.nextFloat(), .45f + r.nextFloat() * .4f, .35f + r.nextFloat() * .45f);
                    g.setColor(bg); g.fillRect(ox, oy, w, h);
                    g.setColor(bg.darker()); g.setStroke(new BasicStroke(4)); g.drawRect(ox + 2, oy + 2, w - 5, h - 5);
                    g.setColor(new Color(0, 0, 0, 80)); g.drawString(text, tx + 2, ty + 2);
                    g.setColor(r.nextBoolean() ? Color.WHITE : new Color(0xFFE8A8)); g.drawString(text, tx, ty);
                    // Back-lit box signs: letters glow, the panel less so.
                    for (int i = 0; i < w * h; i++) {
                        int px = ox + i % w, py = oy + i / w;
                        int c = image.getRGB(px, py);
                        boolean letter = (c >> 16 & 255) > 200 && (c >> 8 & 255) > 200;
                        image.setRGB(px, py, ((letter ? 230 : 40) << 24) | (c & 0xFFFFFF));
                    }
                }
            }
            g.setClip(clip);
            float s = SIZE;
            return new float[]{ox / s, oy / s, (ox + w) / s, (oy + h) / s, w / (float) h};
        }
        private void fillAlpha(int ox, int oy, int w, int h, int alpha) {
            for (int yy = oy; yy < oy + h; yy++) for (int xx = ox; xx < ox + w; xx++) image.setRGB(xx, yy, (alpha << 24) | (image.getRGB(xx, yy) & 0xFFFFFF));
        }
        void finish() { g.dispose(); }
    }
}

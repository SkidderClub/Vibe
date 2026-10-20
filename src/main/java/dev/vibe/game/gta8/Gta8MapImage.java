package dev.vibe.game.gta8;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

/** Satellite-style map of Los Vibes for the radar and the pause-menu map. */
public final class Gta8MapImage {
    public static final double EXTENT = 820;
    public static final int SIZE = 2048;

    private Gta8MapImage() { }

    /** World coordinate to map pixel. */
    public static double px(double x) { return (x + EXTENT) / (EXTENT * 2) * SIZE; }

    public static BufferedImage render(Gta8World w) {
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        // Terrain and water, shaded by slope.
        int step = 4;
        for (int y = 0; y < SIZE; y += step) for (int x = 0; x < SIZE; x += step) {
            double wx = x / (double) SIZE * EXTENT * 2 - EXTENT, wz = y / (double) SIZE * EXTENT * 2 - EXTENT;
            double h = w.baseGround(wx, wz);
            int rgb;
            if (h < Gta8World.WATER - .05) {
                double depth = Gta8Math.clamp((Gta8World.WATER - h) / 14, 0, 1);
                rgb = mix(0x6EA4B8, 0x2E5A78, depth);
            } else if (wx > w.gridX0() - 5 && wx < w.gridX1() + 5 && wz > w.gridZ0() - 5 && wz < w.gridZ1() + 5) rgb = 0x5E6166;
            else {
                double hx = w.baseGround(wx + 3, wz) - h, hz = w.baseGround(wx, wz + 3) - h;
                double shade = Gta8Math.clamp(.85 - hx * .08 + hz * .06, .55, 1.15);
                boolean sand = wz > w.gridZ1() && h < 3 || h < Gta8World.WATER + .6;
                int base = sand ? 0xD8C8A0 : h > 60 ? 0x9A907A : 0x8E9468;
                rgb = scale(base, shade);
            }
            for (int dy = 0; dy < step; dy++) for (int dx = 0; dx < step; dx++) image.setRGB(x + dx, y + dy, 0xFF000000 | rgb);
        }
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        double k = SIZE / (EXTENT * 2);
        // Blocks and their ground finish.
        for (Gta8World.Block[] column : w.blocks) for (Gta8World.Block b : column) {
            double ax0 = Gta8World.line(b.bx) + w.nsHalf(b.bx), ax1 = Gta8World.line(b.bx + 1) - w.nsHalf(b.bx + 1);
            double az0 = Gta8World.line(b.bz) + w.ewHalf(b.bz), az1 = Gta8World.line(b.bz + 1) - w.ewHalf(b.bz + 1);
            fill(g, ax0, az0, ax1, az1, 0xB9B6AE);
            int lot = b.ground == Gta8World.Block.GRASS ? 0x86A45E : b.ground == Gta8World.Block.ASPHALT ? 0x7C7E82 : 0xA8A49A;
            fill(g, b.x0, b.z0, b.x1, b.z1, lot);
        }
        for (Gta8World.Area a : w.areas) {
            if (a.type == Gta8World.Area.NAMED) continue;
            int c = a.type == Gta8World.Area.PARK ? 0x86A45E : a.type == Gta8World.Area.PARKING ? 0x74767A : a.type == Gta8World.Area.DECK ? 0xA88A64 : 0xC2BAA8;
            fill(g, a.x0, a.z0, a.x1, a.z1, c);
        }
        for (Gta8World.Pool p : w.pools) fill(g, p.x0, p.z0, p.x1, p.z1, 0x5EC8E0);
        // Roads with lane markings.
        g.setColor(new Color(0x3E4146));
        for (int i = 0; i < Gta8World.LINES; i++) {
            fill(g, Gta8World.line(i) - w.nsHalf(i), w.gridZ0(), Gta8World.line(i) + w.nsHalf(i), w.gridZ1(), 0x404348);
            fill(g, w.gridX0(), Gta8World.line(i) - w.ewHalf(i), w.gridX1(), Gta8World.line(i) + w.ewHalf(i), 0x404348);
        }
        g.setStroke(new BasicStroke((float) (.35 * k)));
        g.setColor(new Color(0xB8962E));
        for (int i = 0; i < Gta8World.LINES; i++) {
            line(g, Gta8World.line(i), w.gridZ0(), Gta8World.line(i), w.gridZ1());
            line(g, w.gridX0(), Gta8World.line(i), w.gridX1(), Gta8World.line(i));
        }
        // Buildings with height-based shading and drop shadows.
        for (Gta8World.Building b : w.buildings) {
            double h = b.top();
            int shadow = (int) Math.min(18, h * .12 * k);
            g.setColor(new Color(0, 0, 0, 70));
            g.fill(new Rectangle2D.Double(px(b.x0) + shadow * .6, px(b.z0) + shadow * .6, (b.x1 - b.x0) * k, (b.z1 - b.z0) * k));
            int base = b.kind == Gta8World.Building.HOUSE ? 0xC8A088 : b.kind == Gta8World.Building.WAREHOUSE ? 0x9AA0A6 : b.kind == Gta8World.Building.TOWER ? 0x8A96A6 : 0xD8D2C6;
            fill(g, b.x0, b.z0, b.x1, b.z1, scale(base, .8 + Math.min(.35, h / 400)));
        }
        // Harbour apron, pier and the VIBE sign.
        fill(g, w.gridX1() + Gta8World.SIDEWALK, w.gridZ0() - Gta8World.SIDEWALK, Gta8World.QUAY, 560, 0x9C9A96);
        for (Gta8World.Prop p : w.props) {
            if (p.type == Gta8World.Prop.CONTAINER && p.y < 1) fill(g, p.x - 1.3, p.z - 1.3, p.x + 1.3, p.z + 1.3, 0xB05A3A);
            if (p.type == Gta8World.Prop.SHIP) fill(g, p.x - 15, p.z - 100, p.x + 15, p.z + 100, 0x4A3A3A);
            if (p.type == Gta8World.Prop.VIBE_LETTER) fill(g, p.x - 6, p.z - 1.5, p.x + 6, p.z + 1.5, 0xF4F4F0);
        }
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        g.setColor(new Color(255, 255, 255, 190));
        for (int i = 0; i < Gta8World.LINES; i += 2) {
            label(g, w.nsNames[i].toUpperCase(java.util.Locale.ROOT), Gta8World.line(i) + 3, w.gridZ0() + 40, true);
            label(g, w.ewNames[i].toUpperCase(java.util.Locale.ROOT), w.gridX0() + 40, Gta8World.line(i) - 3, false);
        }
        g.dispose();
        return image;
    }
    private static void label(Graphics2D g, String text, double x, double z, boolean vertical) {
        java.awt.geom.AffineTransform old = g.getTransform();
        g.translate(px(x), px(z));
        if (vertical) g.rotate(Math.PI / 2);
        g.drawString(text, 0, 0);
        g.setTransform(old);
    }
    private static void fill(Graphics2D g, double x0, double z0, double x1, double z1, int rgb) {
        g.setColor(new Color(rgb));
        g.fill(new Rectangle2D.Double(px(x0), px(z0), (x1 - x0) * SIZE / (EXTENT * 2), (z1 - z0) * SIZE / (EXTENT * 2)));
    }
    private static void line(Graphics2D g, double x0, double z0, double x1, double z1) {
        g.draw(new java.awt.geom.Line2D.Double(px(x0), px(z0), px(x1), px(z1)));
    }
    static int mix(int a, int b, double t) {
        int r = (int) Gta8Math.lerp(a >> 16 & 255, b >> 16 & 255, t), gg = (int) Gta8Math.lerp(a >> 8 & 255, b >> 8 & 255, t), bb = (int) Gta8Math.lerp(a & 255, b & 255, t);
        return r << 16 | gg << 8 | bb;
    }
    static int scale(int rgb, double k) {
        return (int) Math.min(255, (rgb >> 16 & 255) * k) << 16 | (int) Math.min(255, (rgb >> 8 & 255) * k) << 8 | (int) Math.min(255, (rgb & 255) * k);
    }
}

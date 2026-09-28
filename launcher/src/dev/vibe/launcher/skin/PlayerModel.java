package dev.vibe.launcher.skin;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.RescaleOp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Renders a Minecraft 1.8 player model with Java2D. Every cube face is a
 * parallelogram under the orthographic projection, so an affine transform maps
 * its skin region exactly. Parts are drawn back to front; faces are shaded from
 * a fixed light in view space, with pre-shaded copies cached per face.
 */
public final class PlayerModel {
    private static final double[] LIGHT = normalize(0.35, 0.8, 0.9);
    private static final int SHADES = 12;

    private final List<Part> parts = new ArrayList<Part>();
    private final Part head, rightArm, leftArm, cape;

    public PlayerModel(SkinService.Skin skin) {
        BufferedImage t = skin.texture;
        int arm = skin.slim ? 3 : 4;
        head = part(0, 24, 0, box(t, -4, 24, -4, 8, 8, 8, 0, 0, 0), box(t, -4, 24, -4, 8, 8, 8, 32, 0, 0.5));
        part(0, 24, 0, box(t, -4, 12, -2, 8, 12, 4, 16, 16, 0), box(t, -4, 12, -2, 8, 12, 4, 16, 32, 0.25));
        rightArm = part(-5, 22, 0, box(t, -4 - arm, 12, -2, arm, 12, 4, 40, 16, 0), box(t, -4 - arm, 12, -2, arm, 12, 4, 40, 32, 0.25));
        leftArm = part(5, 22, 0, box(t, 4, 12, -2, arm, 12, 4, 32, 48, 0), box(t, 4, 12, -2, arm, 12, 4, 48, 48, 0.25));
        part(-2, 12, 0, box(t, -4, 0, -2, 4, 12, 4, 0, 16, 0), box(t, -4, 0, -2, 4, 12, 4, 0, 32, 0.25));
        part(2, 12, 0, box(t, 0, 0, -2, 4, 12, 4, 16, 48, 0), box(t, 0, 0, -2, 4, 12, 4, 0, 48, 0.25));
        if (skin.cape != null && skin.cape.getWidth() >= 22 && skin.cape.getHeight() >= 17) {
            Box capeBox = box(skin.cape, -5, 8, -3.3, 10, 16, 1, 0, 0, 0);
            // Minecraft renders the cape turned around, so its outer design faces backwards.
            capeBox.turnAround();
            cape = part(0, 24, -2.3, capeBox, null);
        } else {
            cape = null;
        }
    }

    /**
     * Draws the model centred on ({@code cx}, {@code cy}).
     *
     * @param scale screen pixels per skin pixel
     * @param yaw rotation around the vertical axis in radians
     * @param time animation time in seconds; idle arm swing and cape sway
     * @return screen bounds of the model, used for the ESP overlay
     */
    public Rectangle2D render(Graphics2D g, double cx, double cy, double scale, double yaw, double pitch, double time) {
        pose(time);
        View view = new View(cx, cy, scale, yaw, pitch);
        List<Part> ordered = new ArrayList<Part>(parts);
        for (Part part : ordered) part.depth = view.depth(part.transform(part.base.center()));
        Collections.sort(ordered, (a, b) -> Double.compare(a.depth, b.depth));

        Object interpolation = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        Object antialias = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (Part part : ordered) {
            part.base.draw(g, part, view);
            if (part.overlay != null) part.overlay.draw(g, part, view);
            if (part == cape) continue;
            for (double[] corner : part.base.corners()) {
                double[] p = view.project(part.transform(corner));
                minX = Math.min(minX, p[0]); maxX = Math.max(maxX, p[0]);
                minY = Math.min(minY, p[1]); maxY = Math.max(maxY, p[1]);
            }
        }
        if (interpolation != null) g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
        if (antialias != null) g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, antialias);
        return new Rectangle2D.Double(minX, minY, maxX - minX, maxY - minY);
    }

    private void pose(double time) {
        // Minecraft's idle animation, slightly amplified so it reads at launcher size.
        double swing = Math.sin(time * 1.35) * 0.07, spread = Math.cos(time * 1.8) * 0.05 + 0.06;
        rightArm.rx = swing;
        rightArm.rz = -spread;
        leftArm.rx = -swing;
        leftArm.rz = spread;
        head.ry = Math.sin(time * 0.45) * 0.22;
        head.rx = Math.sin(time * 0.6) * 0.06 - 0.04;
        if (cape != null) cape.rx = 0.16 + Math.sin(time * 1.1) * 0.05;
    }

    private Part part(double px, double py, double pz, Box base, Box overlay) {
        Part part = new Part(px, py, pz, base, overlay);
        parts.add(part);
        return part;
    }

    private static Box box(BufferedImage texture, double x, double y, double z, int w, int h, int d, int u, int v, double inflate) {
        return new Box(texture, x - inflate, y - inflate, z - inflate, x + w + inflate, y + h + inflate, z + d + inflate, u, v, w, h, d);
    }

    // ---- geometry ---------------------------------------------------------

    /** A limb, head or body with its pivot and per-frame rotation. */
    private static final class Part {
        final double px, py, pz;
        final Box base, overlay;
        double rx, ry, rz, depth;

        Part(double px, double py, double pz, Box base, Box overlay) {
            this.px = px; this.py = py; this.pz = pz; this.base = base; this.overlay = overlay;
        }

        double[] transform(double[] point) {
            double x = point[0] - px, y = point[1] - py, z = point[2] - pz;
            double[] r = rotate(x, y, z);
            return new double[] { r[0] + px, r[1] + py, r[2] + pz };
        }

        double[] rotate(double x, double y, double z) {
            // X, then Y, then Z, matching ModelRenderer's order.
            double cos = Math.cos(rx), sin = Math.sin(rx);
            double y1 = y * cos - z * sin, z1 = y * sin + z * cos;
            cos = Math.cos(ry); sin = Math.sin(ry);
            double x2 = x * cos + z1 * sin, z2 = -x * sin + z1 * cos;
            cos = Math.cos(rz); sin = Math.sin(rz);
            return new double[] { x2 * cos - y1 * sin, x2 * sin + y1 * cos, z2 };
        }
    }

    private static final class View {
        final double cx, cy, scale, yawCos, yawSin, pitchCos, pitchSin;

        View(double cx, double cy, double scale, double yaw, double pitch) {
            this.cx = cx; this.cy = cy; this.scale = scale;
            yawCos = Math.cos(yaw); yawSin = Math.sin(yaw);
            pitchCos = Math.cos(pitch); pitchSin = Math.sin(pitch);
        }

        /** Model point to view space, centred on the middle of the 32-pixel-tall player. */
        double[] view(double[] p) {
            double x = p[0], y = p[1] - 16, z = p[2];
            double x1 = x * yawCos + z * yawSin, z1 = -x * yawSin + z * yawCos;
            return new double[] { x1, y * pitchCos - z1 * pitchSin, y * pitchSin + z1 * pitchCos };
        }

        double[] rotateNormal(double[] n) {
            double x1 = n[0] * yawCos + n[2] * yawSin, z1 = -n[0] * yawSin + n[2] * yawCos;
            return new double[] { x1, n[1] * pitchCos - z1 * pitchSin, n[1] * pitchSin + z1 * pitchCos };
        }

        double[] project(double[] p) {
            double[] v = view(p);
            return new double[] { cx + v[0] * scale, cy - v[1] * scale, v[2] };
        }

        double depth(double[] p) { return view(p)[2]; }
    }

    private static final class Box {
        final Face[] faces;
        final double x0, y0, z0, x1, y1, z1;

        Box(BufferedImage texture, double x0, double y0, double z0, double x1, double y1, double z1, int u, int v, int w, int h, int d) {
            this.x0 = x0; this.y0 = y0; this.z0 = z0; this.x1 = x1; this.y1 = y1; this.z1 = z1;
            faces = new Face[] {
                new Face(texture, u + d, v + d, w, h, p(x0, y1, z1), p(x1, y1, z1), p(x0, y0, z1), 0, 0, 1),
                new Face(texture, u + 2 * d + w, v + d, w, h, p(x1, y1, z0), p(x0, y1, z0), p(x1, y0, z0), 0, 0, -1),
                new Face(texture, u, v + d, d, h, p(x0, y1, z0), p(x0, y1, z1), p(x0, y0, z0), -1, 0, 0),
                new Face(texture, u + d + w, v + d, d, h, p(x1, y1, z1), p(x1, y1, z0), p(x1, y0, z1), 1, 0, 0),
                new Face(texture, u + d, v, w, d, p(x0, y1, z0), p(x1, y1, z0), p(x0, y1, z1), 0, 1, 0),
                new Face(texture, u + d + w, v, w, d, p(x0, y0, z1), p(x1, y0, z1), p(x0, y0, z0), 0, -1, 0),
            };
        }

        /** Rotates the box 180 degrees about its vertical centre line. */
        void turnAround() {
            double cx = (x0 + x1) / 2, cz = (z0 + z1) / 2;
            for (Face face : faces) {
                for (double[] corner : face.corners) { corner[0] = 2 * cx - corner[0]; corner[2] = 2 * cz - corner[2]; }
                face.normal[0] = -face.normal[0];
                face.normal[2] = -face.normal[2];
            }
        }

        double[] center() { return p((x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2); }

        double[][] corners() {
            return new double[][] { p(x0, y0, z0), p(x1, y0, z0), p(x0, y1, z0), p(x1, y1, z0),
                    p(x0, y0, z1), p(x1, y0, z1), p(x0, y1, z1), p(x1, y1, z1) };
        }

        void draw(Graphics2D g, Part part, View view) {
            for (Face face : faces) face.draw(g, part, view);
        }
    }

    private static final class Face {
        final double[][] corners;
        final double[] normal;
        final BufferedImage image;
        final BufferedImage[] shaded = new BufferedImage[SHADES];

        Face(BufferedImage texture, int u, int v, int w, int h, double[] topLeft, double[] topRight, double[] bottomLeft, double nx, double ny, double nz) {
            corners = new double[][] { topLeft, topRight, bottomLeft };
            normal = new double[] { nx, ny, nz };
            image = region(texture, u, v, w, h);
        }

        void draw(Graphics2D g, Part part, View view) {
            if (image == null) return;
            double[] n = view.rotateNormal(part.rotate(normal[0], normal[1], normal[2]));
            if (n[2] <= 1e-4) return;
            double light = Math.max(0, n[0] * LIGHT[0] + n[1] * LIGHT[1] + n[2] * LIGHT[2]);
            int level = (int) Math.round(light * (SHADES - 1));
            double[] p0 = view.project(part.transform(corners[0]));
            double[] p1 = view.project(part.transform(corners[1]));
            double[] p3 = view.project(part.transform(corners[2]));
            // Grow each face by about half a pixel so neighbouring faces meet without cracks.
            double ux = p1[0] - p0[0], uy = p1[1] - p0[1], vx = p3[0] - p0[0], vy = p3[1] - p0[1];
            double lu = Math.max(1e-6, Math.hypot(ux, uy)), lv = Math.max(1e-6, Math.hypot(vx, vy));
            double gu = 0.6 / lu, gv = 0.6 / lv;
            double ox = p0[0] - ux * gu / 2 - vx * gv / 2, oy = p0[1] - uy * gu / 2 - vy * gv / 2;
            int w = image.getWidth(), h = image.getHeight();
            AffineTransform transform = new AffineTransform(ux * (1 + gu) / w, uy * (1 + gu) / w, vx * (1 + gv) / h, vy * (1 + gv) / h, ox, oy);
            g.drawImage(shade(Math.max(0, Math.min(SHADES - 1, level))), transform, null);
        }

        private BufferedImage shade(int level) {
            if (level == SHADES - 1) return image;
            BufferedImage cached = shaded[level];
            if (cached == null) {
                float factor = (float) (0.52 + 0.48 * level / (SHADES - 1));
                cached = new RescaleOp(new float[] { factor, factor, factor, 1f }, new float[4], null).filter(image, null);
                shaded[level] = cached;
            }
            return cached;
        }

        private static BufferedImage region(BufferedImage texture, int u, int v, int w, int h) {
            if (w <= 0 || h <= 0 || u < 0 || v < 0 || u + w > texture.getWidth() || v + h > texture.getHeight()) return null;
            BufferedImage copy = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            copy.setRGB(0, 0, w, h, texture.getRGB(u, v, w, h, null, 0, w), 0, w);
            return copy;
        }
    }

    private static double[] p(double x, double y, double z) { return new double[] { x, y, z }; }

    private static double[] normalize(double x, double y, double z) {
        double length = Math.sqrt(x * x + y * y + z * z);
        return new double[] { x / length, y / length, z / length };
    }
}

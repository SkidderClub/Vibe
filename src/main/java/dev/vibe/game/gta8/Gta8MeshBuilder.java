package dev.vibe.game.gta8;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.Arrays;

/**
 * CPU mesh assembly with a small transform stack. Vertex layout (32 bytes):
 * position float3, normal byte3 (+pad), colour ubyte4 (alpha = ambient occlusion),
 * surface UV float2 in metres, material ubyte4 (id, seed, parameter, bone/extra).
 */
final class Gta8MeshBuilder {
    static final int STRIDE = 32;
    private byte[] data = new byte[STRIDE * 1024];
    private int[] indices = new int[1536];
    int vertices, indexCount;
    private int material, seed, param, extra, rgb = 0xFFFFFF, ao = 255;
    private final float[] matrix = Gta8Math.identity(new float[16]);
    private final float[][] stack = new float[16][16];
    private int depth;
    private boolean identity = true;
    private final float[] temp = new float[16], temp2 = new float[16];
    double minX = Double.POSITIVE_INFINITY, minY = minX, minZ = minX, maxX = Double.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;

    // ------------------------------------------------------------ state
    Gta8MeshBuilder material(int id) { material = id & 255; return this; }
    Gta8MeshBuilder material(int id, int parameter) { material = id & 255; param = parameter & 255; return this; }
    Gta8MeshBuilder param(int value) { param = value & 255; return this; }
    Gta8MeshBuilder seed(int value) { seed = value & 255; return this; }
    Gta8MeshBuilder extra(int value) { extra = value & 255; return this; }
    Gta8MeshBuilder bone(int value) { extra = value & 255; return this; }
    Gta8MeshBuilder color(int value) { rgb = value & 0xFFFFFF; return this; }
    Gta8MeshBuilder ao(double value) { ao = (int) Gta8Math.clamp(value * 255, 0, 255); return this; }
    int color() { return rgb; }
    int material() { return material; }
    int param() { return param; }

    void push() { System.arraycopy(matrix, 0, stack[depth++], 0, 16); }
    void pop() { System.arraycopy(stack[--depth], 0, matrix, 0, 16); identity = isIdentity(); }
    void reset() { Gta8Math.identity(matrix); depth = 0; identity = true; }
    void translate(double x, double y, double z) { apply(Gta8Math.translation(temp, x, y, z)); }
    void rotateY(double degrees) { if (degrees != 0) apply(Gta8Math.rotationY(temp, -degrees)); }
    /** Rotation used for yaw: 0 faces -Z, 90 faces +X. */
    void yaw(double degrees) { if (degrees != 0) apply(Gta8Math.rotationY(temp, -degrees)); }
    void rotateX(double degrees) { if (degrees != 0) apply(Gta8Math.rotationX(temp, degrees)); }
    void rotateZ(double degrees) { if (degrees != 0) apply(Gta8Math.rotationZ(temp, degrees)); }
    void scale(double x, double y, double z) { Gta8Math.identity(temp); temp[0] = (float) x; temp[5] = (float) y; temp[10] = (float) z; apply(temp); }
    private void apply(float[] m) { Gta8Math.multiply(matrix, m, temp2); System.arraycopy(temp2, 0, matrix, 0, 16); identity = isIdentity(); }
    private boolean isIdentity() {
        for (int i = 0; i < 16; i++) if (matrix[i] != (i % 5 == 0 ? 1 : 0)) return false;
        return true;
    }

    // ------------------------------------------------------------ primitives
    int vertex(double x, double y, double z, double nx, double ny, double nz, double u, double v) {
        if (!identity) {
            double tx = matrix[0] * x + matrix[4] * y + matrix[8] * z + matrix[12];
            double ty = matrix[1] * x + matrix[5] * y + matrix[9] * z + matrix[13];
            double tz = matrix[2] * x + matrix[6] * y + matrix[10] * z + matrix[14];
            double mx = matrix[0] * nx + matrix[4] * ny + matrix[8] * nz;
            double my = matrix[1] * nx + matrix[5] * ny + matrix[9] * nz;
            double mz = matrix[2] * nx + matrix[6] * ny + matrix[10] * nz;
            x = tx; y = ty; z = tz; nx = mx; ny = my; nz = mz;
        }
        double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len > 1e-9) { nx /= len; ny /= len; nz /= len; }
        if (vertices * STRIDE + STRIDE > data.length) data = Arrays.copyOf(data, data.length * 2);
        int o = vertices * STRIDE;
        putFloat(o, (float) x); putFloat(o + 4, (float) y); putFloat(o + 8, (float) z);
        data[o + 12] = (byte) Math.round(nx * 127); data[o + 13] = (byte) Math.round(ny * 127); data[o + 14] = (byte) Math.round(nz * 127); data[o + 15] = 0;
        data[o + 16] = (byte) (rgb >> 16); data[o + 17] = (byte) (rgb >> 8); data[o + 18] = (byte) rgb; data[o + 19] = (byte) ao;
        putFloat(o + 20, (float) u); putFloat(o + 24, (float) v);
        data[o + 28] = (byte) material; data[o + 29] = (byte) seed; data[o + 30] = (byte) param; data[o + 31] = (byte) extra;
        if (x < minX) minX = x; if (y < minY) minY = y; if (z < minZ) minZ = z;
        if (x > maxX) maxX = x; if (y > maxY) maxY = y; if (z > maxZ) maxZ = z;
        return vertices++;
    }
    private void putFloat(int offset, float value) {
        int bits = Float.floatToRawIntBits(value);
        data[offset] = (byte) bits; data[offset + 1] = (byte) (bits >> 8); data[offset + 2] = (byte) (bits >> 16); data[offset + 3] = (byte) (bits >> 24);
    }
    void triangle(int a, int b, int c) {
        if (indexCount + 3 > indices.length) indices = Arrays.copyOf(indices, indices.length * 2);
        indices[indexCount++] = a; indices[indexCount++] = b; indices[indexCount++] = c;
    }
    /** Counter-clockwise quad (seen from the front). */
    void quadIndices(int a, int b, int c, int d) { triangle(a, b, c); triangle(a, c, d); }

    /** Planar quad with explicit UVs; the normal is derived from the winding. */
    void quad(double x0, double y0, double z0, double x1, double y1, double z1, double x2, double y2, double z2, double x3, double y3, double z3,
              double u0, double v0, double u1, double v1, double u2, double v2, double u3, double v3) {
        double ax = x1 - x0, ay = y1 - y0, az = z1 - z0, bx = x3 - x0, by = y3 - y0, bz = z3 - z0;
        double nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
        int a = vertex(x0, y0, z0, nx, ny, nz, u0, v0), b = vertex(x1, y1, z1, nx, ny, nz, u1, v1);
        int c = vertex(x2, y2, z2, nx, ny, nz, u2, v2), d = vertex(x3, y3, z3, nx, ny, nz, u3, v3);
        quadIndices(a, b, c, d);
    }
    void tri(double x0, double y0, double z0, double x1, double y1, double z1, double x2, double y2, double z2, double u0, double v0, double u1, double v1, double u2, double v2) {
        double ax = x1 - x0, ay = y1 - y0, az = z1 - z0, bx = x2 - x0, by = y2 - y0, bz = z2 - z0;
        double nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
        triangle(vertex(x0, y0, z0, nx, ny, nz, u0, v0), vertex(x1, y1, z1, nx, ny, nz, u1, v1), vertex(x2, y2, z2, nx, ny, nz, u2, v2));
    }

    static final int FACE_N = 1, FACE_E = 2, FACE_S = 4, FACE_W = 8, FACE_UP = 16, FACE_DOWN = 32, ALL = 63, SIDES = 15;

    /** Axis-aligned box in the current frame; side UVs run left-to-right as seen from outside, v = height above y0 + vOffset. */
    void box(double x0, double y0, double z0, double x1, double y1, double z1) { box(x0, y0, z0, x1, y1, z1, ALL, 0); }
    void box(double x0, double y0, double z0, double x1, double y1, double z1, int faces) { box(x0, y0, z0, x1, y1, z1, faces, 0); }
    void box(double x0, double y0, double z0, double x1, double y1, double z1, int faces, double vOffset) {
        double h0 = vOffset, h1 = vOffset + (y1 - y0);
        if ((faces & FACE_N) != 0) quad(x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, 0, h0, x1 - x0, h0, x1 - x0, h1, 0, h1);
        if ((faces & FACE_S) != 0) quad(x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, 0, h0, x1 - x0, h0, x1 - x0, h1, 0, h1);
        if ((faces & FACE_W) != 0) quad(x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, 0, h0, z1 - z0, h0, z1 - z0, h1, 0, h1);
        if ((faces & FACE_E) != 0) quad(x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, 0, h0, z1 - z0, h0, z1 - z0, h1, 0, h1);
        if ((faces & FACE_UP) != 0) quad(x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, x0, z1, x1, z1, x1, z0, x0, z0);
        if ((faces & FACE_DOWN) != 0) quad(x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, x0, z0, x1, z0, x1, z1, x0, z1);
    }
    /** Box centred on (cx, cz) with its base at y. */
    void centered(double cx, double y, double cz, double sx, double sy, double sz) { box(cx - sx / 2, y, cz - sz / 2, cx + sx / 2, y + sy, cz + sz / 2); }

    /** A box with rounded vertical edges (chamfered in plan), good for furniture and bodies. */
    void rounded(double x0, double y0, double z0, double x1, double y1, double z1, double r, int segments) {
        r = Math.min(r, Math.min((x1 - x0) / 2, (z1 - z0) / 2));
        int n = segments * 4 + 4;
        double[] px = new double[n], pz = new double[n];
        double[][] centers = {{x1 - r, z0 + r}, {x1 - r, z1 - r}, {x0 + r, z1 - r}, {x0 + r, z0 + r}};
        int k = 0;
        for (int c = 0; c < 4; c++) for (int s = 0; s <= segments; s++) {
            double a = Math.toRadians(-90 + c * 90 + s * 90.0 / segments);
            px[k] = centers[c][0] + Math.cos(a) * r; pz[k] = centers[c][1] + Math.sin(a) * r; k++;
        }
        prism(px, pz, k, y0, y1, true, true);
    }
    /** Vertical prism from a clockwise-in-screen (counter-clockwise from above, +Z south) outline. */
    void prism(double[] px, double[] pz, int n, double y0, double y1, boolean top, boolean bottom) {
        double u = 0;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            double len = Math.hypot(px[j] - px[i], pz[j] - pz[i]);
            double nx = pz[j] - pz[i], nz = -(px[j] - px[i]);
            int a = vertex(px[i], y0, pz[i], nx, 0, nz, u, 0), b = vertex(px[j], y0, pz[j], nx, 0, nz, u + len, 0);
            int c = vertex(px[j], y1, pz[j], nx, 0, nz, u + len, y1 - y0), d = vertex(px[i], y1, pz[i], nx, 0, nz, u, y1 - y0);
            quadIndices(b, a, d, c);
            u += len;
        }
        if (top) {
            int center = vertex(avg(px, n), y1, avg(pz, n), 0, 1, 0, avg(px, n), avg(pz, n));
            int first = -1, prev = -1;
            for (int i = 0; i <= n; i++) {
                int v = i < n ? vertex(px[i], y1, pz[i], 0, 1, 0, px[i], pz[i]) : first;
                if (i == 0) first = v; else triangle(center, v, prev);
                prev = v;
            }
        }
        if (bottom) {
            int center = vertex(avg(px, n), y0, avg(pz, n), 0, -1, 0, avg(px, n), avg(pz, n));
            int first = -1, prev = -1;
            for (int i = 0; i <= n; i++) {
                int v = i < n ? vertex(px[i], y0, pz[i], 0, -1, 0, px[i], pz[i]) : first;
                if (i == 0) first = v; else triangle(center, prev, v);
                prev = v;
            }
        }
    }
    private static double avg(double[] v, int n) { double s = 0; for (int i = 0; i < n; i++) s += v[i]; return s / n; }

    /** Tapered cylinder along +Y. */
    void cylinder(double cx, double y, double cz, double r0, double r1, double h, int segments, boolean caps) {
        double slope = (r0 - r1) / Math.max(1e-6, h);
        int base = vertices;
        for (int i = 0; i <= segments; i++) {
            double a = i * Math.PI * 2 / segments, c = Math.cos(a), s = Math.sin(a);
            vertex(cx + c * r0, y, cz + s * r0, c, slope, s, i * Math.PI * 2 * r0 / segments, 0);
            vertex(cx + c * r1, y + h, cz + s * r1, c, slope, s, i * Math.PI * 2 * r0 / segments, h);
        }
        for (int i = 0; i < segments; i++) { int a = base + i * 2; quadIndices(a + 2, a, a + 1, a + 3); }
        if (caps) { disc(cx, y + h, cz, r1, segments, true); if (r0 > 0) disc(cx, y, cz, r0, segments, false); }
    }
    void disc(double cx, double y, double cz, double r, int segments, boolean up) {
        int center = vertex(cx, y, cz, 0, up ? 1 : -1, 0, cx, cz);
        int first = -1, prev = -1;
        for (int i = 0; i <= segments; i++) {
            double a = i * Math.PI * 2 / segments;
            int v = i < segments ? vertex(cx + Math.cos(a) * r, y, cz + Math.sin(a) * r, 0, up ? 1 : -1, 0, cx + Math.cos(a) * r, cz + Math.sin(a) * r) : first;
            if (i == 0) first = v; else if (up) triangle(center, v, prev); else triangle(center, prev, v);
            prev = v;
        }
    }
    /** Cylinder between two arbitrary points (pipes, arms, limbs). */
    void tube(double x0, double y0, double z0, double x1, double y1, double z1, double r0, double r1, int segments, boolean caps) {
        double dx = x1 - x0, dy = y1 - y0, dz = z1 - z0, len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-6) return;
        push();
        translate(x0, y0, z0);
        // Rotate +Y onto the tube direction.
        double yaw = Math.toDegrees(Math.atan2(dx, dz)), pitch = Math.toDegrees(Math.acos(Gta8Math.clamp(dy / len, -1, 1)));
        rotateY(-yaw);
        rotateX(pitch);
        cylinder(0, 0, 0, r0, r1, len, segments, caps);
        pop();
    }
    /** UV sphere / ellipsoid. */
    void sphere(double cx, double cy, double cz, double rx, double ry, double rz, int rings, int segments) {
        int base = vertices;
        for (int j = 0; j <= rings; j++) {
            double p = -Math.PI / 2 + j * Math.PI / rings, cp = Math.cos(p), sp = Math.sin(p);
            for (int i = 0; i <= segments; i++) {
                double a = i * Math.PI * 2 / segments, ca = Math.cos(a), sa = Math.sin(a);
                vertex(cx + cp * ca * rx, cy + sp * ry, cz + cp * sa * rz, cp * ca / rx, sp / ry, cp * sa / rz, i / (double) segments * Math.PI * 2 * rx, (j / (double) rings) * Math.PI * ry);
            }
        }
        for (int j = 0; j < rings; j++) for (int i = 0; i < segments; i++) {
            int a = base + j * (segments + 1) + i, b = a + segments + 1;
            quadIndices(a + 1, a, b, b + 1);
        }
    }
    /** Surface of revolution around +Y: radius/height pairs from bottom to top. */
    void lathe(double cx, double cy, double cz, double[] radius, double[] height, int segments) {
        int base = vertices;
        for (int j = 0; j < radius.length; j++) {
            double dr = (j + 1 < radius.length ? radius[j + 1] : radius[j]) - (j > 0 ? radius[j - 1] : radius[j]);
            double dh = (j + 1 < radius.length ? height[j + 1] : height[j]) - (j > 0 ? height[j - 1] : height[j]);
            for (int i = 0; i <= segments; i++) {
                double a = i * Math.PI * 2 / segments, c = Math.cos(a), s = Math.sin(a);
                vertex(cx + c * radius[j], cy + height[j], cz + s * radius[j], c * dh, -dr, s * dh, i / (double) segments * 6.283 * radius[0], height[j]);
            }
        }
        for (int j = 0; j + 1 < radius.length; j++) for (int i = 0; i < segments; i++) {
            int a = base + j * (segments + 1) + i, b = a + segments + 1;
            quadIndices(a + 1, a, b, b + 1);
        }
    }
    /** Tyre: a rounded ring around the local X axis. */
    void tyre(double radius, double width, double wall, int segments) {
        int rings = 6;
        int base = vertices;
        for (int j = 0; j <= rings; j++) {
            double t = j / (double) rings, ang = -Math.PI / 2 + t * Math.PI;
            double x = Math.sin(ang) * width / 2, r = radius - wall + Math.cos(ang) * wall;
            double nxl = Math.sin(ang), nr = Math.cos(ang);
            for (int i = 0; i <= segments; i++) {
                double a = i * Math.PI * 2 / segments, c = Math.cos(a), s = Math.sin(a);
                vertex(x, c * r, s * r, nxl, c * nr, s * nr, i * 6.283 * radius / segments, t * width);
            }
        }
        for (int j = 0; j < rings; j++) for (int i = 0; i < segments; i++) {
            int a = base + j * (segments + 1) + i, b = a + segments + 1;
            quadIndices(a, a + 1, b + 1, b);
        }
    }
    /** Double-sided card (foliage, fences); UV 0..1 over the card. */
    void card(double x0, double y0, double z0, double x1, double y1, double z1, double x2, double y2, double z2, double x3, double y3, double z3) {
        quad(x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3, 0, 0, 1, 0, 1, 1, 0, 1);
        quad(x1, y1, z1, x0, y0, z0, x3, y3, z3, x2, y2, z2, 1, 0, 0, 0, 0, 1, 1, 1);
    }

    boolean isEmpty() { return indexCount == 0; }
    void clear() {
        vertices = indexCount = 0; reset();
        minX = minY = minZ = Double.POSITIVE_INFINITY; maxX = maxY = maxZ = Double.NEGATIVE_INFINITY;
    }
    ByteBuffer vertexBuffer() {
        ByteBuffer buffer = ByteBuffer.allocateDirect(vertices * STRIDE).order(ByteOrder.nativeOrder());
        if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) buffer.put(data, 0, vertices * STRIDE);
        else for (int v = 0; v < vertices; v++) {
            int o = v * STRIDE;
            for (int f = 0; f < 3; f++) buffer.putFloat(Float.intBitsToFloat(le(o + f * 4)));
            buffer.put(data, o + 12, 8);
            buffer.putFloat(Float.intBitsToFloat(le(o + 20))); buffer.putFloat(Float.intBitsToFloat(le(o + 24)));
            buffer.put(data, o + 28, 4);
        }
        buffer.flip();
        return buffer;
    }
    private int le(int o) { return (data[o] & 255) | (data[o + 1] & 255) << 8 | (data[o + 2] & 255) << 16 | (data[o + 3] & 255) << 24; }
    IntBuffer indexBuffer() {
        IntBuffer buffer = ByteBuffer.allocateDirect(indexCount * 4).order(ByteOrder.nativeOrder()).asIntBuffer();
        buffer.put(indices, 0, indexCount).flip();
        return buffer;
    }
    /** Vertex position for tests and CPU-side bounds checks. */
    float x(int v) { return Float.intBitsToFloat(le(v * STRIDE)); }
    float y(int v) { return Float.intBitsToFloat(le(v * STRIDE + 4)); }
    float z(int v) { return Float.intBitsToFloat(le(v * STRIDE + 8)); }
    int materialOf(int v) { return data[v * STRIDE + 28] & 255; }
}

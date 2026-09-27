package dev.vibe.game.gta8;

/** Allocation-free matrix, noise and interpolation helpers shared by simulation and rendering. */
public final class Gta8Math {
    private Gta8Math() { }

    public static double clamp(double v, double lo, double hi) { return v < lo ? lo : v > hi ? hi : v; }
    public static float clamp(float v, float lo, float hi) { return v < lo ? lo : v > hi ? hi : v; }
    public static double lerp(double a, double b, double t) { return a + (b - a) * t; }
    public static double smooth(double e0, double e1, double x) { double t = clamp((x - e0) / (e1 - e0), 0, 1); return t * t * (3 - 2 * t); }
    /** Shortest signed angle from a to b in degrees. */
    public static double angleDelta(double a, double b) { double d = (b - a) % 360; if (d > 180) d -= 360; if (d < -180) d += 360; return d; }
    public static double wrap(double degrees) { double d = degrees % 360; return d < 0 ? d + 360 : d; }
    public static double approach(double value, double target, double step) { return value < target ? Math.min(target, value + step) : Math.max(target, value - step); }
    public static double damp(double value, double target, double rate, double dt) { return target + (value - target) * Math.exp(-rate * dt); }

    // ---- Deterministic hashing and value noise ------------------------------------------------
    public static int hash(int x, int y, int seed) {
        int h = x * 374761393 + y * 668265263 + seed * 1442695041;
        h = (h ^ (h >>> 13)) * 1274126177;
        return h ^ (h >>> 16);
    }
    public static double hash01(int x, int y, int seed) { return (hash(x, y, seed) & 0xFFFFFF) / (double) 0x1000000; }
    public static double noise(double x, double y, int seed) {
        int ix = (int) Math.floor(x), iy = (int) Math.floor(y);
        double fx = x - ix, fy = y - iy;
        double ux = fx * fx * (3 - 2 * fx), uy = fy * fy * (3 - 2 * fy);
        double a = hash01(ix, iy, seed), b = hash01(ix + 1, iy, seed), c = hash01(ix, iy + 1, seed), d = hash01(ix + 1, iy + 1, seed);
        return lerp(lerp(a, b, ux), lerp(c, d, ux), uy);
    }
    public static double fbm(double x, double y, int octaves, int seed) {
        double sum = 0, amp = .5, norm = 0;
        for (int i = 0; i < octaves; i++) { sum += noise(x, y, seed + i * 31) * amp; norm += amp; amp *= .5; x *= 2.03; y *= 2.03; }
        return sum / norm;
    }
    /** Sharp mountain ridges: folded noise emphasises crests without discontinuities. */
    public static double ridge(double x, double y, int octaves, int seed) {
        double sum = 0, amp = .5, norm = 0;
        for (int i = 0; i < octaves; i++) {
            double n = 1 - Math.abs(noise(x, y, seed + i * 17) * 2 - 1);
            sum += n * n * amp; norm += amp; amp *= .5; x *= 2.01; y *= 2.01;
        }
        return sum / norm;
    }

    // ---- Column-major 4x4 matrices, as expected by glUniformMatrix4 --------------------------
    public static float[] identity(float[] m) {
        for (int i = 0; i < 16; i++) m[i] = 0;
        m[0] = m[5] = m[10] = m[15] = 1; return m;
    }
    public static float[] multiply(float[] a, float[] b, float[] out) {
        float[] r = out == a || out == b ? new float[16] : out;
        for (int c = 0; c < 4; c++) for (int row = 0; row < 4; row++) {
            r[c * 4 + row] = a[row] * b[c * 4] + a[4 + row] * b[c * 4 + 1] + a[8 + row] * b[c * 4 + 2] + a[12 + row] * b[c * 4 + 3];
        }
        if (r != out) System.arraycopy(r, 0, out, 0, 16);
        return out;
    }
    public static float[] perspective(float[] m, double fovY, double aspect, double near, double far) {
        double f = 1 / Math.tan(Math.toRadians(fovY) / 2);
        identity(m);
        m[0] = (float) (f / aspect); m[5] = (float) f;
        m[10] = (float) ((far + near) / (near - far)); m[11] = -1;
        m[14] = (float) (2 * far * near / (near - far)); m[15] = 0;
        return m;
    }
    public static float[] ortho(float[] m, double l, double r, double b, double t, double n, double f) {
        identity(m);
        m[0] = (float) (2 / (r - l)); m[5] = (float) (2 / (t - b)); m[10] = (float) (-2 / (f - n));
        m[12] = (float) (-(r + l) / (r - l)); m[13] = (float) (-(t + b) / (t - b)); m[14] = (float) (-(f + n) / (f - n));
        return m;
    }
    /** View matrix for an eye at (ex,ey,ez) looking along the given unit forward vector. */
    public static float[] lookDirection(float[] m, double ex, double ey, double ez, double fx, double fy, double fz, double ux, double uy, double uz) {
        double sx = fy * uz - fz * uy, sy = fz * ux - fx * uz, sz = fx * uy - fy * ux;
        double sl = Math.sqrt(sx * sx + sy * sy + sz * sz); if (sl < 1e-9) { sx = 1; sy = sz = 0; sl = 1; }
        sx /= sl; sy /= sl; sz /= sl;
        double vx = sy * fz - sz * fy, vy = sz * fx - sx * fz, vz = sx * fy - sy * fx;
        identity(m);
        m[0] = (float) sx; m[4] = (float) sy; m[8] = (float) sz;
        m[1] = (float) vx; m[5] = (float) vy; m[9] = (float) vz;
        m[2] = (float) -fx; m[6] = (float) -fy; m[10] = (float) -fz;
        m[12] = (float) -(sx * ex + sy * ey + sz * ez);
        m[13] = (float) -(vx * ex + vy * ey + vz * ez);
        m[14] = (float) (fx * ex + fy * ey + fz * ez);
        return m;
    }
    public static float[] translation(float[] m, double x, double y, double z) { identity(m); m[12] = (float) x; m[13] = (float) y; m[14] = (float) z; return m; }
    /** Model matrix: translate, then yaw (degrees, 0 = -Z), then pitch about local X and roll about local Z. */
    public static float[] pose(float[] m, double x, double y, double z, double yaw, double pitch, double roll) {
        double cy = Math.cos(Math.toRadians(-yaw)), sy = Math.sin(Math.toRadians(-yaw));
        double cp = Math.cos(Math.toRadians(pitch)), sp = Math.sin(Math.toRadians(pitch));
        double cr = Math.cos(Math.toRadians(roll)), sr = Math.sin(Math.toRadians(roll));
        // R = Ry * Rx * Rz
        double r00 = cy * cr + sy * sp * sr, r01 = -cy * sr + sy * sp * cr, r02 = sy * cp;
        double r10 = cp * sr, r11 = cp * cr, r12 = -sp;
        double r20 = -sy * cr + cy * sp * sr, r21 = sy * sr + cy * sp * cr, r22 = cy * cp;
        m[0] = (float) r00; m[1] = (float) r10; m[2] = (float) r20; m[3] = 0;
        m[4] = (float) r01; m[5] = (float) r11; m[6] = (float) r21; m[7] = 0;
        m[8] = (float) r02; m[9] = (float) r12; m[10] = (float) r22; m[11] = 0;
        m[12] = (float) x; m[13] = (float) y; m[14] = (float) z; m[15] = 1;
        return m;
    }
    public static float[] rotationX(float[] m, double degrees) {
        double c = Math.cos(Math.toRadians(degrees)), s = Math.sin(Math.toRadians(degrees));
        identity(m); m[5] = (float) c; m[6] = (float) s; m[9] = (float) -s; m[10] = (float) c; return m;
    }
    public static float[] rotationY(float[] m, double degrees) {
        double c = Math.cos(Math.toRadians(degrees)), s = Math.sin(Math.toRadians(degrees));
        identity(m); m[0] = (float) c; m[2] = (float) -s; m[8] = (float) s; m[10] = (float) c; return m;
    }
    public static float[] rotationZ(float[] m, double degrees) {
        double c = Math.cos(Math.toRadians(degrees)), s = Math.sin(Math.toRadians(degrees));
        identity(m); m[0] = (float) c; m[1] = (float) s; m[4] = (float) -s; m[5] = (float) c; return m;
    }
    public static float[] invert(float[] m, float[] out) {
        double[] inv = new double[16];
        inv[0] = m[5]*m[10]*m[15]-m[5]*m[11]*m[14]-m[9]*m[6]*m[15]+m[9]*m[7]*m[14]+m[13]*m[6]*m[11]-m[13]*m[7]*m[10];
        inv[4] = -m[4]*m[10]*m[15]+m[4]*m[11]*m[14]+m[8]*m[6]*m[15]-m[8]*m[7]*m[14]-m[12]*m[6]*m[11]+m[12]*m[7]*m[10];
        inv[8] = m[4]*m[9]*m[15]-m[4]*m[11]*m[13]-m[8]*m[5]*m[15]+m[8]*m[7]*m[13]+m[12]*m[5]*m[11]-m[12]*m[7]*m[9];
        inv[12] = -m[4]*m[9]*m[14]+m[4]*m[10]*m[13]+m[8]*m[5]*m[14]-m[8]*m[6]*m[13]-m[12]*m[5]*m[10]+m[12]*m[6]*m[9];
        inv[1] = -m[1]*m[10]*m[15]+m[1]*m[11]*m[14]+m[9]*m[2]*m[15]-m[9]*m[3]*m[14]-m[13]*m[2]*m[11]+m[13]*m[3]*m[10];
        inv[5] = m[0]*m[10]*m[15]-m[0]*m[11]*m[14]-m[8]*m[2]*m[15]+m[8]*m[3]*m[14]+m[12]*m[2]*m[11]-m[12]*m[3]*m[10];
        inv[9] = -m[0]*m[9]*m[15]+m[0]*m[11]*m[13]+m[8]*m[1]*m[15]-m[8]*m[3]*m[13]-m[12]*m[1]*m[11]+m[12]*m[3]*m[9];
        inv[13] = m[0]*m[9]*m[14]-m[0]*m[10]*m[13]-m[8]*m[1]*m[14]+m[8]*m[2]*m[13]+m[12]*m[1]*m[10]-m[12]*m[2]*m[9];
        inv[2] = m[1]*m[6]*m[15]-m[1]*m[7]*m[14]-m[5]*m[2]*m[15]+m[5]*m[3]*m[14]+m[13]*m[2]*m[7]-m[13]*m[3]*m[6];
        inv[6] = -m[0]*m[6]*m[15]+m[0]*m[7]*m[14]+m[4]*m[2]*m[15]-m[4]*m[3]*m[14]-m[12]*m[2]*m[7]+m[12]*m[3]*m[6];
        inv[10] = m[0]*m[5]*m[15]-m[0]*m[7]*m[13]-m[4]*m[1]*m[15]+m[4]*m[3]*m[13]+m[12]*m[1]*m[7]-m[12]*m[3]*m[5];
        inv[14] = -m[0]*m[5]*m[14]+m[0]*m[6]*m[13]+m[4]*m[1]*m[14]-m[4]*m[2]*m[13]-m[12]*m[1]*m[6]+m[12]*m[2]*m[5];
        inv[3] = -m[1]*m[6]*m[11]+m[1]*m[7]*m[10]+m[5]*m[2]*m[11]-m[5]*m[3]*m[10]-m[9]*m[2]*m[7]+m[9]*m[3]*m[6];
        inv[7] = m[0]*m[6]*m[11]-m[0]*m[7]*m[10]-m[4]*m[2]*m[11]+m[4]*m[3]*m[10]+m[8]*m[2]*m[7]-m[8]*m[3]*m[6];
        inv[11] = -m[0]*m[5]*m[11]+m[0]*m[7]*m[9]+m[4]*m[1]*m[11]-m[4]*m[3]*m[9]-m[8]*m[1]*m[7]+m[8]*m[3]*m[5];
        inv[15] = m[0]*m[5]*m[10]-m[0]*m[6]*m[9]-m[4]*m[1]*m[10]+m[4]*m[2]*m[9]+m[8]*m[1]*m[6]-m[8]*m[2]*m[5];
        double det = m[0]*inv[0]+m[1]*inv[4]+m[2]*inv[8]+m[3]*inv[12];
        if (Math.abs(det) < 1e-30) return identity(out);
        det = 1 / det;
        for (int i = 0; i < 16; i++) out[i] = (float) (inv[i] * det);
        return out;
    }
    /** Transforms a point by a column-major matrix (w assumed 1, no divide). */
    public static void transform(float[] m, double x, double y, double z, double[] out) {
        out[0] = m[0] * x + m[4] * y + m[8] * z + m[12];
        out[1] = m[1] * x + m[5] * y + m[9] * z + m[13];
        out[2] = m[2] * x + m[6] * y + m[10] * z + m[14];
    }
    /** Clip-space projection with perspective divide; returns false behind the camera. */
    public static boolean project(float[] viewProjection, double x, double y, double z, double[] ndc) {
        double cx = viewProjection[0] * x + viewProjection[4] * y + viewProjection[8] * z + viewProjection[12];
        double cy = viewProjection[1] * x + viewProjection[5] * y + viewProjection[9] * z + viewProjection[13];
        double cz = viewProjection[2] * x + viewProjection[6] * y + viewProjection[10] * z + viewProjection[14];
        double cw = viewProjection[3] * x + viewProjection[7] * y + viewProjection[11] * z + viewProjection[15];
        if (cw <= 1e-4) return false;
        ndc[0] = cx / cw; ndc[1] = cy / cw; ndc[2] = cz / cw; return true;
    }
}

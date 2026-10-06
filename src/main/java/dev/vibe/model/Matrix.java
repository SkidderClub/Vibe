package dev.vibe.model;

/** Minimal column-major 4x4 matrix helpers, matching glTF's and OpenGL's layout. */
final class Matrix {

    private Matrix() {
    }

    static double[] identity() {
        double[] m = new double[16];
        m[0] = m[5] = m[10] = m[15] = 1.0D;
        return m;
    }

    static double[] multiply(double[] a, double[] b) {
        double[] result = new double[16];
        for (int column = 0; column < 4; column++) {
            for (int row = 0; row < 4; row++) {
                double sum = 0.0D;
                for (int k = 0; k < 4; k++) sum += a[k * 4 + row] * b[column * 4 + k];
                result[column * 4 + row] = sum;
            }
        }
        return result;
    }

    static double[] translation(double x, double y, double z) {
        double[] m = identity();
        m[12] = x;
        m[13] = y;
        m[14] = z;
        return m;
    }

    static double[] scale(double x, double y, double z) {
        double[] m = identity();
        m[0] = x;
        m[5] = y;
        m[10] = z;
        return m;
    }

    /** Rotation of {@code degrees} counter-clockwise around a unit axis. */
    static double[] rotation(double degrees, double x, double y, double z) {
        double radians = Math.toRadians(degrees), c = Math.cos(radians), s = Math.sin(radians), t = 1.0D - c;
        double[] m = identity();
        m[0] = t * x * x + c;
        m[1] = t * x * y + s * z;
        m[2] = t * x * z - s * y;
        m[4] = t * x * y - s * z;
        m[5] = t * y * y + c;
        m[6] = t * y * z + s * x;
        m[8] = t * x * z + s * y;
        m[9] = t * y * z - s * x;
        m[10] = t * z * z + c;
        return m;
    }

    /** glTF node transform: T * R * S with R from an (x, y, z, w) quaternion. */
    static double[] fromTrs(double[] t, double[] q, double[] s) {
        double x = q[0], y = q[1], z = q[2], w = q[3];
        double length = Math.sqrt(x * x + y * y + z * z + w * w);
        if (length > 1.0E-12D) {
            x /= length;
            y /= length;
            z /= length;
            w /= length;
        } else {
            w = 1.0D;
        }
        double[] m = new double[16];
        m[0] = (1 - 2 * (y * y + z * z)) * s[0];
        m[1] = (2 * (x * y + z * w)) * s[0];
        m[2] = (2 * (x * z - y * w)) * s[0];
        m[4] = (2 * (x * y - z * w)) * s[1];
        m[5] = (1 - 2 * (x * x + z * z)) * s[1];
        m[6] = (2 * (y * z + x * w)) * s[1];
        m[8] = (2 * (x * z + y * w)) * s[2];
        m[9] = (2 * (y * z - x * w)) * s[2];
        m[10] = (1 - 2 * (x * x + y * y)) * s[2];
        m[12] = t[0];
        m[13] = t[1];
        m[14] = t[2];
        m[15] = 1.0D;
        return m;
    }

    /** Builds a matrix whose columns are the given basis vectors, i.e. maps local axes to those vectors. */
    static double[] basis(double[] x, double[] y, double[] z) {
        double[] m = identity();
        m[0] = x[0];
        m[1] = x[1];
        m[2] = x[2];
        m[4] = y[0];
        m[5] = y[1];
        m[6] = y[2];
        m[8] = z[0];
        m[9] = z[1];
        m[10] = z[2];
        return m;
    }

    /** Inverse transpose of the upper 3x3, column-major, for transforming normals. */
    static double[] normalMatrix(double[] m) {
        double a = m[0], b = m[4], c = m[8];
        double d = m[1], e = m[5], f = m[9];
        double g = m[2], h = m[6], i = m[10];
        double ca = e * i - f * h, cb = -(d * i - f * g), cc = d * h - e * g;
        double determinant = a * ca + b * cb + c * cc;
        if (Math.abs(determinant) < 1.0E-30D) return new double[] {1, 0, 0, 0, 1, 0, 0, 0, 1};
        double inv = 1.0D / determinant;
        // The cofactor matrix divided by the determinant is the inverse transpose,
        // stored here column by column: (C00, C10, C20), (C01, C11, C21), (C02, C12, C22).
        return new double[] {
                ca * inv, -(b * i - c * h) * inv, (b * f - c * e) * inv,
                cb * inv, (a * i - c * g) * inv, -(a * f - c * d) * inv,
                cc * inv, -(a * h - b * g) * inv, (a * e - b * d) * inv};
    }

    static double[] transformPoint(double[] m, double x, double y, double z) {
        return new double[] {
                m[0] * x + m[4] * y + m[8] * z + m[12],
                m[1] * x + m[5] * y + m[9] * z + m[13],
                m[2] * x + m[6] * y + m[10] * z + m[14]};
    }
}

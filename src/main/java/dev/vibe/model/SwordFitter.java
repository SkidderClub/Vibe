package dev.vibe.model;

/**
 * Places an arbitrary knife or sword mesh where vanilla's sword sprite is: in the item's unit
 * square with the handle in the lower-left corner and the tip towards the upper right. Because
 * every Minecraft item transform (first person, third person, blocking) is applied around that
 * square, the custom model is then held exactly like a sword.
 *
 * <p>Orientation comes from principal component analysis: the longest axis is the blade, the
 * flattest axis is the blade's face normal. The tip is the end whose cross-section is thinner,
 * since a blade is flatter than its grip.
 */
public final class SwordFitter {

    /** Length of the fitted model along the sprite diagonal, in item units (the sprite spans ~1.24). */
    public static final double LENGTH = 1.15D;
    /** Handle end on the sprite's lower-left corner, centred in the 1/16 thick sprite. */
    public static final double[] ANCHOR = {0.07D, 0.07D, 0.5D};
    private static final double[] U = {Math.sqrt(0.5D), Math.sqrt(0.5D), 0.0D};
    private static final double[] V = {-Math.sqrt(0.5D), Math.sqrt(0.5D), 0.0D};
    private static final double[] W = {0.0D, 0.0D, 1.0D};

    private SwordFitter() {
    }

    public static PreparedModel prepare(MeshData mesh, int triangleBudget) {
        int source = mesh.triangleCount();
        mesh.transform(fit(mesh));
        PreparedModel.Lod lod = Simplifier.build(mesh, triangleBudget, 1, null, null);
        float[] anchors = {
                (float) (ANCHOR[0] + U[0] * LENGTH * 0.22D), (float) (ANCHOR[1] + U[1] * LENGTH * 0.22D), (float) ANCHOR[2],
                (float) (ANCHOR[0] + U[0] * LENGTH), (float) (ANCHOR[1] + U[1] * LENGTH), (float) ANCHOR[2]};
        return new PreparedModel(mesh.materials, new PreparedModel.Lod[] {lod}, null, anchors, source, null);
    }

    /** The column-major matrix that maps the mesh into item space. */
    static double[] fit(MeshData mesh) {
        Moments moments = Moments.of(mesh);
        double[][] axes = moments.principalAxes();
        double[] u = axes[0], w = axes[2], v = cross(w, u);
        // Extents along the blade axis, measured on vertices so thin tips are not cut short.
        double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
        for (MeshData.Primitive primitive : mesh.primitives) {
            float[] p = primitive.positions;
            for (int index = 0; index < p.length; index += 3) {
                double s = (p[index] - moments.mean[0]) * u[0] + (p[index + 1] - moments.mean[1]) * u[1]
                        + (p[index + 2] - moments.mean[2]) * u[2];
                min = Math.min(min, s);
                max = Math.max(max, s);
            }
        }
        double length = max - min;
        if (!(length > 1.0E-9D)) {
            return Matrix.translation(ANCHOR[0], ANCHOR[1], ANCHOR[2]);
        }
        EndStats handle = moments.end(u, v, w, min, min + length * 0.3D);
        EndStats tip = moments.end(u, v, w, max - length * 0.3D, max);
        if (handle.thickness < tip.thickness) {
            // The thin blade end is on the low side: turn the model around its face normal.
            u = scale(u, -1);
            v = scale(v, -1);
            double oldMin = min;
            min = -max;
            max = -oldMin;
            EndStats blade = handle;
            handle = new EndStats(tip.thickness, -tip.side);
            tip = new EndStats(blade.thickness, -blade.side);
        }
        // Deterministic roll: the blade's bulk leans towards +v (the sprite's upper left).
        if (tip.side < handle.side) {
            v = scale(v, -1);
            w = scale(w, -1);
        }
        double k = LENGTH / length;
        double[] origin = {moments.mean[0] + u[0] * min, moments.mean[1] + u[1] * min, moments.mean[2] + u[2] * min};
        // Rows of the local frame: a = (p - origin)·u, b = (p - mean)·v, c = (p - mean)·w.
        double[] toLocal = Matrix.identity();
        toLocal[0] = u[0];
        toLocal[4] = u[1];
        toLocal[8] = u[2];
        toLocal[1] = v[0];
        toLocal[5] = v[1];
        toLocal[9] = v[2];
        toLocal[2] = w[0];
        toLocal[6] = w[1];
        toLocal[10] = w[2];
        toLocal[12] = -(u[0] * origin[0] + u[1] * origin[1] + u[2] * origin[2]);
        toLocal[13] = -(v[0] * moments.mean[0] + v[1] * moments.mean[1] + v[2] * moments.mean[2]);
        toLocal[14] = -(w[0] * moments.mean[0] + w[1] * moments.mean[1] + w[2] * moments.mean[2]);
        double[] toItem = Matrix.multiply(Matrix.translation(ANCHOR[0], ANCHOR[1], ANCHOR[2]),
                Matrix.multiply(Matrix.scale(k, k, k), Matrix.basis(U, V, W)));
        return Matrix.multiply(toItem, toLocal);
    }

    private static double[] cross(double[] a, double[] b) {
        double[] c = {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
        double length = Math.sqrt(c[0] * c[0] + c[1] * c[1] + c[2] * c[2]);
        return length > 1.0E-12D ? scale(c, 1.0D / length) : new double[] {0, 1, 0};
    }

    private static double[] scale(double[] a, double factor) {
        return new double[] {a[0] * factor, a[1] * factor, a[2] * factor};
    }

    static final class EndStats {
        /** Area-weighted spread across the face normal: small for a flat blade. */
        final double thickness;
        /** Area-weighted mean offset along v. */
        final double side;

        EndStats(double thickness, double side) {
            this.thickness = thickness;
            this.side = side;
        }
    }

    /** Area-weighted triangle centroid statistics. */
    static final class Moments {
        final double[] mean = new double[3];
        final double[] covariance = new double[9];
        private final MeshData mesh;

        private Moments(MeshData mesh) {
            this.mesh = mesh;
        }

        static Moments of(MeshData mesh) {
            Moments moments = new Moments(mesh);
            double total = 0.0D;
            double[] sum = new double[3];
            for (MeshData.Primitive primitive : mesh.primitives) {
                float[] p = primitive.positions;
                int[] indices = primitive.indices;
                for (int index = 0; index + 2 < indices.length; index += 3) {
                    double[] centroid = new double[3];
                    double area = triangle(p, indices[index], indices[index + 1], indices[index + 2], centroid);
                    total += area;
                    for (int axis = 0; axis < 3; axis++) sum[axis] += centroid[axis] * area;
                }
            }
            if (total <= 1.0E-18D) return uniform(mesh);
            for (int axis = 0; axis < 3; axis++) moments.mean[axis] = sum[axis] / total;
            for (MeshData.Primitive primitive : mesh.primitives) {
                float[] p = primitive.positions;
                int[] indices = primitive.indices;
                for (int index = 0; index + 2 < indices.length; index += 3) {
                    double[] centroid = new double[3];
                    double area = triangle(p, indices[index], indices[index + 1], indices[index + 2], centroid) / total;
                    double dx = centroid[0] - moments.mean[0], dy = centroid[1] - moments.mean[1], dz = centroid[2] - moments.mean[2];
                    moments.add(dx, dy, dz, area);
                }
            }
            return moments;
        }

        private static Moments uniform(MeshData mesh) {
            Moments moments = new Moments(mesh);
            long count = 0;
            for (MeshData.Primitive primitive : mesh.primitives) {
                float[] p = primitive.positions;
                for (int index = 0; index < p.length; index += 3) {
                    for (int axis = 0; axis < 3; axis++) moments.mean[axis] += p[index + axis];
                    count++;
                }
            }
            if (count == 0) return moments;
            for (int axis = 0; axis < 3; axis++) moments.mean[axis] /= count;
            for (MeshData.Primitive primitive : mesh.primitives) {
                float[] p = primitive.positions;
                for (int index = 0; index < p.length; index += 3) {
                    moments.add(p[index] - moments.mean[0], p[index + 1] - moments.mean[1], p[index + 2] - moments.mean[2], 1.0D / count);
                }
            }
            return moments;
        }

        private void add(double dx, double dy, double dz, double weight) {
            covariance[0] += dx * dx * weight;
            covariance[1] += dx * dy * weight;
            covariance[2] += dx * dz * weight;
            covariance[4] += dy * dy * weight;
            covariance[5] += dy * dz * weight;
            covariance[8] += dz * dz * weight;
            covariance[3] = covariance[1];
            covariance[6] = covariance[2];
            covariance[7] = covariance[5];
        }

        /** Unit eigenvectors sorted by descending variance. */
        double[][] principalAxes() {
            return eigenvectors(covariance);
        }

        /** Statistics of triangles whose centroid lies within [from, to] along u. */
        EndStats end(double[] u, double[] v, double[] w, double from, double to) {
            double total = 0, sumW = 0, sumW2 = 0, sumV = 0;
            for (MeshData.Primitive primitive : mesh.primitives) {
                float[] p = primitive.positions;
                int[] indices = primitive.indices;
                for (int index = 0; index + 2 < indices.length; index += 3) {
                    double[] centroid = new double[3];
                    double area = triangle(p, indices[index], indices[index + 1], indices[index + 2], centroid);
                    double dx = centroid[0] - mean[0], dy = centroid[1] - mean[1], dz = centroid[2] - mean[2];
                    double s = dx * u[0] + dy * u[1] + dz * u[2];
                    if (s < from || s > to || area <= 0) continue;
                    double depth = dx * w[0] + dy * w[1] + dz * w[2];
                    total += area;
                    sumW += depth * area;
                    sumW2 += depth * depth * area;
                    sumV += (dx * v[0] + dy * v[1] + dz * v[2]) * area;
                }
            }
            if (total <= 0) return new EndStats(Double.MAX_VALUE, 0);
            double meanW = sumW / total;
            return new EndStats(Math.sqrt(Math.max(0, sumW2 / total - meanW * meanW)), sumV / total);
        }
    }

    /** Returns the triangle's area and writes its centroid into {@code centroid}. */
    static double triangle(float[] p, int a, int b, int c, double[] centroid) {
        a *= 3;
        b *= 3;
        c *= 3;
        double ux = p[b] - p[a], uy = p[b + 1] - p[a + 1], uz = p[b + 2] - p[a + 2];
        double vx = p[c] - p[a], vy = p[c + 1] - p[a + 1], vz = p[c + 2] - p[a + 2];
        double nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
        centroid[0] = (p[a] + p[b] + p[c]) / 3.0D;
        centroid[1] = (p[a + 1] + p[b + 1] + p[c + 1]) / 3.0D;
        centroid[2] = (p[a + 2] + p[b + 2] + p[c + 2]) / 3.0D;
        return 0.5D * Math.sqrt(nx * nx + ny * ny + nz * nz);
    }

    /** Jacobi eigen decomposition of a symmetric 3x3 matrix (row-major); vectors sorted by eigenvalue, largest first. */
    static double[][] eigenvectors(double[] matrix) {
        double[][] a = {
                {matrix[0], matrix[1], matrix[2]},
                {matrix[3], matrix[4], matrix[5]},
                {matrix[6], matrix[7], matrix[8]}};
        double[][] vectors = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};
        for (int sweep = 0; sweep < 64; sweep++) {
            double off = Math.abs(a[0][1]) + Math.abs(a[0][2]) + Math.abs(a[1][2]);
            if (off < 1.0E-15D) break;
            for (int p = 0; p < 2; p++) {
                for (int q = p + 1; q < 3; q++) {
                    if (Math.abs(a[p][q]) < 1.0E-300D) continue;
                    double theta = (a[q][q] - a[p][p]) / (2.0D * a[p][q]);
                    double t = Math.signum(theta) / (Math.abs(theta) + Math.sqrt(theta * theta + 1.0D));
                    if (theta == 0.0D) t = 1.0D;
                    double c = 1.0D / Math.sqrt(t * t + 1.0D), s = t * c;
                    for (int k = 0; k < 3; k++) {
                        double akp = a[k][p], akq = a[k][q];
                        a[k][p] = c * akp - s * akq;
                        a[k][q] = s * akp + c * akq;
                    }
                    for (int k = 0; k < 3; k++) {
                        double apk = a[p][k], aqk = a[q][k];
                        a[p][k] = c * apk - s * aqk;
                        a[q][k] = s * apk + c * aqk;
                    }
                    for (int k = 0; k < 3; k++) {
                        double vkp = vectors[k][p], vkq = vectors[k][q];
                        vectors[k][p] = c * vkp - s * vkq;
                        vectors[k][q] = s * vkp + c * vkq;
                    }
                }
            }
        }
        Integer[] order = {0, 1, 2};
        final double[] values = {a[0][0], a[1][1], a[2][2]};
        java.util.Arrays.sort(order, (x, y) -> Double.compare(values[y], values[x]));
        double[][] result = new double[3][];
        for (int index = 0; index < 3; index++) {
            int column = order[index];
            double[] vector = {vectors[0][column], vectors[1][column], vectors[2][column]};
            double length = Math.sqrt(vector[0] * vector[0] + vector[1] * vector[1] + vector[2] * vector[2]);
            result[index] = scale(vector, length > 0 ? 1.0D / length : 1.0D);
        }
        // Make the frame right-handed so the fitted model is never mirrored.
        double[] expected = cross(result[0], result[1]);
        if (expected[0] * result[2][0] + expected[1] * result[2][1] + expected[2] * result[2][2] < 0) {
            result[2] = scale(result[2], -1);
        }
        return result;
    }
}

package dev.vibe.ui;

/** Double-precision homogeneous clipping; returns GUI bounds or null outside the view. */
public final class BoxProjection {
    private BoxProjection() { }
    public static double[] bounds(double[] min, double[] max, float[] model, float[] projection, double width, double height) {
        double[][] corners = new double[8][];
        for (int i = 0; i < 8; i++) corners[i] = transform(projection, transform(model,
                new double[] {(i & 1) == 0 ? min[0] : max[0], (i & 2) == 0 ? min[1] : max[1],
                        (i & 4) == 0 ? min[2] : max[2], 1}));
        double[] bounds = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
        for (int i = 0; i < 8; i++) for (int axis = 1; axis <= 4; axis <<= 1) {
            if ((i & axis) != 0) continue;
            double[] a = corners[i], b = corners[i | axis];
            double start = 0, end = 1;
            boolean visible = true;
            for (int plane = 0; plane < 6; plane++) {
                int coordinate = plane / 2;
                double sign = plane % 2 == 0 ? 1 : -1;
                double fa = a[3] + sign * a[coordinate], fb = b[3] + sign * b[coordinate];
                if (fa < 0 && fb < 0) { visible = false; break; }
                if ((fa < 0) != (fb < 0)) {
                    double t = fa / (fa - fb);
                    if (fa < 0) start = Math.max(start, t); else end = Math.min(end, t);
                }
            }
            if (!visible || start > end) continue;
            include(bounds, a, b, start, width, height);
            include(bounds, a, b, end, width, height);
        }
        return Double.isFinite(bounds[0]) ? bounds : null;
    }
    private static double[] transform(float[] matrix, double[] point) {
        double[] result = new double[4];
        for (int row = 0; row < 4; row++) for (int column = 0; column < 4; column++)
            result[row] += matrix[column * 4 + row] * point[column];
        return result;
    }
    private static void include(double[] bounds, double[] a, double[] b, double t, double width, double height) {
        double w = a[3] + (b[3] - a[3]) * t;
        if (w <= 1e-7) return;
        double x = ((a[0] + (b[0] - a[0]) * t) / w + 1) * width * .5;
        double y = (1 - (a[1] + (b[1] - a[1]) * t) / w) * height * .5;
        if (!Double.isFinite(x) || !Double.isFinite(y)) return;
        bounds[0] = Math.min(bounds[0], x); bounds[1] = Math.min(bounds[1], y);
        bounds[2] = Math.max(bounds[2], x); bounds[3] = Math.max(bounds[3], y);
    }
}

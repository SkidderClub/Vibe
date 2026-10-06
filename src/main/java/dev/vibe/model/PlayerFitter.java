package dev.vibe.model;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

/**
 * Turns a character mesh into a six-part ModelBiped replacement. The mesh is stood upright,
 * turned to face forward, scaled to the player's 32 pixel height and split into head, body,
 * arms and legs, each with the joint it rotates around. Vanilla's own limb angles then animate
 * it, so walking, swinging, sneaking, bow aiming and head turning all work without bones.
 *
 * <p>Rigged files are split by their skin weights and use their real joint positions. Static
 * meshes are split by humanoid proportions measured on the mesh (neck, crotch, torso width).
 */
public final class PlayerFitter {

    /** Model height in ModelBiped pixels: the vanilla player spans y = -8 (head top) to 24 (feet). */
    public static final float HEIGHT = 32.0F;
    private static final int PARTS = BodyPart.values().length;

    private PlayerFitter() {
    }

    /** Proportions measured on the upright, normalised mesh (feet at y = 0, height {@link #HEIGHT}, facing +Z). */
    static final class Layout {
        float torsoHalfWidth;
        float neck;
        float crotch;

        BodyPart classify(float x, float y) {
            float t = y / HEIGHT;
            if (Math.abs(x) > torsoHalfWidth && t > crotch - 0.05F) return x < 0 ? BodyPart.RIGHT_ARM : BodyPart.LEFT_ARM;
            if (t >= neck) return BodyPart.HEAD;
            if (t < crotch) return x < 0 ? BodyPart.RIGHT_LEG : BodyPart.LEFT_LEG;
            return BodyPart.BODY;
        }
    }

    /**
     * @param yawDegrees  extra turn applied after automatic facing detection
     * @param fixPose     lowers T-pose and A-pose arms so they hang like vanilla's
     */
    public static PreparedModel prepare(MeshData mesh, int triangleBudget, double yawDegrees, boolean fixPose) {
        int source = mesh.triangleCount();
        standUpright(mesh);
        double facing = facingDegrees(mesh);
        mesh.transform(Matrix.rotation(facing + yawDegrees, 0, 1, 0));
        normalise(mesh);
        final Layout layout = layout(mesh);
        final boolean skinned = mesh.hasPartHints();
        Simplifier.Classifier classifier = new Simplifier.Classifier() {
            @Override
            public int classify(MeshData.Primitive primitive, int a, int b, int c) {
                if (primitive.parts != null) {
                    byte pa = primitive.parts[a], pb = primitive.parts[b], pc = primitive.parts[c];
                    byte majority = pa == pb || pa == pc ? pa : pb == pc ? pb : pa;
                    if (majority >= 0) return majority;
                }
                float[] p = primitive.positions;
                float x = (p[a * 3] + p[b * 3] + p[c * 3]) / 3.0F;
                float y = (p[a * 3 + 1] + p[b * 3 + 1] + p[c * 3 + 1]) / 3.0F;
                return layout.classify(x, y).ordinal();
            }
        };
        float[][] pivots = pivots(mesh, layout);
        double[][] rest = new double[PARTS][];
        double[] toBiped = toBiped();
        StringBuilder notes = new StringBuilder(skinned ? "rig" : "proportions");
        for (BodyPart part : BodyPart.values()) {
            double[] correction = Matrix.identity();
            if (fixPose && part.isArm()) {
                double angle = armRaise(mesh, layout, part, pivots[part.ordinal()]);
                if (angle > 25.0D) {
                    float[] pivot = pivots[part.ordinal()];
                    double turn = (angle - 5.0D) * (part == BodyPart.LEFT_ARM ? -1.0D : 1.0D);
                    correction = Matrix.multiply(Matrix.translation(pivot[0], pivot[1], pivot[2]),
                            Matrix.multiply(Matrix.rotation(turn, 0, 0, 1), Matrix.translation(-pivot[0], -pivot[1], -pivot[2])));
                    notes.append(", lowered ").append(part == BodyPart.LEFT_ARM ? "left" : "right").append(" arm by ")
                            .append(Math.round(angle - 5.0D)).append(" degrees");
                }
            }
            rest[part.ordinal()] = Matrix.multiply(toBiped, correction);
        }
        PreparedModel.Lod near = Simplifier.build(mesh, Math.max(500, triangleBudget), PARTS, classifier, rest);
        // Distant players use a fifth of the triangles; small models need no second level.
        int farBudget = Math.max(1500, near.triangles / 5);
        PreparedModel.Lod[] lods = farBudget >= near.triangles ? new PreparedModel.Lod[] {near}
                : new PreparedModel.Lod[] {near, Simplifier.build(mesh, farBudget, PARTS, classifier, rest)};
        float[][] bipedPivots = new float[PARTS][];
        for (int part = 0; part < PARTS; part++) {
            double[] p = Matrix.transformPoint(toBiped, pivots[part][0], pivots[part][1], pivots[part][2]);
            bipedPivots[part] = new float[] {(float) p[0], (float) p[1], (float) p[2]};
        }
        return new PreparedModel(mesh.materials, lods, bipedPivots, null, source, notes.toString());
    }

    /** Maps the normalised space (y up, facing +Z, feet at 0) to ModelBiped's (y down, facing -Z, feet at 24). */
    static double[] toBiped() {
        double[] m = Matrix.identity();
        m[5] = -1.0D;
        m[10] = -1.0D;
        m[13] = 24.0D;
        return m;
    }

    /** OBJ files have no defined up axis; a clearly taller Z extent means a Z-up export. */
    static void standUpright(MeshData mesh) {
        if (mesh.definedUpAxis) return;
        float[] bounds = mesh.bounds();
        float x = bounds[3] - bounds[0], y = bounds[4] - bounds[1], z = bounds[5] - bounds[2];
        if (z > y * 1.25F && z >= x) mesh.transform(Matrix.rotation(-90.0D, 1, 0, 0));
    }

    /** Degrees to turn around +Y so the character faces +Z. */
    static double facingDegrees(MeshData mesh) {
        float[] left = mesh.jointPivots.get(BodyPart.LEFT_ARM), right = mesh.jointPivots.get(BodyPart.RIGHT_ARM);
        if (left != null && right != null) {
            double lx = left[0] - right[0], lz = left[2] - right[2];
            if (lx * lx + lz * lz > 1.0E-12D) {
                // forward = left × up = (-lz, 0, lx)
                return -Math.toDegrees(Math.atan2(-lz, lx));
            }
        }
        float[] bounds = mesh.bounds();
        double height = bounds[4] - bounds[1];
        if (height <= 0) return 0.0D;
        // Area-weighted triangle centroids work for low-poly meshes whose long faces have no
        // vertices inside a height band.
        double[] centroid = new double[3];
        double legMinX = Double.MAX_VALUE, legMaxX = -Double.MAX_VALUE, legMinZ = Double.MAX_VALUE, legMaxZ = -Double.MAX_VALUE;
        double[] feet = new double[3], legs = new double[3];
        for (MeshData.Primitive primitive : mesh.primitives) {
            int[] indices = primitive.indices;
            for (int index = 0; index + 2 < indices.length; index += 3) {
                double area = SwordFitter.triangle(primitive.positions, indices[index], indices[index + 1], indices[index + 2], centroid);
                double t = (centroid[1] - bounds[1]) / height;
                if (t < 0.06D) {
                    feet[0] += centroid[0] * area;
                    feet[1] += centroid[2] * area;
                    feet[2] += area;
                } else if (t > 0.10D && t < 0.42D) {
                    legs[0] += centroid[0] * area;
                    legs[1] += centroid[2] * area;
                    legs[2] += area;
                    legMinX = Math.min(legMinX, centroid[0]);
                    legMaxX = Math.max(legMaxX, centroid[0]);
                    legMinZ = Math.min(legMinZ, centroid[2]);
                    legMaxZ = Math.max(legMaxZ, centroid[2]);
                }
            }
        }
        // Legs stand side by side, so the lower body is wider than deep across the facing direction.
        boolean sideways = legs[2] > 0 ? (legMaxZ - legMinZ) > (legMaxX - legMinX) * 1.15D
                : (bounds[5] - bounds[2]) > (bounds[3] - bounds[0]) * 1.15F;
        int axis = sideways ? 0 : 1;
        // Toes stick out in front of the shins.
        double sign = 1.0D;
        if (feet[2] > 0 && legs[2] > 0) {
            double difference = feet[axis] / feet[2] - legs[axis] / legs[2];
            if (Math.abs(difference) > height * 0.01D) sign = Math.signum(difference);
        }
        if (sideways) return sign > 0 ? -90.0D : 90.0D;
        return sign > 0 ? 0.0D : 180.0D;
    }

    /** Puts the feet at y = 0, centres the torso on the y axis and scales to {@link #HEIGHT}. */
    static void normalise(MeshData mesh) {
        float[] bounds = mesh.bounds();
        double height = bounds[4] - bounds[1];
        if (!(height > 1.0E-9D)) height = 1.0D;
        double sumX = 0, sumZ = 0, total = 0;
        double[] centroid = new double[3];
        for (MeshData.Primitive primitive : mesh.primitives) {
            int[] indices = primitive.indices;
            for (int index = 0; index + 2 < indices.length; index += 3) {
                double area = SwordFitter.triangle(primitive.positions, indices[index], indices[index + 1], indices[index + 2], centroid);
                double t = (centroid[1] - bounds[1]) / height;
                if (t < 0.45D || t > 0.75D) continue;
                sumX += centroid[0] * area;
                sumZ += centroid[2] * area;
                total += area;
            }
        }
        double centreX = total > 0 ? sumX / total : (bounds[0] + bounds[3]) * 0.5D;
        double centreZ = total > 0 ? sumZ / total : (bounds[2] + bounds[5]) * 0.5D;
        double scale = HEIGHT / height;
        mesh.transform(Matrix.multiply(Matrix.scale(scale, scale, scale), Matrix.translation(-centreX, -bounds[1], -centreZ)));
    }

    static Layout layout(MeshData mesh) {
        Layout layout = new Layout();
        layout.torsoHalfWidth = torsoHalfWidth(mesh);
        layout.neck = neck(mesh, layout.torsoHalfWidth);
        layout.crotch = crotch(mesh);
        return layout;
    }

    /** Finds the gap between torso and arms at waist height; without one, assumes raised arms. */
    static float torsoHalfWidth(MeshData mesh) {
        float low = 0.55F * HEIGHT, high = 0.70F * HEIGHT;
        float max = 0.0F;
        for (MeshData.Primitive primitive : mesh.primitives) {
            float[] p = primitive.positions;
            int[] indices = primitive.indices;
            for (int index = 0; index + 2 < indices.length; index += 3) {
                int a = indices[index] * 3, b = indices[index + 1] * 3, c = indices[index + 2] * 3;
                if (Math.max(p[a + 1], Math.max(p[b + 1], p[c + 1])) < low || Math.min(p[a + 1], Math.min(p[b + 1], p[c + 1])) > high) continue;
                max = Math.max(max, Math.max(Math.abs(p[a]), Math.max(Math.abs(p[b]), Math.abs(p[c]))));
            }
        }
        if (max <= 0.0F) return HEIGHT * 0.13F;
        int bins = 48;
        int[] counts = new int[bins];
        // Mark the |x| range every triangle crossing the waist band covers.
        for (MeshData.Primitive primitive : mesh.primitives) {
            float[] p = primitive.positions;
            int[] indices = primitive.indices;
            for (int index = 0; index + 2 < indices.length; index += 3) {
                int a = indices[index] * 3, b = indices[index + 1] * 3, c = indices[index + 2] * 3;
                if (Math.max(p[a + 1], Math.max(p[b + 1], p[c + 1])) < low || Math.min(p[a + 1], Math.min(p[b + 1], p[c + 1])) > high) continue;
                float minX = Math.min(p[a], Math.min(p[b], p[c])), maxX = Math.max(p[a], Math.max(p[b], p[c]));
                float from = minX < 0 && maxX > 0 ? 0.0F : Math.min(Math.abs(minX), Math.abs(maxX));
                float to = Math.max(Math.abs(minX), Math.abs(maxX));
                int last = Math.min(bins - 1, (int) (to / max * bins));
                for (int bin = Math.min(bins - 1, (int) (from / max * bins)); bin <= last; bin++) counts[bin]++;
            }
        }
        float binWidth = max / bins;
        for (int bin = 0; bin < bins; bin++) {
            float x = bin * binWidth;
            if (x < HEIGHT * 0.07F || counts[bin] > 0) continue;
            for (int later = bin + 1; later < bins; later++) {
                if (counts[later] > 0) return x + binWidth * 0.5F;
            }
            break;
        }
        // No arms at waist height (T-pose) or arms merged with the torso.
        return max <= HEIGHT * 0.20F ? max + HEIGHT * 0.02F : HEIGHT * 0.15F;
    }

    /** The narrowest point between shoulders and chin, as a height fraction. */
    static float neck(MeshData mesh, float torsoHalfWidth) {
        int samples = 19;
        float[] widths = new float[samples];
        for (MeshData.Primitive primitive : mesh.primitives) {
            float[] p = primitive.positions;
            for (int index = 0; index < p.length; index += 3) {
                if (Math.abs(p[index]) > torsoHalfWidth) continue;
                float t = p[index + 1] / HEIGHT;
                int sample = Math.round((t - 0.74F) / 0.01F);
                if (sample >= 0 && sample < samples) widths[sample] = Math.max(widths[sample], Math.abs(p[index]));
            }
        }
        float shoulders = 0.0F;
        for (int sample = 0; sample < 6; sample++) shoulders = Math.max(shoulders, widths[sample]);
        int best = -1;
        for (int sample = 3; sample < samples; sample++) {
            if (widths[sample] <= 0.0F) continue;
            if (best < 0 || widths[sample] < widths[best]) best = sample;
        }
        if (best < 0 || shoulders <= 0.0F || widths[best] > shoulders * 0.85F) return 0.86F;
        return 0.74F + best * 0.01F;
    }

    /** The lowest height where the centre line is covered by geometry, i.e. where the legs join. */
    static float crotch(MeshData mesh) {
        float first = -1.0F;
        for (int step = 0; step <= 35; step++) {
            float t = 0.25F + step * 0.01F;
            if (centreCovered(mesh, t * HEIGHT)) {
                first = t;
                break;
            }
        }
        if (first < 0.0F || first <= 0.27F) return 0.47F;
        return Math.min(0.58F, first);
    }

    private static boolean centreCovered(MeshData mesh, float y) {
        for (MeshData.Primitive primitive : mesh.primitives) {
            float[] p = primitive.positions;
            int[] indices = primitive.indices;
            for (int index = 0; index + 2 < indices.length; index += 3) {
                int a = indices[index] * 3, b = indices[index + 1] * 3, c = indices[index + 2] * 3;
                if (containsXY(p[a], p[a + 1], p[b], p[b + 1], p[c], p[c + 1], 0.0F, y)) return true;
            }
        }
        return false;
    }

    static boolean containsXY(float ax, float ay, float bx, float by, float cx, float cy, float x, float y) {
        float d1 = (x - bx) * (ay - by) - (ax - bx) * (y - by);
        float d2 = (x - cx) * (by - cy) - (bx - cx) * (y - cy);
        float d3 = (x - ax) * (cy - ay) - (cx - ax) * (y - ay);
        boolean negative = d1 < 0 || d2 < 0 || d3 < 0, positive = d1 > 0 || d2 > 0 || d3 > 0;
        return !(negative && positive);
    }

    /** Joint positions in the normalised space: from the skeleton when present, otherwise measured. */
    static float[][] pivots(MeshData mesh, Layout layout) {
        float[][] pivots = new float[PARTS][];
        Map<BodyPart, double[]> sums = new EnumMap<BodyPart, double[]>(BodyPart.class);
        for (BodyPart part : BodyPart.values()) sums.put(part, new double[] {0, 0, 0, 0, -Double.MAX_VALUE});
        // Pass 1: per-part top of geometry.
        for (MeshData.Primitive primitive : mesh.primitives) {
            float[] p = primitive.positions;
            for (int vertex = 0; vertex < p.length / 3; vertex++) {
                BodyPart part = partOf(primitive, vertex, layout);
                double[] sum = sums.get(part);
                sum[4] = Math.max(sum[4], p[vertex * 3 + 1]);
            }
        }
        // Pass 2: centroid of the top slice of each part, where its joint is.
        for (MeshData.Primitive primitive : mesh.primitives) {
            float[] p = primitive.positions;
            for (int vertex = 0; vertex < p.length / 3; vertex++) {
                BodyPart part = partOf(primitive, vertex, layout);
                double[] sum = sums.get(part);
                float y = p[vertex * 3 + 1];
                float slice = part.isArm() ? HEIGHT * 0.10F : HEIGHT * 0.08F;
                if (part == BodyPart.HEAD) {
                    if (y > layout.neck * HEIGHT + HEIGHT * 0.03F) continue;
                } else if (y < sum[4] - slice) {
                    continue;
                }
                sum[0] += p[vertex * 3];
                sum[1] += y;
                sum[2] += p[vertex * 3 + 2];
                sum[3] += 1.0D;
            }
        }
        float neckY = layout.neck * HEIGHT;
        double[] head = sums.get(BodyPart.HEAD);
        float neckZ = head[3] > 0 ? (float) (head[2] / head[3]) : 0.0F;
        for (BodyPart part : BodyPart.values()) {
            float[] joint = mesh.jointPivots.get(part);
            if (joint != null) {
                pivots[part.ordinal()] = joint.clone();
                continue;
            }
            double[] sum = sums.get(part);
            float side = part == BodyPart.LEFT_ARM || part == BodyPart.LEFT_LEG ? 1.0F : -1.0F;
            float meanX = sum[3] > 0 ? (float) (sum[0] / sum[3]) : 0.0F;
            float meanZ = sum[3] > 0 ? (float) (sum[2] / sum[3]) : neckZ;
            if (part.isArm()) {
                float top = sum[3] > 0 ? (float) sum[4] : HEIGHT * 0.82F;
                pivots[part.ordinal()] = new float[] {side * layout.torsoHalfWidth * 1.05F, top - HEIGHT * 0.05F, meanZ};
            } else if (part.isLeg()) {
                float x = sum[3] > 0 ? meanX : side * HEIGHT * 0.06F;
                pivots[part.ordinal()] = new float[] {x, layout.crotch * HEIGHT, meanZ};
            } else {
                pivots[part.ordinal()] = new float[] {0.0F, neckY, neckZ};
            }
        }
        float[] headJoint = mesh.jointPivots.get(BodyPart.HEAD);
        if (headJoint != null) pivots[BodyPart.BODY.ordinal()] = headJoint.clone();
        return pivots;
    }

    private static BodyPart partOf(MeshData.Primitive primitive, int vertex, Layout layout) {
        if (primitive.parts != null && primitive.parts[vertex] >= 0) return BodyPart.values()[primitive.parts[vertex]];
        float[] p = primitive.positions;
        return layout.classify(p[vertex * 3], p[vertex * 3 + 1]);
    }

    /** How far an arm is raised from hanging straight down, in degrees (90 = T-pose). */
    static double armRaise(MeshData mesh, Layout layout, BodyPart arm, float[] pivot) {
        int count = 0;
        double[] distances = new double[64];
        for (MeshData.Primitive primitive : mesh.primitives) {
            float[] p = primitive.positions;
            for (int vertex = 0; vertex < p.length / 3; vertex++) {
                if (partOf(primitive, vertex, layout) != arm) continue;
                double dx = p[vertex * 3] - pivot[0], dy = p[vertex * 3 + 1] - pivot[1];
                if (count == distances.length) distances = Arrays.copyOf(distances, count * 2);
                distances[count++] = dx * dx + dy * dy;
            }
        }
        if (count < 8) return 0.0D;
        double[] sorted = Arrays.copyOf(distances, count);
        Arrays.sort(sorted);
        double median = sorted[count / 2];
        double sumX = 0, sumY = 0, far = 0;
        int index = 0;
        for (MeshData.Primitive primitive : mesh.primitives) {
            float[] p = primitive.positions;
            for (int vertex = 0; vertex < p.length / 3; vertex++) {
                if (partOf(primitive, vertex, layout) != arm) continue;
                if (distances[index++] < median) continue;
                sumX += p[vertex * 3] - pivot[0];
                sumY += p[vertex * 3 + 1] - pivot[1];
                far++;
            }
        }
        if (far == 0) return 0.0D;
        return Math.toDegrees(Math.atan2(Math.abs(sumX / far), -sumY / far));
    }
}

package dev.vibe.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Turns indexed primitives into per-part, per-material vertex batches. Meshes above the triangle
 * budget are reduced with vertex clustering: vertices sharing a grid cell merge into their mean,
 * and triangles that collapse or duplicate another are dropped. It is fast (linear), needs no
 * connectivity information and keeps texture coordinates per corner, which suits scanned and
 * game-ripped Sketchfab meshes that are rarely manifold.
 */
public final class Simplifier {

    /** Assigns a triangle (given by vertex indices into {@code primitive}) to a part index. */
    public interface Classifier {
        int classify(MeshData.Primitive primitive, int a, int b, int c);
    }

    private Simplifier() {
    }

    /**
     * @param partCount       number of parts the classifier returns indices for
     * @param partTransforms  optional column-major matrix per part applied to output positions and normals
     */
    public static PreparedModel.Lod build(MeshData mesh, int targetTriangles, int partCount, Classifier classifier,
            double[][] partTransforms) {
        float[] bounds = mesh.bounds();
        double extent = Math.max(bounds[3] - bounds[0], Math.max(bounds[4] - bounds[1], bounds[5] - bounds[2]));
        int resolution = mesh.triangleCount() <= targetTriangles || extent <= 0 ? 0 : resolution(mesh, targetTriangles, bounds, extent);
        List<long[]> keys = new ArrayList<long[]>();
        float[][] representatives = null;
        if (resolution > 0) {
            LongIntMap clusters = new LongIntMap(1024);
            List<int[]> clusterIds = new ArrayList<int[]>();
            for (MeshData.Primitive primitive : mesh.primitives) {
                long[] primitiveKeys = keys(primitive, bounds, extent, resolution);
                keys.add(primitiveKeys);
                int[] ids = new int[primitiveKeys.length];
                for (int vertex = 0; vertex < ids.length; vertex++) ids[vertex] = clusters.putIfAbsent(primitiveKeys[vertex], clusters.size());
                clusterIds.add(ids);
            }
            double[] sums = new double[clusters.size() * 4];
            for (int index = 0; index < mesh.primitives.size(); index++) {
                float[] positions = mesh.primitives.get(index).positions;
                int[] ids = clusterIds.get(index);
                for (int vertex = 0; vertex < ids.length; vertex++) {
                    int id = ids[vertex] * 4;
                    sums[id] += positions[vertex * 3];
                    sums[id + 1] += positions[vertex * 3 + 1];
                    sums[id + 2] += positions[vertex * 3 + 2];
                    sums[id + 3] += 1.0D;
                }
            }
            representatives = new float[mesh.primitives.size()][];
            for (int index = 0; index < mesh.primitives.size(); index++) {
                int[] ids = clusterIds.get(index);
                float[] snapped = new float[ids.length * 3];
                for (int vertex = 0; vertex < ids.length; vertex++) {
                    int id = ids[vertex] * 4;
                    double count = Math.max(1.0D, sums[id + 3]);
                    snapped[vertex * 3] = (float) (sums[id] / count);
                    snapped[vertex * 3 + 1] = (float) (sums[id + 1] / count);
                    snapped[vertex * 3 + 2] = (float) (sums[id + 2] / count);
                }
                representatives[index] = snapped;
            }
        }
        Builder[][] builders = new Builder[partCount][mesh.materials.size()];
        Set<TriangleKey> seen = resolution > 0 ? new HashSet<TriangleKey>() : null;
        for (int index = 0; index < mesh.primitives.size(); index++) {
            MeshData.Primitive primitive = mesh.primitives.get(index);
            long[] primitiveKeys = resolution > 0 ? keys.get(index) : null;
            float[] positions = representatives == null ? primitive.positions : representatives[index];
            int[] indices = primitive.indices;
            for (int triangle = 0; triangle + 2 < indices.length; triangle += 3) {
                int a = indices[triangle], b = indices[triangle + 1], c = indices[triangle + 2];
                if (primitiveKeys != null) {
                    long ka = primitiveKeys[a], kb = primitiveKeys[b], kc = primitiveKeys[c];
                    if (ka == kb || kb == kc || ka == kc) continue;
                    if (!seen.add(new TriangleKey(primitive.material, ka, kb, kc))) continue;
                }
                int part = Math.max(0, Math.min(partCount - 1, classifier == null ? 0 : classifier.classify(primitive, a, b, c)));
                Builder builder = builders[part][primitive.material];
                if (builder == null) {
                    builder = new Builder(primitive.colors != null);
                    builders[part][primitive.material] = builder;
                }
                double[] transform = partTransforms == null ? null : partTransforms[part];
                builder.corner(primitive, positions, a, transform);
                builder.corner(primitive, positions, b, transform);
                builder.corner(primitive, positions, c, transform);
            }
        }
        PreparedModel.Batch[][] parts = new PreparedModel.Batch[partCount][];
        for (int part = 0; part < partCount; part++) {
            List<PreparedModel.Batch> batches = new ArrayList<PreparedModel.Batch>();
            for (int material = 0; material < builders[part].length; material++) {
                Builder builder = builders[part][material];
                if (builder != null && builder.size > 0) batches.add(builder.finish(material));
            }
            parts[part] = batches.toArray(new PreparedModel.Batch[0]);
        }
        return new PreparedModel.Lod(parts);
    }

    /** Finds the finest grid whose surviving triangle count stays within the budget. */
    static int resolution(MeshData mesh, int target, float[] bounds, double extent) {
        int low = 2, high = 4096, best = 2;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            if (survivors(mesh, bounds, extent, middle) <= target) {
                best = middle;
                low = middle + 1;
            } else {
                high = middle - 1;
            }
        }
        return best;
    }

    private static long survivors(MeshData mesh, float[] bounds, double extent, int resolution) {
        long count = 0;
        for (MeshData.Primitive primitive : mesh.primitives) {
            long[] keys = keys(primitive, bounds, extent, resolution);
            int[] indices = primitive.indices;
            for (int triangle = 0; triangle + 2 < indices.length; triangle += 3) {
                long a = keys[indices[triangle]], b = keys[indices[triangle + 1]], c = keys[indices[triangle + 2]];
                if (a != b && b != c && a != c) count++;
            }
        }
        return count;
    }

    private static long[] keys(MeshData.Primitive primitive, float[] bounds, double extent, int resolution) {
        float[] positions = primitive.positions;
        long[] keys = new long[positions.length / 3];
        double scale = resolution / extent;
        long size = resolution + 1L;
        for (int vertex = 0; vertex < keys.length; vertex++) {
            long x = Math.min(resolution, Math.max(0, (long) ((positions[vertex * 3] - bounds[0]) * scale)));
            long y = Math.min(resolution, Math.max(0, (long) ((positions[vertex * 3 + 1] - bounds[1]) * scale)));
            long z = Math.min(resolution, Math.max(0, (long) ((positions[vertex * 3 + 2] - bounds[2]) * scale)));
            keys[vertex] = (x * size + y) * size + z;
        }
        return keys;
    }

    private static final class TriangleKey {
        private final long a, b, c;
        private final int material;

        TriangleKey(int material, long first, long second, long third) {
            long[] sorted = {first, second, third};
            Arrays.sort(sorted);
            this.material = material;
            this.a = sorted[0];
            this.b = sorted[1];
            this.c = sorted[2];
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof TriangleKey)) return false;
            TriangleKey key = (TriangleKey) other;
            return key.a == a && key.b == b && key.c == c && key.material == material;
        }

        @Override
        public int hashCode() {
            long hash = a * 0x9E3779B97F4A7C15L ^ b * 0xC2B2AE3D27D4EB4FL ^ c * 0x165667B19E3779F9L ^ material;
            return (int) (hash ^ (hash >>> 32));
        }
    }

    private static final class Builder {
        private final boolean colors;
        private float[] data = new float[PreparedModel.STRIDE * 96];
        private int size;

        Builder(boolean colors) {
            this.colors = colors;
        }

        void corner(MeshData.Primitive primitive, float[] positions, int vertex, double[] transform) {
            if (size + PreparedModel.STRIDE > data.length) data = Arrays.copyOf(data, data.length * 2);
            float x = positions[vertex * 3], y = positions[vertex * 3 + 1], z = positions[vertex * 3 + 2];
            float nx = primitive.normals[vertex * 3], ny = primitive.normals[vertex * 3 + 1], nz = primitive.normals[vertex * 3 + 2];
            if (transform != null) {
                float tx = (float) (transform[0] * x + transform[4] * y + transform[8] * z + transform[12]);
                float ty = (float) (transform[1] * x + transform[5] * y + transform[9] * z + transform[13]);
                float tz = (float) (transform[2] * x + transform[6] * y + transform[10] * z + transform[14]);
                float rx = (float) (transform[0] * nx + transform[4] * ny + transform[8] * nz);
                float ry = (float) (transform[1] * nx + transform[5] * ny + transform[9] * nz);
                float rz = (float) (transform[2] * nx + transform[6] * ny + transform[10] * nz);
                x = tx;
                y = ty;
                z = tz;
                nx = rx;
                ny = ry;
                nz = rz;
            }
            data[size++] = x;
            data[size++] = y;
            data[size++] = z;
            data[size++] = nx;
            data[size++] = ny;
            data[size++] = nz;
            data[size++] = primitive.uvs == null ? 0.0F : primitive.uvs[vertex * 2];
            data[size++] = primitive.uvs == null ? 0.0F : primitive.uvs[vertex * 2 + 1];
            if (primitive.colors != null) {
                data[size++] = primitive.colors[vertex * 4];
                data[size++] = primitive.colors[vertex * 4 + 1];
                data[size++] = primitive.colors[vertex * 4 + 2];
                data[size++] = primitive.colors[vertex * 4 + 3];
            } else {
                data[size++] = 1.0F;
                data[size++] = 1.0F;
                data[size++] = 1.0F;
                data[size++] = 1.0F;
            }
        }

        PreparedModel.Batch finish(int material) {
            return new PreparedModel.Batch(material, Arrays.copyOf(data, size), colors);
        }
    }

    /** Open-addressing long to int map; avoids boxing millions of cluster keys. */
    static final class LongIntMap {
        private long[] keys;
        private int[] values;
        private boolean[] used;
        private int size;

        LongIntMap(int capacity) {
            int power = Integer.highestOneBit(Math.max(16, capacity) * 2 - 1);
            keys = new long[power];
            values = new int[power];
            used = new boolean[power];
        }

        int size() {
            return size;
        }

        /** Returns the existing value for {@code key}, or stores and returns {@code value}. */
        int putIfAbsent(long key, int value) {
            if ((size + 1) * 2 > keys.length) grow();
            int mask = keys.length - 1;
            int slot = mix(key) & mask;
            while (used[slot]) {
                if (keys[slot] == key) return values[slot];
                slot = (slot + 1) & mask;
            }
            used[slot] = true;
            keys[slot] = key;
            values[slot] = value;
            size++;
            return value;
        }

        private void grow() {
            long[] oldKeys = keys;
            int[] oldValues = values;
            boolean[] oldUsed = used;
            keys = new long[oldKeys.length * 2];
            values = new int[oldKeys.length * 2];
            used = new boolean[oldKeys.length * 2];
            int mask = keys.length - 1;
            for (int index = 0; index < oldKeys.length; index++) {
                if (!oldUsed[index]) continue;
                int slot = mix(oldKeys[index]) & mask;
                while (used[slot]) slot = (slot + 1) & mask;
                used[slot] = true;
                keys[slot] = oldKeys[index];
                values[slot] = oldValues[index];
            }
        }

        private static int mix(long key) {
            long hash = key * 0x9E3779B97F4A7C15L;
            return (int) (hash ^ (hash >>> 29) ^ (hash >>> 47));
        }
    }
}

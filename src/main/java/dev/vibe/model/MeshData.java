package dev.vibe.model;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * A model as loaded from disk: indexed triangle primitives in the file's own coordinate
 * space, their materials and, for rigged files, which body part every vertex belongs to.
 * Nothing in here touches OpenGL, so loading runs on a worker thread.
 */
public final class MeshData {

    public static final class Material {
        /** glTF alpha modes: alpha ignored, alpha tested against {@link #alphaCutoff}, or alpha blended. */
        public static final int OPAQUE = 0, MASK = 1, BLEND = 2;

        public final String name;
        /** Linear RGBA multiplier (glTF baseColorFactor, OBJ Kd/d). */
        public final float[] color;
        /** Decoded and already downscaled; null for untextured materials. */
        public final BufferedImage texture;
        public final int alphaMode;
        public final float alphaCutoff;
        public final boolean repeat;

        public Material(String name, float[] color, BufferedImage texture, int alphaMode, float alphaCutoff, boolean repeat) {
            this.name = name == null ? "" : name;
            this.color = color == null ? new float[] {1.0F, 1.0F, 1.0F, 1.0F} : color;
            this.texture = texture;
            this.alphaMode = alphaMode;
            this.alphaCutoff = alphaCutoff;
            this.repeat = repeat;
        }
    }

    public static final class Primitive {
        public final int material;
        /** xyz per vertex. */
        public final float[] positions;
        /** xyz per vertex; never null after {@link MeshData#MeshData}. */
        public float[] normals;
        /** uv per vertex with v = 0 at the top of the image; may be null. */
        public final float[] uvs;
        /** rgba per vertex; may be null. */
        public final float[] colors;
        /** Three vertex indices per triangle. */
        public final int[] indices;
        /** {@link BodyPart} ordinal per vertex from skin weights, -1 when unknown; may be null. */
        public final byte[] parts;

        public Primitive(int material, float[] positions, float[] normals, float[] uvs, float[] colors, int[] indices, byte[] parts) {
            this.material = material;
            this.positions = positions;
            this.normals = normals;
            this.uvs = uvs;
            this.colors = colors;
            this.indices = indices;
            this.parts = parts;
        }

        public int vertexCount() {
            return positions.length / 3;
        }

        public int triangleCount() {
            return indices.length / 3;
        }
    }

    public final List<Material> materials;
    public final List<Primitive> primitives;
    /** Rest-pose joint positions of a recognised humanoid skeleton, in the same space as the vertices. */
    public final Map<BodyPart, float[]> jointPivots;
    /** False when the format does not define an up axis (OBJ), so the fitter may detect it. */
    public final boolean definedUpAxis;

    public MeshData(List<Material> materials, List<Primitive> primitives, Map<BodyPart, float[]> jointPivots,
            boolean definedUpAxis) {
        this.materials = Collections.unmodifiableList(new ArrayList<Material>(materials));
        this.primitives = Collections.unmodifiableList(new ArrayList<Primitive>(primitives));
        this.jointPivots = jointPivots == null ? new EnumMap<BodyPart, float[]>(BodyPart.class) : jointPivots;
        this.definedUpAxis = definedUpAxis;
        for (Primitive primitive : this.primitives) {
            if (primitive.normals == null || primitive.normals.length != primitive.positions.length) {
                primitive.normals = smoothNormals(primitive.positions, primitive.indices);
            }
        }
    }

    public int triangleCount() {
        int count = 0;
        for (Primitive primitive : primitives) count += primitive.triangleCount();
        return count;
    }

    public boolean hasPartHints() {
        for (Primitive primitive : primitives) if (primitive.parts != null) return true;
        return false;
    }

    /** Applies a column-major 4x4 matrix to every position and its normal matrix to every normal. */
    public void transform(double[] matrix) {
        double[] normalMatrix = Matrix.normalMatrix(matrix);
        for (Primitive primitive : primitives) {
            float[] p = primitive.positions, n = primitive.normals;
            for (int index = 0; index < p.length; index += 3) {
                double x = p[index], y = p[index + 1], z = p[index + 2];
                p[index] = (float) (matrix[0] * x + matrix[4] * y + matrix[8] * z + matrix[12]);
                p[index + 1] = (float) (matrix[1] * x + matrix[5] * y + matrix[9] * z + matrix[13]);
                p[index + 2] = (float) (matrix[2] * x + matrix[6] * y + matrix[10] * z + matrix[14]);
                double nx = n[index], ny = n[index + 1], nz = n[index + 2];
                double tx = normalMatrix[0] * nx + normalMatrix[3] * ny + normalMatrix[6] * nz;
                double ty = normalMatrix[1] * nx + normalMatrix[4] * ny + normalMatrix[7] * nz;
                double tz = normalMatrix[2] * nx + normalMatrix[5] * ny + normalMatrix[8] * nz;
                double length = Math.sqrt(tx * tx + ty * ty + tz * tz);
                if (length > 1.0E-12D) {
                    n[index] = (float) (tx / length);
                    n[index + 1] = (float) (ty / length);
                    n[index + 2] = (float) (tz / length);
                }
            }
        }
        for (float[] pivot : jointPivots.values()) {
            double x = pivot[0], y = pivot[1], z = pivot[2];
            pivot[0] = (float) (matrix[0] * x + matrix[4] * y + matrix[8] * z + matrix[12]);
            pivot[1] = (float) (matrix[1] * x + matrix[5] * y + matrix[9] * z + matrix[13]);
            pivot[2] = (float) (matrix[2] * x + matrix[6] * y + matrix[10] * z + matrix[14]);
        }
    }

    /** Axis-aligned bounds as {minX, minY, minZ, maxX, maxY, maxZ}; zeros for an empty mesh. */
    public float[] bounds() {
        float[] bounds = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        boolean any = false;
        for (Primitive primitive : primitives) {
            float[] p = primitive.positions;
            for (int index = 0; index < p.length; index += 3) {
                any = true;
                for (int axis = 0; axis < 3; axis++) {
                    bounds[axis] = Math.min(bounds[axis], p[index + axis]);
                    bounds[axis + 3] = Math.max(bounds[axis + 3], p[index + axis]);
                }
            }
        }
        return any ? bounds : new float[6];
    }

    /** Area-weighted vertex normals; used when a file has none. */
    static float[] smoothNormals(float[] positions, int[] indices) {
        float[] normals = new float[positions.length];
        for (int index = 0; index + 2 < indices.length; index += 3) {
            int a = indices[index] * 3, b = indices[index + 1] * 3, c = indices[index + 2] * 3;
            float ux = positions[b] - positions[a], uy = positions[b + 1] - positions[a + 1], uz = positions[b + 2] - positions[a + 2];
            float vx = positions[c] - positions[a], vy = positions[c + 1] - positions[a + 1], vz = positions[c + 2] - positions[a + 2];
            float nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
            normals[a] += nx;
            normals[a + 1] += ny;
            normals[a + 2] += nz;
            normals[b] += nx;
            normals[b + 1] += ny;
            normals[b + 2] += nz;
            normals[c] += nx;
            normals[c + 1] += ny;
            normals[c + 2] += nz;
        }
        for (int index = 0; index < normals.length; index += 3) {
            float length = (float) Math.sqrt(normals[index] * normals[index] + normals[index + 1] * normals[index + 1]
                    + normals[index + 2] * normals[index + 2]);
            if (length > 1.0E-12F) {
                normals[index] /= length;
                normals[index + 1] /= length;
                normals[index + 2] /= length;
            } else {
                normals[index + 1] = 1.0F;
            }
        }
        return normals;
    }
}

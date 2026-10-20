package dev.vibe.model;

import java.util.List;

/**
 * GPU-ready geometry: interleaved vertex arrays already fitted to Minecraft's space, grouped by
 * level of detail, body part and material. Built on the worker thread and compiled into display
 * lists on the render thread.
 */
public final class PreparedModel {

    /** Floats per vertex: position (3), normal (3), uv (2), rgba (4). */
    public static final int STRIDE = 12;

    public static final class Batch {
        public final int material;
        public final float[] vertices;
        public final boolean vertexColors;

        public Batch(int material, float[] vertices, boolean vertexColors) {
            this.material = material;
            this.vertices = vertices;
            this.vertexColors = vertexColors;
        }

        public int vertexCount() {
            return vertices.length / STRIDE;
        }
    }

    public static final class Lod {
        /** Batches per part; swords use part 0 only. */
        public final Batch[][] parts;
        public final int triangles;

        public Lod(Batch[][] parts) {
            this.parts = parts;
            int count = 0;
            for (Batch[] batches : parts) for (Batch batch : batches) count += batch.vertexCount() / 3;
            this.triangles = count;
        }
    }

    public final List<MeshData.Material> materials;
    public final Lod[] lods;
    /** Rotation pivots per {@link BodyPart} in ModelBiped pixels, or null for held items. */
    public final float[][] pivots;
    /** Model-specific anchors: for held items {gripX, gripY, gripZ, tipX, tipY, tipZ} in item space. */
    public final float[] anchors;
    public final int sourceTriangles;
    public final String notes;

    public PreparedModel(List<MeshData.Material> materials, Lod[] lods, float[][] pivots, float[] anchors,
            int sourceTriangles, String notes) {
        this.materials = materials;
        this.lods = lods;
        this.pivots = pivots;
        this.anchors = anchors;
        this.sourceTriangles = sourceTriangles;
        this.notes = notes == null ? "" : notes;
    }
}

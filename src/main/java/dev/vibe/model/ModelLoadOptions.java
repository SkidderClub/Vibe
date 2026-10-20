package dev.vibe.model;

/** Limits applied while reading model files from disk. */
public final class ModelLoadOptions {
    /** Largest texture edge kept after decoding. */
    public final int maxTextureSize;
    /** Files with more source triangles are rejected before their geometry is read. */
    public final int maxSourceTriangles;
    /** Largest single file (model, buffer or texture) that is read into memory. */
    public final long maxFileBytes;

    public ModelLoadOptions(int maxTextureSize, int maxSourceTriangles, long maxFileBytes) {
        this.maxTextureSize = Math.max(16, maxTextureSize);
        this.maxSourceTriangles = Math.max(1, maxSourceTriangles);
        this.maxFileBytes = Math.max(1L, maxFileBytes);
    }

    public static ModelLoadOptions defaults() {
        return new ModelLoadOptions(1024, 3000000, 768L * 1024L * 1024L);
    }
}

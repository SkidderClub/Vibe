package dev.vibe.ui.render;

/** Injected into RenderChunk: one timestamp, without a world/chunk map or per-frame allocations. */
public interface ChunkAnimationState {
    long vibe$getAnimationStart();
    void vibe$setAnimationStart(long value);
}

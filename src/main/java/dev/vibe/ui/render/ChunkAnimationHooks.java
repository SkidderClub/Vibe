package dev.vibe.ui.render;

import dev.vibe.module.impl.visual.ChunkAnimationModule;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.chunk.CompiledChunk;
import java.util.Objects;

/** Render-thread transforms shared by the vanilla display-list and VBO paths. */
public final class ChunkAnimationHooks {
    private static final long ORIGIN = System.nanoTime();
    private static volatile ChunkAnimationModule module;
    private static volatile boolean enabled;
    private static long frameTime, enabledSince, duration;
    private static float distance;
    private static int mode;
    private ChunkAnimationHooks() { }

    public static void bind(ChunkAnimationModule value) { module = value; enabled = false; }
    public static void disable(ChunkAnimationModule value) { if (module == value) enabled = false; }

    /** Once per game frame: all block layers get exactly the same time and settings. */
    public static void beginFrame() {
        ChunkAnimationModule value = module;
        beginFrame(clock(), value != null && value.isEnabled()
                && !dev.vibe.module.impl.meme.HypixelModule.visualsSuppressed(), value);
    }
    static void beginFrame(long now, boolean active, ChunkAnimationModule value) {
        frameTime = now;
        if (active && !enabled) enabledSince = now;
        enabled = active;
        if (!active) return;
        duration = value.duration.getInt() * 1000000L; distance = value.distance.getFloat();
        mode = value.animation.is("Drop") ? 1 : value.animation.is("Slide") ? 2 : value.animation.is("Grow") ? 3 : 0;
    }

    public static void resetPosition(Object chunk, Object previous, Object next) {
        if (chunk instanceof ChunkAnimationState && !Objects.equals(previous, next))
            ((ChunkAnimationState) chunk).vibe$setAnimationStart(0);
    }
    public static void compiled(Object chunk, Object geometry) {
        if (!(chunk instanceof ChunkAnimationState) || !(geometry instanceof CompiledChunk) || geometry == CompiledChunk.DUMMY) return;
        ChunkAnimationState state = (ChunkAnimationState) chunk;
        if (state.vibe$getAnimationStart() != 0) return;
        state.vibe$setAnimationStart(enabled && !((CompiledChunk) geometry).isEmpty() ? clock() : -1);
    }
    public static void discarded(Object chunk) {
        if (chunk instanceof ChunkAnimationState) ((ChunkAnimationState) chunk).vibe$setAnimationStart(-1);
    }
    public static void apply(Object chunk) {
        if (!(chunk instanceof ChunkAnimationState)) return;
        float remaining = remaining((ChunkAnimationState) chunk);
        if (remaining <= 0) return;
        if (mode == 3) {
            float scale = Math.max(.001F, 1 - remaining);
            GlStateManager.translate(8F, 0F, 8F); GlStateManager.scale(scale, scale, scale); GlStateManager.translate(-8F, 0F, -8F);
        } else if (mode == 2) GlStateManager.translate(distance * remaining, 0F, 0F);
        else GlStateManager.translate(0F, (mode == 1 ? distance : -distance) * remaining, 0F);
    }
    static float remaining(ChunkAnimationState state) {
        long born = state.vibe$getAnimationStart();
        if (born <= 0) return 0;
        if (!enabled || born < enabledSince || frameTime - born >= duration) { state.vibe$setAnimationStart(-1); return 0; }
        return remaining(frameTime, born, duration);
    }
    static float remaining(long now, long born, long length) {
        float left = 1 - Math.max(0F, Math.min(1F, (now - born) / (float) Math.max(1L, length)));
        return left * left * left;
    }
    private static long clock() { return System.nanoTime() - ORIGIN + 1; }
}

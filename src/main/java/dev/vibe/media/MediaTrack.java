package dev.vibe.media;

import java.awt.image.BufferedImage;

/** Immutable snapshot published by the media worker; no OS or network calls on the render thread. */
public final class MediaTrack {
    public final String title, artist, owner, status;
    public final boolean playing, live;
    public final long positionMs, durationMs, sampledAt;
    public final BufferedImage artwork;
    public MediaTrack(String title, String artist, String owner, String status, boolean playing, boolean live,
                      long positionMs, long durationMs, BufferedImage artwork) {
        this.title = clean(title); this.artist = clean(artist); this.owner = clean(owner); this.status = clean(status);
        this.playing = playing; this.live = live; this.positionMs = Math.max(0, positionMs); this.durationMs = Math.max(0, durationMs);
        this.artwork = artwork; sampledAt = System.currentTimeMillis();
    }
    private static String clean(String s) { return s == null ? "" : s.replace('\u00a7', ' ').replaceAll("[\\p{Cntrl}]", " ").trim(); }
    /** Unknown-duration video sessions still expose a useful elapsed timer. */
    public long position() {
        // Radio/live sessions intentionally have no meaningful timeline.
        // A regular media session can also report an unknown duration, and
        // that case must keep counting so its timer remains visible.
        if (live) return 0;
        long elapsed = positionMs + (playing ? Math.max(0, System.currentTimeMillis() - sampledAt) : 0);
        return durationMs > 0 ? Math.min(durationMs, elapsed) : elapsed;
    }
    public static MediaTrack idle(String status) { return new MediaTrack("Waiting for media", "", "", status, false, false, 0, 0, null); }
}

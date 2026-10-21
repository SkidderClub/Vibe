package dev.vibe.feedback;

import java.util.*;

/** Each transition gets its own card. All animation uses elapsed time, including stack reflow. */
public final class ModuleNotifications {
    public static final int LIMIT = 4;
    public static final long DURATION = 3200;
    public static final int MAX_LIMIT = 8;
    private static final long MOVE_DURATION = 240;
    public static final class Entry {
        public final String id, name;
        public final String detail;
        public final boolean message;
        public final boolean enabled;
        public final long started;
        public final long duration;
        private float fromSlot, targetSlot;
        private long moved;
        private Entry(String id, String name, boolean enabled, String detail, long started, float initialSlot, long duration) {
            this.id = id; this.name = name; this.enabled = enabled; this.started = started;
            this.detail = detail == null ? "" : detail; message = detail != null;
            this.duration = duration;
            fromSlot = initialSlot; moved = started;
        }
        public float remaining(long now) { return Math.max(0, Math.min(1, 1 - (now - started) / (float) duration)); }
        public float visibility(long now) {
            float opening = ease((now - started) / 180F);
            float closing = Math.max(0, Math.min(1, (duration - (now - started)) / 220F));
            return opening * closing;
        }
        /** Zero is the bottom card; arrivals move the existing cards up without overlapping them. */
        public float slot(long now) {
            return fromSlot + (targetSlot - fromSlot) * ease((now - moved) / (float) MOVE_DURATION);
        }
        private void moveTo(int slot, long now) {
            if (targetSlot == slot) return;
            fromSlot = slot(now); targetSlot = slot; moved = now;
        }
        private static float ease(float progress) {
            float t = Math.max(0, Math.min(1, progress));
            return 1 - (1 - t) * (1 - t) * (1 - t);
        }
    }
    private final List<Entry> entries = new ArrayList<Entry>();
    private int limit = LIMIT;
    private long duration = DURATION;

    public synchronized void configure(int maximum, long lifetime, long now) {
        limit = Math.max(1, Math.min(MAX_LIMIT, maximum));
        duration = Math.max(1000, Math.min(10000, lifetime));
        prune(now);
        while (entries.size() > limit) entries.remove(0);
        layout(now);
    }

    public synchronized void add(String id, String name, boolean enabled, long now) {
        addEntry(id, name, enabled, null, now);
    }
    public synchronized void addMessage(String id, String title, String body, long now) {
        addEntry(id, title, true, body, now);
    }
    private void addEntry(String id, String name, boolean enabled, String detail, long now) {
        prune(now);
        // Enter below the previous bottom card. Retargeting both by the same easing
        // preserves their spacing even when several modules toggle in one frame.
        float initialSlot = entries.isEmpty() ? -.35F : entries.get(entries.size() - 1).slot(now) - 1;
        entries.add(new Entry(id, name, enabled, detail, now, initialSlot, duration));
        while (entries.size() > limit) entries.remove(0);
        layout(now);
    }
    public synchronized List<Entry> active(long now) {
        prune(now);
        layout(now);
        return new ArrayList<Entry>(entries);
    }
    private void layout(long now) {
        for (int i = 0; i < entries.size(); i++) entries.get(i).moveTo(entries.size() - 1 - i, now);
    }
    private void prune(long now) { entries.removeIf(entry -> now - entry.started >= entry.duration); }
    public synchronized void clear() { entries.clear(); }
}

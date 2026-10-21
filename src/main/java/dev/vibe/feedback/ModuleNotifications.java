package dev.vibe.feedback;

import java.util.*;

/** Each transition gets its own card. All animation uses elapsed time, including stack reflow. */
public final class ModuleNotifications {
    public static final int LIMIT = 4;
    public static final long DURATION = 3200;
    private static final long MOVE_DURATION = 240;
    public static final class Entry {
        public final String id, name;
        public final boolean enabled;
        public final long started;
        private float fromSlot, targetSlot;
        private long moved;
        private Entry(String id, String name, boolean enabled, long started, float initialSlot) {
            this.id = id; this.name = name; this.enabled = enabled; this.started = started;
            fromSlot = initialSlot; moved = started;
        }
        public float remaining(long now) { return Math.max(0, Math.min(1, 1 - (now - started) / (float) DURATION)); }
        public float visibility(long now) {
            float opening = ease((now - started) / 180F);
            float closing = Math.max(0, Math.min(1, (DURATION - (now - started)) / 220F));
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

    public synchronized void add(String id, String name, boolean enabled, long now) {
        entries.removeIf(entry -> now - entry.started >= DURATION);
        // Enter below the previous bottom card. Retargeting both by the same easing
        // preserves their spacing even when several modules toggle in one frame.
        float initialSlot = entries.isEmpty() ? -.35F : entries.get(entries.size() - 1).slot(now) - 1;
        entries.add(new Entry(id, name, enabled, now, initialSlot));
        while (entries.size() > LIMIT) entries.remove(0);
        layout(now);
    }
    public synchronized List<Entry> active(long now) {
        entries.removeIf(entry -> now - entry.started >= DURATION);
        layout(now);
        return new ArrayList<Entry>(entries);
    }
    private void layout(long now) {
        for (int i = 0; i < entries.size(); i++) entries.get(i).moveTo(entries.size() - 1 - i, now);
    }
    public synchronized void clear() { entries.clear(); }
}

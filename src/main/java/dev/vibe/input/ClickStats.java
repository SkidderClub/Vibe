package dev.vibe.input;

import java.util.ArrayDeque;
import java.util.Deque;

/** Lightweight one-second click history shared by HUD and input modules. */
public final class ClickStats {
    private static final Deque<Long> LEFT = new ArrayDeque<Long>();
    private static final Deque<Long> RIGHT = new ArrayDeque<Long>();

    private ClickStats() { }

    public static synchronized void recordLeft() { record(LEFT); }
    public static synchronized void recordRight() { record(RIGHT); }

    public static synchronized int leftCps() { return count(LEFT); }
    public static synchronized int rightCps() { return count(RIGHT); }

    private static void record(Deque<Long> clicks) {
        long now = System.currentTimeMillis();
        clicks.addLast(now);
        trim(clicks, now);
    }

    private static int count(Deque<Long> clicks) {
        trim(clicks, System.currentTimeMillis());
        return clicks.size();
    }

    private static void trim(Deque<Long> clicks, long now) {
        while (!clicks.isEmpty() && now - clicks.peekFirst() > 1000L) clicks.removeFirst();
    }
}

package dev.vibe.debuginfo;

import java.util.ArrayDeque;
import java.util.Deque;

/** Sliding one-second counter for inbound (RX) and outbound (TX) packets per second. */
public final class PacketTrafficTracker {

    private static final long WINDOW_MS = 1000L;

    private static Object connection;
    private static final Deque<Long> INBOUND = new ArrayDeque<Long>();
    private static final Deque<Long> OUTBOUND = new ArrayDeque<Long>();

    private PacketTrafficTracker() {
    }

    public static synchronized void observeInbound(Object source, long nowMs) {
        switchConnection(source);
        INBOUND.addLast(nowMs);
        trim(INBOUND, nowMs);
    }

    public static synchronized void observeOutbound(Object source, long nowMs) {
        switchConnection(source);
        OUTBOUND.addLast(nowMs);
        trim(OUTBOUND, nowMs);
    }

    public static synchronized int inboundPerSecond(Object source, long nowMs) {
        if (source == null || source != connection) {
            return 0;
        }
        trim(INBOUND, nowMs);
        return INBOUND.size();
    }

    public static synchronized int outboundPerSecond(Object source, long nowMs) {
        if (source == null || source != connection) {
            return 0;
        }
        trim(OUTBOUND, nowMs);
        return OUTBOUND.size();
    }

    public static synchronized void reset() {
        connection = null;
        INBOUND.clear();
        OUTBOUND.clear();
    }

    private static void switchConnection(Object source) {
        if (source != connection) {
            connection = source;
            INBOUND.clear();
            OUTBOUND.clear();
        }
    }

    private static void trim(Deque<Long> samples, long nowMs) {
        while (!samples.isEmpty() && nowMs - samples.peekFirst() > WINDOW_MS) {
            samples.removeFirst();
        }
    }
}

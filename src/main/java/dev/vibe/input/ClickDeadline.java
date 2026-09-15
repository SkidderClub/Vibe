package dev.vibe.input;

import java.util.function.LongSupplier;

/** Keeps fractional-tick click time instead of rounding every interval up to a tick. */
public final class ClickDeadline {
    private long next;
    public void reset() { next = 0; }
    public int poll(long now, LongSupplier delay) {
        if (next == 0 || now - next > 250) next = now;
        int clicks = 0;
        while (now >= next && clicks < 4) {
            clicks++;
            next += Math.max(1, delay.getAsLong());
        }
        return clicks;
    }
}

package dev.vibe.input;

import java.util.Random;

/** Monotonic idle timer. Automated actions never update the last human-input time. */
public final class IdleActionSchedule {
    private long lastInput, nextAction = -1;
    public void userInput(long now) { lastInput = now; nextAction = -1; }
    public boolean ready(long now, long idleMillis) {
        return now - lastInput >= idleMillis && (nextAction < 0 || now >= nextAction);
    }
    public void acted(long now, long minimum, long maximum, Random random) {
        long low = Math.max(1, Math.min(minimum, maximum));
        long high = Math.max(low, Math.max(minimum, maximum));
        nextAction = now + low + (long) (random.nextDouble() * (high - low + 1));
    }
}

package dev.vibe.combat;

import java.util.Random;

/** Deadlines preserve fractional CPS across 50 ms ticks without replaying lag. */
public final class AuraClickScheduler {
    private final Random random;
    private String mode = "";
    private double nextClick = Double.NaN;
    private double burstEnd;
    private boolean secondFinger;
    private double pairPeriod;

    public AuraClickScheduler(Random random) { this.random = random; }

    public void reset() {
        nextClick = Double.NaN;
        burstEnd = 0.0D;
        secondFinger = false;
    }

    public boolean isDue(long now) { return Double.isNaN(nextClick) || now >= nextClick; }

    public int poll(long now, String selectedMode, double minCps, double maxCps,
                    double minBurst, double maxBurst, double minPause, double maxPause) {
        if (!selectedMode.equals(mode)) {
            reset();
            mode = selectedMode;
        }
        if (Double.isNaN(nextClick) || now - nextClick > 150.0D) nextClick = now;
        int clicks = 0;
        while (now >= nextClick && clicks < 4) {
            if ("Drag Clicking".equals(mode) && burstEnd == 0.0D) {
                burstEnd = nextClick + sample(minBurst, maxBurst);
            }
            clicks++;
            if ("Butterfly".equals(mode)) {
                if (!secondFinger) pairPeriod = 2000.0D / sample(minCps, maxCps);
                nextClick += pairPeriod * (secondFinger ? 0.65D : 0.35D);
                secondFinger = !secondFinger;
            } else {
                nextClick += 1000.0D / sample(minCps, maxCps);
            }
            if ("Drag Clicking".equals(mode) && nextClick >= burstEnd) {
                nextClick = burstEnd + sample(minPause, maxPause);
                burstEnd = 0.0D;
            }
        }
        return clicks;
    }

    private double sample(double min, double max) {
        return Math.max(1.0D, min + random.nextDouble() * Math.max(0.0D, max - min));
    }
}

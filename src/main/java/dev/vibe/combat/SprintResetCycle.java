package dev.vibe.combat;

import java.util.Random;

/** Tick state machine recovered from Crab's sprint_reset handler at 0x18016c5e0. */
public final class SprintResetCycle {
    private final Random random;
    private final Ramp delayNoise = new Ramp(5.0D);
    private final Ramp durationNoise = new Ramp(30.0D);
    private final Ramp extensionNoise = new Ramp(30.0D);
    private int phase;
    private int damageTicks;
    private float remainingTicks;
    private double lastHitCheck;
    private boolean pendingHit;

    public SprintResetCycle(final Random random) {
        this.random = random;
    }

    public void tick(final double now, final int ticksExisted, final int lastAttackerTime,
                     final int hurtTime, final boolean weaponAllowed, final float delay,
                     final float stopDuration, final boolean randomize, final boolean damagedOnly) {
        if (now - lastHitCheck >= 0.5D && ticksExisted == lastAttackerTime) {
            lastHitCheck = now;
            pendingHit = true;
        }

        damageTicks++;
        if (hurtTime == 10)
            damageTicks = 0;

        if (remainingTicks > 0.0F) {
            remainingTicks -= 1.0F;
            if (remainingTicks > 0.0F) return;
        }

        if (phase == 2) {
            phase = 0;
            return;
        }

        if (phase == 1) {
            phase = 2;
            pendingHit = false;
            float duration = stopDuration;
            if (randomize) {
                duration = Math.max(0.0F, duration - durationNoise.sample(now));
                if (random.nextFloat() < extensionNoise.sample(now) * 0.05D + 0.05D)
                    duration += random.nextFloat() + 1.0F;
            }

            remainingTicks += duration;
            return;
        }

        // Crab samples the delay noise even while no hit is pending.
        final float wait = delay + (randomize ? delayNoise.sample(now) * 2.0F : 0.0F);
        if (pendingHit && (!damagedOnly || damageTicks == 0) && weaponAllowed) {
            remainingTicks += wait;
            phase = 1;
        }
    }

    public boolean isStopping() {
        return phase == 2;
    }

    public void reset() {
        phase = 0;
        damageTicks = 0;
        remainingTicks = 0.0F;
        lastHitCheck = 0.0D;
        pendingHit = false;
        delayNoise.reset();
        durationNoise.reset();
        extensionNoise.reset();
    }

    private final class Ramp {
        private final double period;
        private double updatedAt;
        private float from, to;

        private Ramp(final double period) {
            this.period = period;
        }

        private float sample(final double now) {
            if (now > updatedAt + period) {
                updatedAt = now;
                if (from == 0.0F && to == 0.0F) {
                    do {
                        from = random.nextFloat();
                        to = random.nextFloat();
                    } while (Math.abs(from - to) < 0.2D);
                } else {
                    from = to;
                    do {
                        to = random.nextFloat();
                    } while (Math.abs(from - to) < 0.2D);
                }
            }

            return (float) (from + (to - from) * ((now - updatedAt) / period));
        }

        private void reset() {
            updatedAt = 0.0D;
            from = 0.0F;
            to = 0.0F;
        }
    }
}

package dev.vibe.core.startup;

import java.util.Random;

/** Bounded, time-based wax emissions shared by the shader and legacy renderer. */
final class StartupDrips {
    static final int COUNT = 12;
    final float[] drops = new float[COUNT * 4], forms = new float[COUNT * 4], necks = new float[COUNT * 4];
    private final Drop[] particles = new Drop[COUNT];
    private final Random random = new Random(0x56494245L);
    private float lastTime = -1, nextEmission = .08F, previousRoot = .3F;
    private int cursor;

    StartupDrips() { for (int i = 0; i < COUNT; i++) particles[i] = new Drop(); }

    void sample(float time, float width, float height, float fillStart, float fillWidth, float floor) {
        if (time < lastTime) reset();
        // A stalled loading thread must not replay an unbounded backlog of emissions.
        if (time - nextEmission > 6) nextEmission = time - 4;
        int emitted = 0;
        while (nextEmission <= time && emitted++ < 32) {
            if (fillWidth > 12) emit(nextEmission, width, fillStart, fillWidth);
            float gap = .20F + (float)Math.pow(random.nextFloat(), 1.6) * .94F;
            if (random.nextFloat() < .20F) gap *= .30F;
            if (random.nextFloat() < .15F) gap += .55F;
            nextEmission += gap;
        }
        for (int i = 0; i < COUNT; i++) {
            int at = i * 4;
            drops[at] = drops[at + 1] = drops[at + 2] = drops[at + 3] = 0;
            forms[at] = 1; forms[at + 1] = forms[at + 2] = forms[at + 3] = 0;
            necks[at] = necks[at + 1] = necks[at + 2] = necks[at + 3] = 0;
            Drop drop = particles[i];
            if (!drop.active) continue;
            float age = time - drop.born, anchor = drop.root * width;
            float growth = smooth(age / drop.grow), flight = Math.max(0, age - drop.grow);
            if (flight == 0 && (anchor < fillStart + 2 || anchor > fillStart + fillWidth - 2)) {
                if (growth < .05F) { drop.active = false; continue; }
                // Moving indicators and new Forge stages release existing wax.
                // Preserve its current volume and shape instead of cancelling it.
                drop.radius *= growth; drop.pendant *= growth;
                drop.aspect -= .26F * (1 - growth);
                drop.grow = Math.max(.001F, age - .001F);
                growth = 1; flight = .001F;
            }
            float stretch = drop.aspect + growth * .26F + Math.min(.5F, flight * .25F)
                    + .08F * (float)Math.sin(age * drop.frequency + drop.phase);
            float radius = drop.radius * growth;
            float rx = radius / (float)Math.sqrt(stretch), ry = radius * (float)Math.sqrt(stretch);
            float x = anchor + drop.drift * flight + (float)Math.sin(flight * drop.frequency) * drop.sway;
            float y = height - .4F + drop.pendant * growth + drop.velocity * flight + drop.gravity * flight * flight * .5F;
            if (y - ry > floor + 12) { drop.active = false; continue; }
            float tilt = drop.tilt + .12F * (float)Math.sin(age * 1.7 + drop.phase) * Math.min(1, flight);
            drops[at] = x; drops[at + 1] = y; drops[at + 2] = rx; drops[at + 3] = ry;
            forms[at] = (float)Math.cos(tilt); forms[at + 1] = (float)Math.sin(tilt);
            forms[at + 2] = drop.taper; forms[at + 3] = drop.lobes;
            necks[at] = anchor;
            necks[at + 1] = flight == 0 ? radius * .43F * (1 - smooth((age / drop.grow - .67F) / .33F)) : 0;
            necks[at + 2] = drop.phase; necks[at + 3] = drop.radius;
        }
        lastTime = time;
    }
    private void emit(float born, float width, float start, float filled) {
        Drop drop = null;
        for (int i = 0; i < COUNT; i++) {
            int index = (cursor + i) % COUNT;
            if (!particles[index].active) { drop = particles[index]; cursor = (index + 1) % COUNT; break; }
        }
        if (drop == null) return; // Never cut a falling drop short to make room for a new one.
        // Sometimes a neighbouring wax pocket releases too; other drops start elsewhere.
        float unit = random.nextFloat() < .48F ? previousRoot + (float)random.nextGaussian() * .12F : random.nextFloat();
        unit = Math.max(.025F, Math.min(.975F, unit)); previousRoot = unit;
        drop.root = (start + 4 + unit * Math.max(0, filled - 8)) / width;
        drop.radius = 2.2F + (float)Math.pow(random.nextFloat(), .8) * 5.1F;
        drop.aspect = .70F + random.nextFloat() * .90F;
        drop.taper = -.25F + random.nextFloat() * .62F;
        drop.lobes = .025F + random.nextFloat() * .14F;
        drop.tilt = -.40F + random.nextFloat() * .80F;
        drop.grow = .38F + random.nextFloat() * .95F;
        drop.pendant = 4 + random.nextFloat() * 7;
        drop.velocity = 5 + random.nextFloat() * 9;
        drop.gravity = 24 + random.nextFloat() * 25;
        drop.drift = -4 + random.nextFloat() * 8;
        drop.sway = .2F + random.nextFloat() * 1.6F;
        drop.frequency = 1.4F + random.nextFloat() * 2;
        drop.phase = random.nextFloat() * (float)(Math.PI * 2);
        drop.born = born; drop.active = true;
    }
    private void reset() {
        random.setSeed(0x56494245L); nextEmission = .08F; cursor = 0; previousRoot = .3F;
        for (Drop drop : particles) drop.active = false;
    }
    private static float smooth(float t) { t = Math.max(0, Math.min(1, t)); return t * t * (3 - 2 * t); }
    private static final class Drop {
        boolean active;
        float root, radius, aspect, taper, lobes, tilt, grow, pendant, velocity, gravity, drift, sway, frequency, phase, born;
    }
}

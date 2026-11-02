package dev.vibe.ui.menu;

import java.nio.FloatBuffer;

/** Shared emitter clocks for the menu glass and the early Forge loading screen. */
public final class LavaLampMotion {
    public static final int DROPS = 8;
    private LavaLampMotion() { }

    public static void sample(double time, FloatBuffer drops, FloatBuffer forms) {
        drops.clear(); forms.clear();
        for (int i = 0; i < DROPS; i++) {
            double seed = random(i + 1.7), life = 8.5 + 3 * random(i + 7.1), period = life + 4 + 4 * random(i + 12.8);
            double clock = time + i * 2.31, age = clock - Math.floor(clock / period) * period;
            if (age >= life) { drops.put(0).put(0).put(0).put(1); forms.put(1).put(1).put(0).put(0); continue; }
            double radius = (.038 + .033 * random(i * 3.1 + Math.floor(clock / period) * 7.3 + 19)) * (.85 + .15 * seed);
            double growth = Math.max(0, Math.min(1, age / .85)); growth = growth * growth * (3 - 2 * growth);
            double travel = Math.max(0, (age - .55) / (life - .55)); travel = travel * travel * (3 - 2 * travel);
            double arrival = Math.max(0, Math.min(1, (travel - .76) / .24)); arrival = arrival * arrival * (3 - 2 * arrival);
            double wobble = Math.sin(age * 1.15 + seed * 6.28), angle = .23 * Math.sin(age * .71 + seed * 6.28);
            drops.put((float)(.09 + i * .117 + Math.sin(travel * 4.4 + seed * 6.28) * (.018 + .018 * seed)));
            // Absorb the entire blob into the upper pool before its emitter sleeps.
            drops.put((float)(.105 - radius * .8 + travel * (.82 + radius * 2.85)));
            drops.put((float)(radius * (.35 + .65 * growth))).put((float)(.87 + .065 * wobble + .26 * arrival));
            forms.put((float)(1.16 - .08 * wobble - .34 * arrival)).put((float)Math.cos(angle));
            forms.put((float)Math.sin(angle)).put((float)(.23 * Math.sin(age * .62 + seed * 8)));
        }
        drops.flip(); forms.flip();
    }

    public static float depth(int index) { return .80F + .20F * (float)random(index + 1.7); }
    private static double random(double value) { double n = Math.sin(value * 12.9898 + 78.233) * 43758.5453; return n - Math.floor(n); }
}

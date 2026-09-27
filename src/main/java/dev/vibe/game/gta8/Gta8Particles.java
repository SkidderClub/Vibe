package dev.vibe.game.gta8;

import java.util.Random;

/** Fixed-capacity particle pool (structure of arrays) for smoke, fire, sparks, debris, blood and spray. */
public final class Gta8Particles {
    public static final int SMOKE = 0, FIRE = 1, SPARK = 2, DEBRIS = 3, BLOOD = 4, SPLASH = 5, DUST = 6, MUZZLE = 7, TRACER = 9;
    public static final int CAPACITY = 3000;
    public final float[] x = new float[CAPACITY], y = new float[CAPACITY], z = new float[CAPACITY];
    public final float[] vx = new float[CAPACITY], vy = new float[CAPACITY], vz = new float[CAPACITY];
    public final float[] life = new float[CAPACITY], maxLife = new float[CAPACITY], size = new float[CAPACITY], grow = new float[CAPACITY];
    public final float[] rot = new float[CAPACITY], spin = new float[CAPACITY], stretch = new float[CAPACITY];
    public final int[] color = new int[CAPACITY], type = new int[CAPACITY];
    public final float[] alpha = new float[CAPACITY];
    public int count;
    private final Random random = new Random(31);

    public int spawn(int kind, double px, double py, double pz, double pvx, double pvy, double pvz, double lifetime, double sz, double growth, int rgb, double a) {
        int i;
        if (count < CAPACITY) i = count++;
        else { i = random.nextInt(CAPACITY); }
        type[i] = kind; x[i] = (float) px; y[i] = (float) py; z[i] = (float) pz;
        vx[i] = (float) pvx; vy[i] = (float) pvy; vz[i] = (float) pvz;
        life[i] = maxLife[i] = (float) lifetime; size[i] = (float) sz; grow[i] = (float) growth;
        rot[i] = random.nextFloat() * 6.28f; spin[i] = (random.nextFloat() - .5f) * 2;
        color[i] = rgb; alpha[i] = (float) a; stretch[i] = 0;
        return i;
    }
    public double r() { return random.nextDouble(); }
    public double s() { return random.nextDouble() * 2 - 1; }

    public void update(double dt, double windX, double windZ, Gta8World world) {
        for (int i = 0; i < count; i++) {
            life[i] -= dt;
            if (life[i] <= 0) { remove(i); i--; continue; }
            int t = type[i];
            float g = t == SMOKE || t == DUST ? -.4f : t == FIRE ? -1.8f : t == TRACER || t == MUZZLE ? 0 : 9.81f;
            float drag = t == SMOKE ? 1.2f : t == DUST ? 2.5f : t == FIRE ? 1.5f : t == BLOOD || t == SPLASH ? .6f : .15f;
            vy[i] -= g * dt;
            float k = (float) Math.exp(-drag * dt);
            vx[i] = vx[i] * k + (float) (windX * (1 - k) * (t == SMOKE ? 1 : .3)); vz[i] = vz[i] * k + (float) (windZ * (1 - k) * (t == SMOKE ? 1 : .3));
            vy[i] *= t == SMOKE || t == FIRE || t == DUST ? k : 1;
            x[i] += vx[i] * dt; y[i] += vy[i] * dt; z[i] += vz[i] * dt;
            size[i] += grow[i] * dt;
            rot[i] += spin[i] * dt;
            if ((t == DEBRIS || t == SPARK || t == BLOOD || t == SPLASH) && vy[i] < 0) {
                double ground = world.groundHeight(x[i], z[i]);
                if (y[i] < ground + .02) {
                    y[i] = (float) ground + .02f;
                    vy[i] = -vy[i] * (t == DEBRIS ? .3f : .1f); vx[i] *= .5f; vz[i] *= .5f;
                    if (t == SPARK || t == BLOOD || t == SPLASH) life[i] = Math.min(life[i], .08f);
                }
            }
        }
    }
    private void remove(int i) {
        int last = --count;
        if (i == last) return;
        x[i] = x[last]; y[i] = y[last]; z[i] = z[last]; vx[i] = vx[last]; vy[i] = vy[last]; vz[i] = vz[last];
        life[i] = life[last]; maxLife[i] = maxLife[last]; size[i] = size[last]; grow[i] = grow[last];
        rot[i] = rot[last]; spin[i] = spin[last]; color[i] = color[last]; type[i] = type[last]; alpha[i] = alpha[last]; stretch[i] = stretch[last];
    }
    public void clear() { count = 0; }

    // ------------------------------------------------------------------ presets
    public void impact(double px, double py, double pz, double nx, double ny, double nz, boolean metal) {
        for (int i = 0; i < (metal ? 7 : 4); i++)
            spawn(metal ? SPARK : DEBRIS, px, py, pz, nx * 3 + s() * 2.5, ny * 3 + r() * 2.5, nz * 3 + s() * 2.5, .25 + r() * .3, metal ? .025 : .03, 0, metal ? 0xFFD090 : 0x8A8278, 1);
        spawn(DUST, px + nx * .05, py + ny * .05, pz + nz * .05, nx * .8, ny * .8 + .2, nz * .8, .9 + r() * .6, .12, .5, metal ? 0x8A8A88 : 0xA8A092, .5);
    }
    public void blood(double px, double py, double pz, double dx, double dy, double dz) {
        for (int i = 0; i < 5; i++) spawn(BLOOD, px, py, pz, dx * 2 + s(), dy * 2 + r() * 1.5, dz * 2 + s(), .35 + r() * .3, .05 + r() * .04, .15, 0x5A0A0A, .9);
    }
    public void muzzle(double px, double py, double pz, double dx, double dy, double dz, double scale) {
        int i = spawn(MUZZLE, px, py, pz, 0, 0, 0, .05, .18 * scale, 0, 0xFFC070, 3);
        stretch[i] = 0;
        spawn(SMOKE, px + dx * .2, py + dy * .2, pz + dz * .2, dx * .8 + s() * .2, dy * .8 + .3, dz * .8 + s() * .2, .7 + r() * .5, .08 * scale, .5, 0xB8B4AC, .22);
    }
    public void tracer(double ax, double ay, double az, double bx, double by, double bz) {
        double dx = bx - ax, dy = by - ay, dz = bz - az, len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 2) return;
        double t = .3 + r() * .5;
        int i = spawn(TRACER, ax + dx * t, ay + dy * t, az + dz * t, dx / len * .001, dy / len * .001, dz / len * .001, .04, .012, 0, 0xFFE0A0, 2.5);
        stretch[i] = (float) Math.min(4, len * .25);
    }
    public void explosion(double px, double py, double pz) {
        for (int i = 0; i < 40; i++) spawn(FIRE, px + s(), py + r(), pz + s(), s() * 6, 2 + r() * 7, s() * 6, .5 + r() * .8, .9 + r(), 1.8, 0xFF8A30, 1);
        for (int i = 0; i < 36; i++) spawn(SMOKE, px + s() * 2, py + r() * 2, pz + s() * 2, s() * 4, 2 + r() * 5, s() * 4, 3 + r() * 4, 1.2 + r(), 1.5, 0x2A2826, .75);
        for (int i = 0; i < 30; i++) spawn(DEBRIS, px, py + .5, pz, s() * 12, 4 + r() * 10, s() * 12, 1.5 + r(), .08 + r() * .1, 0, 0x2A2A2A, 1);
        for (int i = 0; i < 30; i++) spawn(SPARK, px, py + .5, pz, s() * 14, 3 + r() * 10, s() * 14, .6 + r() * .6, .03, 0, 0xFFC060, 1);
    }
    public void glass(double px, double py, double pz, double dx, double dz) {
        for (int i = 0; i < 26; i++) spawn(DEBRIS, px + s() * .6, py + s() * .8, pz + s() * .6, dx * 2 + s() * 2, r() * 2, dz * 2 + s() * 2, 1 + r(), .025 + r() * .03, 0, 0xB8D8E0, .85);
    }
}

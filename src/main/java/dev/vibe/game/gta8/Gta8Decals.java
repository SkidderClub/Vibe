package dev.vibe.game.gta8;

/** Ring buffer of surface marks: tyre skids, bullet holes, scorch marks and blood pools. */
public final class Gta8Decals {
    public static final int SKID = 0, BULLET = 1, SCORCH = 2, BLOOD = 3;
    public static final int CAPACITY = 2400;
    public final float[] x = new float[CAPACITY], y = new float[CAPACITY], z = new float[CAPACITY];
    public final float[] nx = new float[CAPACITY], ny = new float[CAPACITY], nz = new float[CAPACITY];
    public final float[] ax = new float[CAPACITY], az = new float[CAPACITY], size = new float[CAPACITY], length = new float[CAPACITY], alpha = new float[CAPACITY];
    public final int[] type = new int[CAPACITY];
    public int count, head, revision;

    public void add(int kind, double px, double py, double pz, double pnx, double pny, double pnz, double dirX, double dirZ, double sz, double len, double a) {
        int i = head;
        head = (head + 1) % CAPACITY;
        if (count < CAPACITY) count++;
        type[i] = kind; x[i] = (float) px; y[i] = (float) py; z[i] = (float) pz;
        nx[i] = (float) pnx; ny[i] = (float) pny; nz[i] = (float) pnz;
        ax[i] = (float) dirX; az[i] = (float) dirZ; size[i] = (float) sz; length[i] = (float) len; alpha[i] = (float) a;
        revision++;
    }
    /** Adds a skid segment between the previous and current wheel contact points. */
    public void skid(double x0, double z0, double x1, double z1, double y, double width, double strength) {
        double dx = x1 - x0, dz = z1 - z0, len = Math.hypot(dx, dz);
        if (len < .05 || len > 3) return;
        add(SKID, (x0 + x1) / 2, y + .012, (z0 + z1) / 2, 0, 1, 0, dx / len, dz / len, width, len / 2 + .02, strength);
    }
    public void clear() { count = head = 0; revision++; }
}

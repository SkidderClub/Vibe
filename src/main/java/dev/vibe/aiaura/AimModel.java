package dev.vibe.aiaura;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Random;

/**
 * What AIAura V2 has of yours - ported from Nyx 26.2's AI Aura. Each hit you make yourself is kept as
 * where on them it landed: across and up from the nearest point of their hitbox, in shares of their
 * half width and half height. Each tick of your aim that led to such a hit is kept as how far off that
 * spot the aim was, how far toward it you turned and how far to the side. And each stretch between two
 * of your hits on someone is kept as how far out past them the aim got (or that it never left them),
 * with what it was made in: them going sideways to you or not, you in the air or on the ground.
 *
 * <p>And your aim itself, a tick at a time, in fights ({@link #step}): where it was against them, how
 * they had been going round you, how you had been turning - and the turn you then made. Played back
 * ({@link #act}), the turn you made from nearest where the aim is now: your own hand, not a model of one.
 */
public final class AimModel {
    private AimModel() {
    }

    /** Hits it wants before the aim wanders by them, turns before it turns at their speed, stretches before it overshoots. */
    public static final int ENOUGH = 120, PACED = 500, STRETCHES = 30;
    private static final int NEAR = 8;

    /** Entries of a few numbers each; full, the oldest go first. */
    private static final class Ring {
        final float[] data;
        final int width;
        int count, next;

        Ring(int max, int width) {
            data = new float[max * width];
            this.width = width;
        }

        void add(float... entry) {
            System.arraycopy(entry, 0, data, next * width, width);
            next = (next + 1) % (data.length / width);
            count = Math.min(count + 1, data.length / width);
        }

        void read(DataInputStream in, int entries) throws IOException {
            count = Math.min(entries, data.length / width);
            for (int i = 0; i < count * width; i++) data[i] = in.readFloat();
            in.skipBytes((entries - count) * width * 4);
            next = count % (data.length / width);
        }

        void write(DataOutputStream out) throws IOException {
            out.writeInt(count);
            for (int i = 0; i < count * width; i++) out.writeFloat(data[i]);
        }
    }

    /** Across and up; and how far off (log1p degrees), with the turn made toward and aside. */
    private static final Ring hits = new Ring(2000, 2), turns = new Ring(4000, 3);
    /** How far out the aim got between two hits, yaw and pitch - nothing and nothing where it stayed on them - and what that stretch was made in. */
    private static final Ring overs = new Ring(1000, 3);
    /** Your clicking in a fight, oldest first: milliseconds from one click to the next, 0 where a burst ended. */
    private static final Ring clicks = new Ring(3000, 1);
    /** Gaps it wants before it clicks in your rhythm. */
    public static final int CLICKS = 150;
    /**
     * Ticks of your aim in fights ({@link #sample}): off yaw and pitch from their middle, how far they went round
     * a tick and two ticks before, your turn a tick before (yaw, pitch), half their width (all degrees); the turn made.
     */
    private static final Ring steps = new Ring(30000, 9);
    /** Ticks of your aim it wants before it aims as you do: some two minutes of fighting. */
    public static final int STEPS = 2000;
    /** How much of a difference in each of a tick's numbers makes two ticks of yours alike (degrees). */
    private static final float[] ALIKE = {8f, 8f, 4f, 4f, 2f, 1.5f, 4f};
    /** Which of those turn over, left for right, when a tick of yours is mirrored. */
    private static final boolean[] SIDEWAYS = {true, false, true, true, true, false, false};
    private static final int NEAREST = 12;
    /**
     * Your swings in fights as the server sees them: ticks from one to the next, 0 for a second in the same tick,
     * -1 where a burst began. The server has nothing finer: a click is sent in the tick it is handled in.
     */
    private static final Ring swings = new Ring(6000, 1);
    /** Swings it wants before it clicks on your ticks. */
    public static final int SWINGS = 300;
    private static final int VERSION = 4;
    /** Where in your clicking (and in your swings) the run being replayed is, and how many more of it to play. */
    private static final int[] clickPlay = {-1, 0}, swingPlay = {-1, 0};
    private static File file;
    private static final Random RNG = new Random();

    public static int size() {
        return hits.count;
    }

    public static int turns() {
        return turns.count;
    }

    public static int overshoots() {
        return overs.count;
    }

    public static boolean ready() {
        return hits.count >= ENOUGH;
    }

    public static boolean paced() {
        return turns.count >= PACED;
    }

    public static void add(float across, float up) {
        hits.add(across, up);
    }

    /** One of your hits, across and up; only once it is {@link #ready}. */
    public static float[] pick() {
        int at = RNG.nextInt(hits.count) * 2;
        return new float[]{hits.data[at], hits.data[at + 1]};
    }

    /** One tick of your aim: off degrees from the spot, turned toward it by that much and aside by that much. */
    public static void pace(float off, float toward, float aside) {
        turns.add((float) Math.log1p(off), toward, aside);
    }

    /**
     * How far you turn in a tick from that far off, toward and aside; only once it is {@link #paced}.
     * Of the turns made from nearest that far, any but the slowest quarter - those are a turn's first
     * tick, getting going, which Acceleration is there for - and brisk, 0 to 1, says which.
     */
    // ponytail: every turn is looked at each tick, and "all but the slowest quarter" is a guess at mid-turn; sort them by how far off if 4000 is ever not enough
    public static float[] turn(float off, float brisk) {
        float at = (float) Math.log1p(off);
        float[] far = new float[NEAR];
        float[][] made = new float[NEAR][];
        Arrays.fill(far, Float.MAX_VALUE);
        for (int i = 0; i < turns.count; i++) {
            float d = Math.abs(turns.data[i * 3] - at);
            if (d >= far[NEAR - 1]) continue;
            int j = NEAR - 1;
            for (; j > 0 && far[j - 1] > d; j--) {
                far[j] = far[j - 1];
                made[j] = made[j - 1];
            }
            far[j] = d;
            // made from a little nearer or further than it was learned at, it is that much shorter or longer
            float was = (float) Math.expm1(turns.data[i * 3]), scale = was > 1f ? Math.max(0.6f, Math.min(1.6f, off / was)) : 1f;
            made[j] = new float[]{turns.data[i * 3 + 1] * scale, turns.data[i * 3 + 2] * scale};
        }
        Arrays.sort(made, new Comparator<float[]>() {
            @Override public int compare(float[] one, float[] other) { return Float.compare(one[0], other[0]); }
        });
        return made[Math.min(NEAR - 1, NEAR / 4 + (int) (brisk * (NEAR - NEAR / 4)))];
    }

    public static void overshot(float yaw, float pitch, int state) {
        overs.add(yaw, pitch, state);
    }

    /**
     * What the aim did in one of the stretches between your hits that were made in that state: how far
     * out it got, or nothing - and nothing until it has seen enough of them. outside asks for one where
     * it did get out.
     */
    public static float[] overshoot(int state, boolean outside) {
        int seen = 0, fit = 0, at = -1;
        for (int i = 0; i < overs.count; i++) {
            if (overs.data[i * 3 + 2] != state) continue;
            seen++;
            if (outside && overs.data[i * 3] == 0f && overs.data[i * 3 + 1] == 0f) continue;
            if (RNG.nextInt(++fit) == 0) at = i;         // each of those that fit as likely as the next
        }
        return seen < STRETCHES || at < 0 ? new float[2] : new float[]{overs.data[at * 3], overs.data[at * 3 + 1]};
    }

    /** One gap between two of your clicks, in milliseconds; 0 marks the end of a burst. */
    public static void click(float gap) {
        clicks.add(gap);
    }

    /** How many gaps of your clicking it has. */
    public static int clicks() {
        int gaps = 0;
        for (int i = 0; i < clicks.count; i++) if (clicks.data[i] > 0f) gaps++;
        return gaps;
    }

    public static boolean rhythm() {
        return clicks() >= CLICKS;
    }

    /** Your clicks a second, over everything it has of your clicking; 0 with nothing. */
    public static float cps() {
        float sum = 0f;
        int gaps = 0;
        for (int i = 0; i < clicks.count; i++) {
            if (clicks.data[i] <= 0f) continue;
            sum += clicks.data[i];
            gaps++;
        }
        return gaps == 0 ? 0f : 1000f * gaps / sum;
    }

    /**
     * The next gap of your clicking: a run of your own gaps played back in the order you made them, from a
     * random place, and another run once that one ends or a burst of yours did. Drawn one at a time they
     * would lose what makes them yours - a fast pair after a slow one, the drift of a burst. 0 when it has none.
     */
    public static float nextGap() {
        return play(clicks, 0f, clickPlay);
    }

    /** One tick of yours with swings in it: ticks since your last (-1: the first of a burst), and one 0 for each more in that tick. */
    public static void swing(int ticks) {
        swings.add(ticks);
    }

    /** How many tick gaps of your swinging it has. */
    public static int swings() {
        int gaps = 0;
        for (int i = 0; i < swings.count; i++) if (swings.data[i] >= 0f) gaps++;
        return gaps;
    }

    public static boolean swingRhythm() {
        return swings() >= SWINGS;
    }

    /** The ticks to your next swing, 0 for another in the same tick: a run of yours, as {@link #nextGap}. -1 when it has none. */
    public static int nextSwing() {
        return (int) play(swings, -1f, swingPlay);
    }

    /** A run of a rhythm of yours played back in your order from a random place; a value at or under end starts another run. end when it has none. */
    private static float play(Ring ring, float end, int[] at) {
        int count = ring.count, capacity = ring.data.length, oldest = count < capacity ? 0 : ring.next;
        for (int tries = 0; tries < 32 && count > 0; tries++) {
            if (at[0] < 0 || at[0] >= count || at[1] <= 0) {
                at[0] = RNG.nextInt(count);
                at[1] = 10 + RNG.nextInt(30);
            }
            float value = ring.data[(oldest + at[0]) % capacity];
            at[0]++;
            at[1]--;
            if (value > end) return value;
            at[0] = -1;                                  // the end of one of your bursts: a run from somewhere else
        }
        return end;
    }

    /**
     * A tick of yours from four ticks of where your aim and they were, newest first - each {yaw, pitch, the way
     * to their middle (yaw, pitch), half their width}: what the aim was at a tick before and the turn then made.
     */
    public static float[] sample(float[] now, float[] before, float[] twoBefore, float[] threeBefore) {
        float[] state = state(before, twoBefore, threeBefore), sample = Arrays.copyOf(state, 9);
        sample[7] = wrap(now[0] - before[0]);
        sample[8] = now[1] - before[1];
        return sample;
    }

    /** Where the aim is against them, from the last three ticks of it, newest first: what {@link #act} looks up. */
    public static float[] state(float[] last, float[] before, float[] twoBefore) {
        return new float[]{clamp(wrap(last[0] - last[2]), 90f), clamp(last[1] - last[3], 60f), clamp(wrap(last[2] - before[2]), 30f),
                clamp(wrap(before[2] - twoBefore[2]), 30f), clamp(wrap(last[0] - before[0]), 40f), clamp(last[1] - before[1], 30f),
                Math.min(60f, last[4])};
    }

    /** One tick of your aim in a fight ({@link #sample}). */
    public static void step(float[] sample) {
        steps.add(sample);
    }

    public static int steps() {
        return steps.count;
    }

    public static boolean cloned() {
        return steps.count >= STEPS;
    }

    /**
     * The turn you made from nearest this: of the few ticks of yours most alike it - each also mirrored,
     * left for right - one, the more alike the likelier. Pull, 0 up, also favours those of them that would
     * leave the aim nearer their middle, were they to go on round as they just did: your own turns, but more
     * often the ones that brought you back onto them. Nothing until it has any.
     */
    public static float[] act(float[] state) {
        return act(state, 0f);
    }

    /** {@link #act(float[])}, pulled toward their middle that much. */
    // ponytail: every tick of yours is looked at, twice, each tick it aims - 30 000 of them is some 400 000 sums; a k-d tree if that ever shows
    public static float[] act(float[] state, float pull) {
        float[] far = new float[NEAREST];
        int[] at = new int[NEAREST];
        boolean[] mirrored = new boolean[NEAREST];
        Arrays.fill(far, Float.MAX_VALUE);
        for (int i = 0; i < steps.count; i++) {
            for (int m = 0; m < 2; m++) {
                float d = 0f;
                for (int f = 0; f < 7 && d < far[NEAREST - 1]; f++) {
                    float value = steps.data[i * 9 + f];
                    if (m == 1 && SIDEWAYS[f]) value = -value;
                    float off = (value - state[f]) / ALIKE[f];
                    d += off * off;
                }
                if (d >= far[NEAREST - 1]) continue;
                int j = NEAREST - 1;
                for (; j > 0 && far[j - 1] > d; j--) {
                    far[j] = far[j - 1];
                    at[j] = at[j - 1];
                    mirrored[j] = mirrored[j - 1];
                }
                far[j] = d;
                at[j] = i;
                mirrored[j] = m == 1;
            }
        }
        float[] likely = new float[NEAREST];
        float total = 0f;
        for (int j = 0; j < NEAREST && far[j] < Float.MAX_VALUE; j++) {
            float yaw = steps.data[at[j] * 9 + 7] * (mirrored[j] ? -1f : 1f), pitch = steps.data[at[j] * 9 + 8];
            float leftYaw = (state[0] + yaw - state[2]) / ALIKE[0], leftPitch = (state[1] + pitch) / ALIKE[1];
            likely[j] = 1f / (float) (Math.sqrt(far[j] + pull * (leftYaw * leftYaw + leftPitch * leftPitch)) + 0.1D);
            total += likely[j];
        }
        if (total == 0f) return new float[2];
        float pick = RNG.nextFloat() * total;
        int j = 0;
        for (; j < NEAREST - 1 && likely[j + 1] > 0f; j++) {
            pick -= likely[j];
            if (pick <= 0f) break;
        }
        float yaw = steps.data[at[j] * 9 + 7];
        return new float[]{mirrored[j] ? -yaw : yaw, steps.data[at[j] * 9 + 8]};
    }

    /**
     * Your aim out of one of Record Fights' files: every tick of your own fighting in it - none of a fight the
     * aura had a part in, whose turning back is MoveFix's, not yours. A fight is one while you clicked in the
     * last second and they were within six blocks. How many ticks it learned.
     */
    public static int learnFrom(File csv) throws IOException {
        java.util.List<String> lines = java.nio.file.Files.readAllLines(csv.toPath());
        java.util.Set<String> aided = new java.util.HashSet<String>();
        for (String line : lines) {
            String[] c = line.split(",");
            if (c.length > 19 && !line.startsWith("tick") && c[19].equals("1")) aided.add(c[18]);
        }
        java.util.ArrayDeque<float[]> run = new java.util.ArrayDeque<float[]>();
        double lastTick = -9, clickedAt = -99, swungAt = -99;
        String fight = null, id = null;
        int learned = 0;
        for (String line : lines) {
            if (line.startsWith("tick")) continue;
            String[] c = line.split(",");
            if (c.length < 20 || aided.contains(c[18])) {
                run.clear();
                continue;
            }
            double tick = Double.parseDouble(c[0]);
            if (tick != lastTick + 1 || !c[18].equals(fight) || !c[17].equals(id)) {
                run.clear();
                clickedAt = swungAt = -99;
            }
            lastTick = tick;
            fight = c[18];
            id = c[17];
            if (Double.parseDouble(c[11]) > 0 || c.length > 20 && Double.parseDouble(c[20]) > 0) clickedAt = tick;
            int swung = (int) Double.parseDouble(c[11]);    // your swings as sent, tick by tick: your clicking as the server had it
            if (swung > 0) {
                swing(tick - swungAt <= 20 ? (int) (tick - swungAt) : -1);
                for (int i = 1; i < swung; i++) swing(0);
                swungAt = tick;
            }
            double sx = Double.parseDouble(c[4]), sy = Double.parseDouble(c[5]), sz = Double.parseDouble(c[6]);
            double ex = Double.parseDouble(c[7]), ey = Double.parseDouble(c[8]), ez = Double.parseDouble(c[9]);
            run.addFirst(seen(Float.parseFloat(c[2]), Float.parseFloat(c[3]), sx, sy + 1.62D, sz, ex, ey + 0.9D, ez, 0.4D));
            if (run.size() > 4) run.removeLast();
            if (run.size() == 4 && tick - clickedAt <= 20 && Double.parseDouble(c[10]) <= 6.0D) {
                float[][] r = run.toArray(new float[0][]);
                step(sample(r[0], r[1], r[2], r[3]));
                learned++;
            }
        }
        return learned;
    }

    /** A tick of where the aim and they are: {yaw, pitch, the way from the eye to their middle (yaw, pitch), half their width, all degrees}. */
    public static float[] seen(float yaw, float pitch, double eyeX, double eyeY, double eyeZ, double midX, double midY, double midZ, double halfWidth) {
        double dx = midX - eyeX, dz = midZ - eyeZ, flat = Math.max(0.3D, Math.sqrt(dx * dx + dz * dz));
        return new float[]{yaw, pitch, (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f, (float) -Math.toDegrees(Math.atan2(midY - eyeY, flat)),
                (float) Math.toDegrees(Math.atan2(halfWidth, flat))};
    }

    private static float wrap(float angle) {
        angle %= 360f;
        if (angle >= 180f) angle -= 360f;
        if (angle < -180f) angle += 360f;
        return angle;
    }

    private static float clamp(float value, float limit) {
        return Math.max(-limit, Math.min(limit, value));
    }

    public static void clear() {
        for (Ring r : new Ring[]{hits, turns, overs, clicks, steps, swings}) r.count = r.next = 0;
        clickPlay[0] = swingPlay[0] = -1;
        save();
    }

    public static boolean loaded() {
        return file != null;
    }

    public static void load(File from) {
        file = from;
        clickPlay[0] = swingPlay[0] = -1;
        for (Ring r : new Ring[]{hits, turns, overs, clicks, steps, swings}) r.count = r.next = 0;
        if (!from.exists()) return;
        try (DataInputStream in = new DataInputStream(new java.io.BufferedInputStream(new FileInputStream(from)))) {
            int version = in.readInt();
            if (version < 1 || version > VERSION) throw new IOException("unknown version");
            hits.read(in, in.readInt());
            turns.read(in, in.readInt());
            overs.read(in, in.readInt());
            if (version >= 2) clicks.read(in, in.readInt());   // the first files had no clicking in them
            if (version >= 3) steps.read(in, in.readInt());    // nor your aim, tick by tick
            if (version >= 4) swings.read(in, in.readInt());   // nor your swings
        } catch (IOException | RuntimeException broken) {
            for (Ring r : new Ring[]{hits, turns, overs, clicks, steps, swings}) r.count = r.next = 0;   // a cut-off file is nothing learned, not a crash
        }
    }

    public static void save() {
        if (file == null) return;
        file.getParentFile().mkdirs();
        File part = new File(file.getPath() + ".part");    // written whole and then put in place: a game closed mid-write leaves the last one
        try {
            try (DataOutputStream out = new DataOutputStream(new java.io.BufferedOutputStream(new FileOutputStream(part)))) {
                out.writeInt(VERSION);
                hits.write(out);
                turns.write(out);
                overs.write(out);
                clicks.write(out);
                steps.write(out);
                swings.write(out);
            }
            java.nio.file.Files.move(part.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException failed) {
            // kept in memory; the next save writes again
        }
    }

    /** Test seam: forget the file so a test's clear() writes nothing. */
    static void detach() {
        file = null;
    }
}

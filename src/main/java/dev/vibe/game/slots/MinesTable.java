package dev.vibe.game.slots;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Authoritatively generated 5x5 virtual Mines board; mine cells stay hidden until chosen. */
public final class MinesTable {
    public static final int SIZE = 5, CELLS = SIZE * SIZE, MINES = 3;
    private final boolean[] mines = new boolean[CELLS];
    private final boolean[] revealed = new boolean[CELLS];
    private boolean complete;
    public MinesTable(SecureRandom random) {
        if (random == null) throw new IllegalArgumentException("Secure RNG required");
        List<Integer> cells = new ArrayList<Integer>(); for (int index = 0; index < CELLS; index++) cells.add(index);
        Collections.shuffle(cells, random); for (int index = 0; index < MINES; index++) mines[cells.get(index)] = true;
    }
    public boolean reveal(int cell) {
        if (complete || cell < 0 || cell >= CELLS || revealed[cell]) return false;
        revealed[cell] = true; if (mines[cell]) { complete = true; return false; } return true;
    }
    public boolean isRevealed(int cell) { return cell >= 0 && cell < CELLS && revealed[cell]; }
    public boolean isComplete() { return complete; }
    public int safeReveals() { int count = 0; for (int index = 0; index < CELLS; index++) if (revealed[index] && !mines[index]) count++; return count; }
    public double multiplier() {
        int safe = safeReveals(); if (safe == 0) return 0.0D;
        double survive = 1.0D; for (int revealedSafe = 0; revealedSafe < safe; revealedSafe++) survive *= (CELLS - MINES - revealedSafe) / (double) (CELLS - revealedSafe);
        return 0.94D / survive;
    }
    /** Computes cashout without mutating round state; the wallet transaction is committed first. */
    public long cashout(long bet) { if (bet <= 0 || complete || safeReveals() == 0) return 0L; return (long) Math.floor(bet * multiplier()); }
    /** Each fixed safe-tile cashout point has exactly this configured expected return before rounding. */
    public static double expectedReturn() { return 0.94D; }
}

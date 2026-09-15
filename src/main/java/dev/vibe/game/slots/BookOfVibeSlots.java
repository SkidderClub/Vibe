package dev.vibe.game.slots;

import java.security.SecureRandom;

/** Five-reel Book of Vibe machine. Outcome generation is separate from UI animation. */
public final class BookOfVibeSlots {
    public enum Symbol {
        SOL("Sol", 30, new int[] {0, 0, 0, 1, 2}),
        LUNA("Luna", 25, new int[] {0, 0, 0, 1, 2}),
        LEAF("Leaf", 20, new int[] {0, 0, 0, 1, 3}),
        COMET("Comet", 14, new int[] {0, 0, 1, 2, 5}),
        CROWN("Crown", 8, new int[] {0, 0, 1, 5, 10}),
        VIBE_BOOK("Vibe Book", 2, new int[] {0, 0, 2, 6, 30}),
        WILD("Wild", 1, new int[] {0, 0, 5, 15, 50}),
        RIFT("Rift", 2, new int[] {0, 0, 0, 0, 0});

        private final String label;
        private final int weight;
        private final int[] payouts;
        Symbol(String label, int weight, int[] payouts) { this.label = label; this.weight = weight; this.payouts = payouts; }
        public String getLabel() { return label; }
        public int getWeight() { return weight; }
        public int payout(int count) { return count < 0 || count >= payouts.length ? 0 : payouts[count]; }
    }

    private static final int[][] PAYLINES = {
            {1, 1, 1, 1, 1}, {0, 0, 0, 0, 0}, {2, 2, 2, 2, 2},
            {0, 1, 2, 1, 0}, {2, 1, 0, 1, 2}
    };
    private static final int TOTAL_WEIGHT = totalWeight();

    public static final class Outcome {
        private final Symbol[][] reels;
        private final long payout;
        private final int winningLines;
        private final int scatterCount;
        private final boolean[] winningPaylines;

        Outcome(Symbol[][] reels, long payout, int winningLines, int scatterCount, boolean[] winningPaylines) {
            this.reels = reels; this.payout = payout; this.winningLines = winningLines; this.scatterCount = scatterCount; this.winningPaylines = winningPaylines;
        }
        public Symbol get(int row, int reel) { return reels[row][reel]; }
        public long getPayout() { return payout; }
        public int getWinningLines() { return winningLines; }
        public int getScatterCount() { return scatterCount; }
        public boolean isWinningPayline(int index) { return index >= 0 && index < winningPaylines.length && winningPaylines[index]; }
        public String resultLabel() {
            if (payout <= 0L) return "No winning line";
            return winningLines + (winningLines == 1 ? " winning line" : " winning lines") + " • paid " + payout + " Vibe Tokens";
        }
    }

    public Outcome spin(int bet, SecureRandom random) {
        if (bet <= 0 || random == null) throw new IllegalArgumentException("A positive bet and secure RNG are required");
        Symbol[][] reels = new Symbol[3][5];
        int scatterCount = 0;
        for (int row = 0; row < 3; row++) for (int reel = 0; reel < 5; reel++) {
            Symbol symbol = roll(random); reels[row][reel] = symbol;
            if (symbol == Symbol.RIFT) scatterCount++;
        }
        int multiplier = 0, wins = 0; boolean[] winningPaylines = new boolean[PAYLINES.length];
        for (int index = 0; index < PAYLINES.length; index++) {
            int[] line = PAYLINES[index];
            int linePayout = linePayout(reels, line);
            if (linePayout > 0) { multiplier += linePayout; wins++; winningPaylines[index] = true; }
        }
        int scatterMultiplier = scatterPayout(scatterCount);
        if (scatterMultiplier > 0) wins++;
        return new Outcome(reels, (long) (multiplier + scatterMultiplier) * bet, wins, scatterCount, winningPaylines);
    }

    public static int[][] paylines() {
        int[][] result = new int[PAYLINES.length][];
        for (int index = 0; index < PAYLINES.length; index++) result[index] = PAYLINES[index].clone();
        return result;
    }

    /** Exact expected payout for one total bet; linearity covers overlapping paylines. */
    public static double expectedReturn() {
        Symbol[] symbols = Symbol.values();
        double lineReturn = enumerateLine(symbols, new Symbol[5], 0, 1.0D);
        return PAYLINES.length * lineReturn + scatterExpectedReturn();
    }

    public static void validateConfiguration() {
        if (TOTAL_WEIGHT <= 0) throw new IllegalStateException("Slots symbol weights must be positive");
        for (Symbol symbol : Symbol.values()) {
            if (symbol.weight <= 0) throw new IllegalStateException("Every slot symbol needs a positive weight");
            for (int payout : symbol.payouts) if (payout < 0) throw new IllegalStateException("Negative slot payout");
        }
        double expected = expectedReturn();
        if (Double.isNaN(expected) || expected >= SlotConfig.MAX_PLAYER_RETURN) {
            throw new IllegalStateException("Book of Vibe must retain a configurable house edge");
        }
    }

    private static double enumerateLine(Symbol[] symbols, Symbol[] line, int index, double chance) {
        if (index == line.length) return chance * linePayout(line);
        double result = 0.0D;
        for (Symbol symbol : symbols) {
            line[index] = symbol;
            result += enumerateLine(symbols, line, index + 1, chance * symbol.weight / TOTAL_WEIGHT);
        }
        return result;
    }

    private static double scatterExpectedReturn() {
        double chance = Symbol.RIFT.weight / (double) TOTAL_WEIGHT;
        double expected = 0.0D;
        for (int count = 0; count <= 15; count++) {
            double probability = choose(15, count) * Math.pow(chance, count) * Math.pow(1.0D - chance, 15 - count);
            expected += probability * scatterPayout(count);
        }
        return expected;
    }

    private static long choose(int n, int k) {
        long result = 1L;
        for (int value = 1; value <= k; value++) result = result * (n - (value - 1)) / value;
        return result;
    }

    private static Symbol roll(SecureRandom random) {
        int target = random.nextInt(TOTAL_WEIGHT);
        for (Symbol symbol : Symbol.values()) {
            target -= symbol.weight;
            if (target < 0) return symbol;
        }
        return Symbol.SOL; // Unreachable after configuration validation.
    }

    private static int linePayout(Symbol[][] reels, int[] rows) {
        Symbol[] line = new Symbol[5];
        for (int reel = 0; reel < 5; reel++) line[reel] = reels[rows[reel]][reel];
        return linePayout(line);
    }

    /** Best compatible symbol pays once per line; wild substitutes but never double-pays. */
    private static int linePayout(Symbol[] line) {
        int best = 0;
        for (Symbol candidate : Symbol.values()) {
            if (candidate == Symbol.RIFT) continue;
            int run = 0;
            for (Symbol symbol : line) {
                if (symbol != candidate && symbol != Symbol.WILD) break;
                run++;
            }
            best = Math.max(best, candidate.payout(run));
        }
        return best;
    }

    private static int scatterPayout(int count) {
        return count == 3 ? 1 : count == 4 ? 2 : count >= 5 ? 10 : 0;
    }

    private static int totalWeight() {
        int total = 0; for (Symbol symbol : Symbol.values()) total += symbol.weight; return total;
    }
}

package dev.vibe.game.slots;

import java.security.SecureRandom;

/** Original virtual collectible case with explicit rarity weights and values. */
public final class VibeCaseTable {
    public enum Rarity {
        COMMON("Common", 700, 0), RARE("Rare", 240, 1), MYTHIC("Mythic", 55, 4), PRISMATIC("Prismatic", 5, 16);
        final String title; final int weight, multiplier;
        Rarity(String title, int weight, int multiplier) { this.title = title; this.weight = weight; this.multiplier = multiplier; }
    }
    private static final String[][] ITEMS = {
            {"Static Sticker", "Orbital Tag", "Soft Neon Patch"}, {"Pulse Chroma", "Aurora Marker", "Vector Charm"},
            {"Lunar Engine", "Dream Circuit", "Solar Mantle"}, {"Vibe Singularity", "Prism Crown"}
    };
    private static final int TOTAL_WEIGHT = 1000;
    private VibeCaseTable() { }
    public static final class Result {
        private final Rarity rarity; private final String item; private final long payout;
        Result(Rarity rarity, String item, long payout) { this.rarity = rarity; this.item = item; this.payout = payout; }
        public Rarity getRarity() { return rarity; } public String getItem() { return item; } public long getPayout() { return payout; }
        public String label() { return "Vibe Case: " + rarity.title + " " + item; }
    }
    public static Result open(long price, SecureRandom random) {
        if (price <= 0 || random == null) throw new IllegalArgumentException("Opening cost and RNG are required");
        int target = random.nextInt(TOTAL_WEIGHT); Rarity selected = Rarity.COMMON;
        for (Rarity rarity : Rarity.values()) { target -= rarity.weight; if (target < 0) { selected = rarity; break; } }
        String[] items = ITEMS[selected.ordinal()];
        return new Result(selected, items[random.nextInt(items.length)], price * selected.multiplier);
    }
    public static double expectedReturn() { double result = 0.0D; for (Rarity rarity : Rarity.values()) result += rarity.weight / 1000.0D * rarity.multiplier; return result; }
    public static void validate() { if (expectedReturn() >= 1.0D) throw new IllegalStateException("Vibe Case requires a virtual house edge"); }
}

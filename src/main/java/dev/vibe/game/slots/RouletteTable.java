package dev.vibe.game.slots;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** European one-zero roulette resolver covering standard inside and outside wagers. */
public final class RouletteTable {
    public enum Type { STRAIGHT, RED, BLACK, ODD, EVEN, LOW, HIGH, DOZEN, COLUMN, SPLIT, STREET, CORNER }
    public static final class Bet {
        private final Type type; private final long amount; private final int[] numbers;
        public Bet(Type type, long amount, int... numbers) {
            if (type == null || amount <= 0 || numbers == null || numbers.length == 0) throw new IllegalArgumentException("Invalid roulette bet");
            this.type = type; this.amount = amount; this.numbers = numbers.clone(); validate();
        }
        public Type getType() { return type; } public long getAmount() { return amount; }
        boolean wins(int number) { for (int value : numbers) if (value == number) return true; return false; }
        int netMultiplier() { switch (type) { case STRAIGHT: return 35; case SPLIT: return 17; case STREET: return 11; case CORNER: return 8; case DOZEN: case COLUMN: return 2; default: return 1; } }
        private void validate() {
            int expected = type == Type.STRAIGHT ? 1 : type == Type.SPLIT ? 2 : type == Type.STREET ? 3 : type == Type.CORNER ? 4 : type == Type.DOZEN || type == Type.COLUMN ? 12 : type == Type.RED || type == Type.BLACK || type == Type.ODD || type == Type.EVEN || type == Type.LOW || type == Type.HIGH ? 18 : -1;
            if (expected != numbers.length) throw new IllegalArgumentException("Wrong roulette coverage");
            for (int number : numbers) if (number < 0 || number > 36) throw new IllegalArgumentException("Invalid roulette number");
        }
    }
    public static final class Result {
        private final int number; private final long wager, payout;
        Result(int number, long wager, long payout) { this.number = number; this.wager = wager; this.payout = payout; }
        public int getNumber() { return number; } public long getWager() { return wager; } public long getPayout() { return payout; }
        public String label() { return "European roulette landed " + number + (payout > 0 ? " • winning table" : " • no winning bets"); }
    }
    private RouletteTable() { }
    public static Result spin(List<Bet> bets, SecureRandom random) {
        if (bets == null || bets.isEmpty() || random == null) throw new IllegalArgumentException("At least one roulette bet is required");
        return resolve(bets, random.nextInt(37));
    }
    /** Deterministic resolver useful for validation after an authoritative roll is made. */
    public static Result resolve(List<Bet> bets, int number) {
        if (bets == null || bets.isEmpty() || number < 0 || number > 36) throw new IllegalArgumentException("Invalid roulette resolution");
        long wager = 0L, payout = 0L;
        for (Bet bet : bets) { wager = Math.addExact(wager, bet.amount); if (bet.wins(number)) payout = Math.addExact(payout, bet.amount * (bet.netMultiplier() + 1L)); }
        return new Result(number, wager, payout);
    }
    public static Bet colour(boolean red, long amount) { return new Bet(red ? Type.RED : Type.BLACK, amount, colourNumbers(red)); }
    public static List<Integer> redNumbers() { List<Integer> values = new ArrayList<Integer>(); for (int n : colourNumbers(true)) values.add(n); return Collections.unmodifiableList(values); }
    private static int[] colourNumbers(boolean red) {
        int[] values = new int[18]; int at = 0;
        for (int number = 1; number <= 36; number++) if (isRed(number) == red) values[at++] = number;
        return values;
    }
    public static boolean isRed(int number) { return number != 0 && (number % 2 == 1 ? number <= 10 || number >= 19 && number <= 28 : number >= 11 && number <= 18 || number >= 29); }
}

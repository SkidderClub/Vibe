package dev.vibe.game.slots;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Heads-up Texas Hold'em all-in table with a 3% virtual rake. */
public final class HoldemTable {
    public enum Street { PREFLOP, FLOP, TURN, RIVER, SHOWDOWN }
    private final CardDeck deck;
    private final List<PlayingCard> player = new ArrayList<PlayingCard>();
    private final List<PlayingCard> house = new ArrayList<PlayingCard>();
    private final List<PlayingCard> board = new ArrayList<PlayingCard>();
    private final long wager;
    private Street street = Street.PREFLOP;

    public HoldemTable(long wager, SecureRandom random) {
        if (wager <= 0L) throw new IllegalArgumentException("Positive virtual wager required");
        this.wager = wager; deck = new CardDeck(1, random);
        player.add(deck.deal()); house.add(deck.deal()); player.add(deck.deal()); house.add(deck.deal());
    }
    public List<PlayingCard> getPlayer() { return Collections.unmodifiableList(player); }
    public List<PlayingCard> getHouse() { return Collections.unmodifiableList(house); }
    public List<PlayingCard> getBoard() { return Collections.unmodifiableList(board); }
    public Street getStreet() { return street; }
    public boolean isComplete() { return street == Street.SHOWDOWN; }
    public void advance() {
        if (street == Street.PREFLOP) { dealBoard(3); street = Street.FLOP; }
        else if (street == Street.FLOP) { dealBoard(1); street = Street.TURN; }
        else if (street == Street.TURN) { dealBoard(1); street = Street.RIVER; }
        else if (street == Street.RIVER) street = Street.SHOWDOWN;
    }
    public long payout() {
        if (!isComplete()) throw new IllegalStateException("Showdown has not finished");
        int comparison = evaluate(player, board).compareTo(evaluate(house, board));
        if (comparison > 0) return wager * 194L / 100L;
        if (comparison == 0) return wager * 97L / 100L;
        return 0L;
    }
    public String result() {
        if (!isComplete()) return "Texas Hold'em " + street.name().toLowerCase(java.util.Locale.ROOT);
        int comparison = evaluate(player, board).compareTo(evaluate(house, board));
        return comparison > 0 ? "Texas Hold'em: your " + evaluate(player, board).name() + " wins" : comparison == 0 ? "Texas Hold'em: split pot" : "Texas Hold'em: house " + evaluate(house, board).name() + " wins";
    }
    /** Equal heads-up cards with a 3% virtual rake return 97% on average. */
    public static double expectedReturn() { return 0.97D; }

    private void dealBoard(int count) { for (int index = 0; index < count; index++) board.add(deck.deal()); }

    public static HandScore evaluate(List<PlayingCard> hole, List<PlayingCard> board) {
        if (hole == null || board == null || hole.size() + board.size() != 7) throw new IllegalArgumentException("Hold'em needs seven cards");
        List<PlayingCard> all = new ArrayList<PlayingCard>(hole); all.addAll(board); HandScore best = null;
        for (int a = 0; a < 7; a++) for (int b = a + 1; b < 7; b++) {
            List<PlayingCard> five = new ArrayList<PlayingCard>();
            for (int card = 0; card < 7; card++) if (card != a && card != b) five.add(all.get(card));
            HandScore score = evaluateFive(five); if (best == null || score.compareTo(best) > 0) best = score;
        }
        return best;
    }

    public static final class HandScore implements Comparable<HandScore> {
        private final int category; private final int[] kickers;
        HandScore(int category, int... kickers) { this.category = category; this.kickers = kickers; }
        public String name() { return new String[] {"High card", "Pair", "Two pair", "Three of a kind", "Straight", "Flush", "Full house", "Four of a kind", "Straight flush"}[category]; }
        @Override public int compareTo(HandScore other) {
            if (category != other.category) return category - other.category;
            for (int index = 0; index < Math.min(kickers.length, other.kickers.length); index++) if (kickers[index] != other.kickers[index]) return kickers[index] - other.kickers[index];
            return 0;
        }
    }

    private static HandScore evaluateFive(List<PlayingCard> cards) {
        int[] count = new int[15]; boolean flush = true; PlayingCard.Suit suit = cards.get(0).getSuit();
        for (PlayingCard card : cards) { count[card.getRank()]++; if (card.getSuit() != suit) flush = false; }
        int straight = straightHigh(count); if (flush && straight > 0) return new HandScore(8, straight);
        int quad = ofKind(count, 4); if (quad > 0) return new HandScore(7, quad, highestExcept(count, quad));
        int trip = ofKind(count, 3), pair = highestWithCount(count, 2, trip); if (trip > 0 && pair > 0) return new HandScore(6, trip, pair);
        if (flush) return new HandScore(5, descending(count));
        if (straight > 0) return new HandScore(4, straight);
        if (trip > 0) return new HandScore(3, prepend(trip, descendingExcept(count, trip)));
        int highPair = ofKind(count, 2), lowPair = highestWithCount(count, 2, highPair);
        if (highPair > 0 && lowPair > 0) return new HandScore(2, highPair, lowPair, highestExcept(count, highPair, lowPair));
        if (highPair > 0) return new HandScore(1, prepend(highPair, descendingExcept(count, highPair)));
        return new HandScore(0, descending(count));
    }
    private static int straightHigh(int[] count) {
        for (int high = 14; high >= 5; high--) { boolean found = true; for (int rank = high; rank > high - 5; rank--) if (count[rank] == 0) found = false; if (found) return high; }
        return count[14] > 0 && count[2] > 0 && count[3] > 0 && count[4] > 0 && count[5] > 0 ? 5 : 0;
    }
    private static int ofKind(int[] count, int number) { for (int rank = 14; rank >= 2; rank--) if (count[rank] == number) return rank; return 0; }
    private static int highestWithCount(int[] count, int number, int ignored) { for (int rank = 14; rank >= 2; rank--) if (rank != ignored && count[rank] == number) return rank; return 0; }
    private static int highestExcept(int[] count, int... ignored) { outer: for (int rank = 14; rank >= 2; rank--) { for (int skip : ignored) if (rank == skip) continue outer; if (count[rank] > 0) return rank; } return 0; }
    private static int[] descendingExcept(int[] count, int... ignored) { int[] all = descending(count); int[] result = new int[all.length - ignored.length]; int target = 0; outer: for (int rank : all) { for (int skip : ignored) if (rank == skip) continue outer; if (target < result.length) result[target++] = rank; } return result; }
    private static int[] descending(int[] count) { int[] result = new int[5]; int target = 0; for (int rank = 14; rank >= 2; rank--) for (int n = 0; n < count[rank]; n++) result[target++] = rank; return result; }
    private static int[] prepend(int value, int[] remaining) { int[] result = new int[remaining.length + 1]; result[0] = value; System.arraycopy(remaining, 0, result, 1, remaining.length); return result; }
}

package dev.vibe.game.slots;

/** Immutable standard-deck card shared by Blackjack and Texas Hold'em. */
public final class PlayingCard {
    public enum Suit { CLUBS('♣'), DIAMONDS('♦'), HEARTS('♥'), SPADES('♠'); final char glyph; Suit(char glyph) { this.glyph = glyph; } }
    private final int rank;
    private final Suit suit;

    public PlayingCard(int rank, Suit suit) {
        if (rank < 2 || rank > 14 || suit == null) throw new IllegalArgumentException("Invalid card");
        this.rank = rank; this.suit = suit;
    }
    public int getRank() { return rank; }
    public Suit getSuit() { return suit; }
    public int blackjackValue() { return rank == 14 ? 11 : Math.min(10, rank); }
    public String shortName() {
        String face = rank <= 10 ? String.valueOf(rank) : rank == 11 ? "J" : rank == 12 ? "Q" : rank == 13 ? "K" : "A";
        return face + suit.glyph;
    }
}

package dev.vibe.game.slots;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Standard virtual blackjack: dealer stands on all 17, doubles and one split are supported. */
public final class BlackjackTable {
    public static final int DEFAULT_DECKS = 6;
    private final CardDeck deck;
    private final List<Hand> hands = new ArrayList<Hand>();
    private final List<PlayingCard> dealer = new ArrayList<PlayingCard>();
    private int activeHand;
    private boolean complete;

    public static final class Hand {
        private final List<PlayingCard> cards = new ArrayList<PlayingCard>();
        private long bet; private boolean stood, doubled;
        private Hand(long bet) { this.bet = bet; }
        public List<PlayingCard> getCards() { return Collections.unmodifiableList(cards); }
        public long getBet() { return bet; }
        public int value() { return BlackjackTable.value(cards); }
        public boolean isBust() { return value() > 21; }
        public boolean isBlackjack() { return cards.size() == 2 && value() == 21; }
        public boolean isStood() { return stood; }
    }

    public BlackjackTable(long bet, SecureRandom random) {
        if (bet <= 0L) throw new IllegalArgumentException("Positive virtual wager required");
        deck = new CardDeck(DEFAULT_DECKS, random); Hand hand = new Hand(bet); hands.add(hand);
        hand.cards.add(deck.deal()); dealer.add(deck.deal()); hand.cards.add(deck.deal()); dealer.add(deck.deal());
        if (hand.isBlackjack() || dealerValue() == 21) { hand.stood = true; complete = true; }
    }

    public List<Hand> getHands() { return Collections.unmodifiableList(hands); }
    public List<PlayingCard> getDealerCards() { return Collections.unmodifiableList(dealer); }
    public boolean isComplete() { return complete; }
    public int getActiveHand() { return activeHand; }
    public boolean canHit() { return !complete && !current().stood && !current().doubled; }
    public boolean canDouble() { return !complete && current().cards.size() == 2 && !current().doubled; }
    public boolean canSplit() { return !complete && hands.size() == 1 && current().cards.size() == 2 && current().cards.get(0).getRank() == current().cards.get(1).getRank(); }

    public void hit() { if (!canHit()) return; current().cards.add(deck.deal()); if (current().isBust() || current().value() == 21) advance(); }
    public long doubleDown() {
        if (!canDouble()) return 0L;
        Hand hand = current(); hand.bet *= 2L; hand.doubled = true; hand.cards.add(deck.deal()); advance(); return hand.bet / 2L;
    }
    public long split() {
        if (!canSplit()) return 0L;
        Hand original = current(), second = new Hand(original.bet); second.cards.add(original.cards.remove(1));
        original.cards.add(deck.deal()); second.cards.add(deck.deal()); hands.add(second); return second.bet;
    }
    public void stand() { if (!complete) { current().stood = true; advance(); } }

    /** Total returned virtual tokens, including returned stakes for wins and pushes. */
    public long payout() {
        if (!complete) throw new IllegalStateException("Round is unfinished");
        while (dealerValue() < 17) dealer.add(deck.deal());
        int dealerValue = dealerValue(); boolean dealerBlackjack = dealer.size() == 2 && dealerValue == 21;
        long payout = 0L;
        for (Hand hand : hands) {
            if (hand.isBust()) continue;
            if (hand.isBlackjack() && !dealerBlackjack) { payout += hand.bet + hand.bet * 3L / 2L; continue; }
            if (dealerValue > 21 || hand.value() > dealerValue) payout += hand.bet * 2L;
            else if (hand.value() == dealerValue) payout += hand.bet;
        }
        return payout;
    }
    public String result() {
        if (!complete) return "Blackjack in progress";
        return "Dealer " + dealerValue() + " • player " + current().value();
    }

    private Hand current() { return hands.get(activeHand); }
    private void advance() {
        if (activeHand + 1 < hands.size()) activeHand++;
        else complete = true;
    }
    private int dealerValue() { return value(dealer); }
    private static int value(List<PlayingCard> cards) {
        int total = 0, aces = 0;
        for (PlayingCard card : cards) { total += card.blackjackValue(); if (card.getRank() == 14) aces++; }
        while (total > 21 && aces-- > 0) total -= 10;
        return total;
    }
}

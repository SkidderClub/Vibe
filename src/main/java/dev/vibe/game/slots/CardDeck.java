package dev.vibe.game.slots;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Securely shuffled standard 52-card deck; no card is ever dealt twice. */
final class CardDeck {
    private final List<PlayingCard> cards = new ArrayList<PlayingCard>();
    CardDeck(int decks, SecureRandom random) {
        if (decks < 1 || random == null) throw new IllegalArgumentException("Deck count and RNG are required");
        for (int copy = 0; copy < decks; copy++) for (PlayingCard.Suit suit : PlayingCard.Suit.values()) for (int rank = 2; rank <= 14; rank++) cards.add(new PlayingCard(rank, suit));
        Collections.shuffle(cards, random);
    }
    PlayingCard deal() {
        if (cards.isEmpty()) throw new IllegalStateException("Deck exhausted");
        return cards.remove(cards.size() - 1);
    }
}

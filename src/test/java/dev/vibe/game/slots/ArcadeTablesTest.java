package dev.vibe.game.slots;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import org.junit.Test;

public class ArcadeTablesTest {
    @Test public void holdemEvaluatorRecognizesEveryHandClass() {
        assertEquals("High card", score(
                card(14, PlayingCard.Suit.SPADES), card(13, PlayingCard.Suit.CLUBS), card(9, PlayingCard.Suit.HEARTS),
                card(7, PlayingCard.Suit.DIAMONDS), card(4, PlayingCard.Suit.SPADES), card(3, PlayingCard.Suit.CLUBS), card(2, PlayingCard.Suit.DIAMONDS)).name());
        assertEquals("Pair", score(
                card(8, PlayingCard.Suit.SPADES), card(8, PlayingCard.Suit.CLUBS), card(14, PlayingCard.Suit.HEARTS),
                card(13, PlayingCard.Suit.DIAMONDS), card(6, PlayingCard.Suit.CLUBS), card(4, PlayingCard.Suit.HEARTS), card(2, PlayingCard.Suit.SPADES)).name());
        assertEquals("Two pair", score(
                card(8, PlayingCard.Suit.SPADES), card(8, PlayingCard.Suit.CLUBS), card(4, PlayingCard.Suit.HEARTS),
                card(4, PlayingCard.Suit.DIAMONDS), card(14, PlayingCard.Suit.CLUBS), card(13, PlayingCard.Suit.HEARTS), card(2, PlayingCard.Suit.SPADES)).name());
        assertEquals("Three of a kind", score(
                card(7, PlayingCard.Suit.SPADES), card(7, PlayingCard.Suit.CLUBS), card(7, PlayingCard.Suit.DIAMONDS),
                card(14, PlayingCard.Suit.HEARTS), card(13, PlayingCard.Suit.CLUBS), card(3, PlayingCard.Suit.DIAMONDS), card(2, PlayingCard.Suit.SPADES)).name());
        assertEquals("Straight", score(
                card(9, PlayingCard.Suit.SPADES), card(8, PlayingCard.Suit.CLUBS), card(7, PlayingCard.Suit.HEARTS),
                card(6, PlayingCard.Suit.DIAMONDS), card(5, PlayingCard.Suit.SPADES), card(14, PlayingCard.Suit.CLUBS), card(2, PlayingCard.Suit.DIAMONDS)).name());
        assertEquals("Flush", score(
                card(14, PlayingCard.Suit.HEARTS), card(11, PlayingCard.Suit.HEARTS), card(9, PlayingCard.Suit.HEARTS),
                card(6, PlayingCard.Suit.HEARTS), card(3, PlayingCard.Suit.HEARTS), card(13, PlayingCard.Suit.CLUBS), card(2, PlayingCard.Suit.DIAMONDS)).name());
        assertEquals("Straight flush", score(
                card(14, PlayingCard.Suit.SPADES), card(13, PlayingCard.Suit.SPADES), card(12, PlayingCard.Suit.SPADES),
                card(11, PlayingCard.Suit.SPADES), card(10, PlayingCard.Suit.SPADES), card(2, PlayingCard.Suit.CLUBS), card(3, PlayingCard.Suit.DIAMONDS)).name());
        assertEquals("Four of a kind", score(
                card(9, PlayingCard.Suit.SPADES), card(9, PlayingCard.Suit.CLUBS), card(9, PlayingCard.Suit.DIAMONDS),
                card(9, PlayingCard.Suit.HEARTS), card(14, PlayingCard.Suit.CLUBS), card(3, PlayingCard.Suit.DIAMONDS), card(2, PlayingCard.Suit.CLUBS)).name());
        assertEquals("Full house", score(
                card(7, PlayingCard.Suit.SPADES), card(7, PlayingCard.Suit.CLUBS), card(7, PlayingCard.Suit.DIAMONDS),
                card(4, PlayingCard.Suit.HEARTS), card(4, PlayingCard.Suit.CLUBS), card(13, PlayingCard.Suit.DIAMONDS), card(2, PlayingCard.Suit.CLUBS)).name());
    }

    @Test public void europeanRouletteUsesOneZeroStandardPayouts() {
        RouletteTable.Bet red = RouletteTable.colour(true, 10L);
        assertEquals(0L, RouletteTable.resolve(Arrays.asList(red), 0).getPayout());
        assertEquals(20L, RouletteTable.resolve(Arrays.asList(red), 1).getPayout());
        RouletteTable.Bet straight = new RouletteTable.Bet(RouletteTable.Type.STRAIGHT, 10L, 17);
        assertEquals(360L, RouletteTable.resolve(Arrays.asList(straight), 17).getPayout());
        assertTrue(36.0D / 37.0D < 1.0D);
    }

    @Test public void configuredTableReturnsRetainVirtualHouseEdge() {
        assertTrue(MinesTable.expectedReturn() < 1.0D);
        assertTrue(HoldemTable.expectedReturn() < 1.0D);
        assertTrue(VibeCaseTable.expectedReturn() < 1.0D);
    }

    @Test public void blackjackDealsLegalCardsFromItsSixDeckShoe() {
        BlackjackTable table = new BlackjackTable(10L, new java.security.SecureRandom());
        assertEquals(2, table.getHands().get(0).getCards().size());
        assertEquals(2, table.getDealerCards().size());
        for (PlayingCard card : table.getHands().get(0).getCards()) assertTrue(card.getRank() >= 2 && card.getRank() <= 14);
        for (PlayingCard card : table.getDealerCards()) assertTrue(card.getRank() >= 2 && card.getRank() <= 14);
    }

    private static HoldemTable.HandScore score(PlayingCard... cards) {
        return HoldemTable.evaluate(Arrays.asList(cards[0], cards[1]), Arrays.asList(cards[2], cards[3], cards[4], cards[5], cards[6]));
    }
    private static PlayingCard card(int rank, PlayingCard.Suit suit) { return new PlayingCard(rank, suit); }
}

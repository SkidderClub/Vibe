package dev.vibe.game.meme;

import org.junit.Test;
import static org.junit.Assert.*;

/** Pure board tests: no Minecraft client or network connection is required. */
public class MemeGameStateTest {
    @Test public void chessUnderstandsSanCastlingAndEnPassant() {
        ChessGameState enPassant = new ChessGameState();
        assertTrue(enPassant.move("e4"));
        assertTrue(enPassant.move("a6"));
        assertTrue(enPassant.move("e5"));
        assertTrue(enPassant.move("d5"));
        assertTrue("The immediate en-passant capture must be legal", enPassant.move("exd6"));
        assertEquals('P', enPassant.get(2, 3)); // d6
        assertEquals(' ', enPassant.get(3, 3)); // captured pawn on d5

        ChessGameState castle = new ChessGameState();
        for (String move : new String[] {"e4", "e5", "Nf3", "Nc6", "Bc4", "Nf6"}) assertTrue(move, castle.move(move));
        assertTrue("O-O", castle.move("O-O"));
        assertEquals('K', castle.get(7, 6));
        assertEquals('R', castle.get(7, 5));
    }

    @Test public void ticTacToeRejectsOccupiedCellsAndFindsAWinner() {
        TicTacToeState game = new TicTacToeState();
        assertTrue(game.move("a3")); assertTrue(game.move("a2")); assertFalse(game.move("a3"));
        assertTrue(game.move("b3")); assertTrue(game.move("b2")); assertTrue(game.move("c3"));
        assertTrue(game.isFinished()); assertEquals("Won by X", game.getStatus());
    }

    @Test public void connectFourDropsToTheLowestOpenRowAndWins() {
        ConnectFourState game = new ConnectFourState();
        for (String move : new String[] {"1", "2", "1", "2", "1", "2", "1"}) assertTrue(move, game.move(move));
        assertEquals('R', game.get(2, 0)); assertTrue(game.isFinished()); assertEquals("Won by Red", game.getStatus());
    }
}

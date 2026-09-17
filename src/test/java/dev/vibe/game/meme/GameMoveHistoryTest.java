package dev.vibe.game.meme;

import org.junit.Test;
import static org.junit.Assert.*;

public class GameMoveHistoryTest {
    @Test public void lostMovesCanBeReplayedInAllThreeGames() {
        MemeGameState[] games = {new ChessGameState(), new TicTacToeState(), new ConnectFourState()};
        String[][] moves = {{"e4", "e5", "Nf3"}, {"a3", "b2", "c1"}, {"1", "2", "1"}};
        for (int i=0; i<games.length; i++) {
            MemeGameState game = games[i];
            GameMoveHistory history = new GameMoveHistory(game);
            assertFalse(history.restoreRequestedTurn(0));
            for (String move : moves[i]) assertTrue(history.move(move));
            assertFalse("Cannot undo the other player's move", history.restoreRequestedTurn(1));
            assertTrue(history.restoreRequestedTurn(0));
            assertEquals(0, game.getTurn());
            assertFalse("Repeated reminders must not undo another move", history.restoreRequestedTurn(0));
            assertTrue(history.move(moves[i][2]));
            assertEquals(1, game.getTurn());
            history.reset();
            assertFalse(history.restoreRequestedTurn(0));
        }
    }

    @Test public void rollbackRestoresChessSpecialMoveRights() {
        ChessGameState game = new ChessGameState();
        GameMoveHistory history = new GameMoveHistory(game);
        for (String move : new String[]{"e4", "a6", "e5", "d5", "exd6"}) assertTrue(history.move(move));
        assertTrue(history.restoreRequestedTurn(0));
        assertEquals('p', game.get(3,3));
        assertTrue("En passant remains legal", history.move("exd6"));
        history.reset();
        for (String move : new String[]{"e4", "e5", "Nf3", "Nc6", "Bc4", "Nf6", "O-O"}) assertTrue(history.move(move));
        assertTrue(history.restoreRequestedTurn(0));
        assertEquals('K', game.get(7,4));
        assertEquals('R', game.get(7,7));
        assertTrue("Castling remains legal", history.move("O-O"));
    }

    @Test public void lostWinningMoveCanBeRestoredAndInvalidMovesAreNotRecorded() {
        TicTacToeState game = new TicTacToeState();
        GameMoveHistory history = new GameMoveHistory(game);
        for (String move : new String[]{"a3", "a2", "b3", "b2", "c3"}) assertTrue(history.move(move));
        assertTrue(game.isFinished());
        assertFalse(history.move("a1"));
        assertTrue(history.restoreRequestedTurn(0));
        assertFalse(game.isFinished());
        assertEquals(' ', game.get(0,2));
        assertTrue(history.move("c3"));
        assertTrue(game.isFinished());
    }
}

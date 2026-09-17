package dev.vibe.game.meme;

import java.util.ArrayList;
import java.util.List;

/** Replays accepted moves to restore all board rules, including castling and en passant. */
public final class GameMoveHistory {
    private final MemeGameState game;
    private final List<String> moves = new ArrayList<String>();
    private int lastSide = -1;

    public GameMoveHistory(MemeGameState game) { this.game = game; }

    public boolean move(String move) {
        int side = game.getTurn();
        if (!game.move(move)) return false;
        moves.add(move);
        lastSide = side;
        return true;
    }

    public void reset() { game.reset(); moves.clear(); lastSide = -1; }

    /** A reminder can undo only our latest move, never the opponent's move. */
    public boolean restoreRequestedTurn(int localSide) {
        if (moves.isEmpty() || lastSide != localSide) return false;
        moves.remove(moves.size() - 1);
        game.reset();
        lastSide = -1;
        for (String move : moves) {
            lastSide = game.getTurn();
            if (!game.move(move)) throw new IllegalStateException("Accepted move failed to replay");
        }
        return true;
    }
}

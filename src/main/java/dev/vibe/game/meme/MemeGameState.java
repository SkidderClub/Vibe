package dev.vibe.game.meme;

import java.util.List;
import java.util.Random;

/** A deterministic, client-only board used by the chat game modules. */
public interface MemeGameState {
    void reset();
    int getTurn();
    boolean isFinished();
    String getStatus();
    List<String> legalMoves();
    boolean move(String wireMove);
    String chooseRobotMove(int strength, Random random);
}

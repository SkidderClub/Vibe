package dev.vibe.module.impl;

import dev.vibe.game.meme.GameType;
import dev.vibe.game.meme.TicTacToeState;

public final class TicTacToeModule extends MemeGameModule {
    public TicTacToeModule() { super("Tic Tac Toe", "Challenge a player or robot through Minecraft chat", GameType.TIC_TAC_TOE, new TicTacToeState()); }
    public TicTacToeState ticTacToe() { return (TicTacToeState) getGame(); }
}

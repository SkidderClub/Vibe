package dev.vibe.module.impl;

import dev.vibe.game.meme.ChessGameState;
import dev.vibe.game.meme.GameType;

public final class ChessModule extends MemeGameModule {
    public ChessModule() { super("Chess", "Play a complete chess match through Minecraft chat", GameType.CHESS, new ChessGameState()); }
    public ChessGameState chess() { return (ChessGameState) getGame(); }
}

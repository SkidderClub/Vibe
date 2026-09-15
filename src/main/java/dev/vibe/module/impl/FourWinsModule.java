package dev.vibe.module.impl;

import dev.vibe.game.meme.ConnectFourState;
import dev.vibe.game.meme.GameType;

public final class FourWinsModule extends MemeGameModule {
    public FourWinsModule() { super("4 Wins", "Challenge a player or robot in a four-in-a-row match", GameType.CONNECT_FOUR, new ConnectFourState()); }
    public ConnectFourState fourWins() { return (ConnectFourState) getGame(); }
}

package dev.vibe.game.meme;

/** Wire names remain short and stable because they are sent through chat. */
public enum GameType {
    CHESS("chess", "Chess"),
    TIC_TAC_TOE("tictactoe", "Tic Tac Toe"),
    CONNECT_FOUR("4wins", "4 Wins");

    private final String wireName;
    private final String displayName;

    GameType(String wireName, String displayName) {
        this.wireName = wireName;
        this.displayName = displayName;
    }

    public String getWireName() { return wireName; }
    public String getDisplayName() { return displayName; }
}

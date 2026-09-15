package dev.vibe.game.meme;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class ConnectFourState implements MemeGameState {
    private final char[][] board = new char[6][7];
    private int turn;
    private String status;
    private int lastColumn = -1, lastRow = -1;

    public ConnectFourState() { reset(); }
    @Override public void reset() {
        for (int row = 0; row < 6; row++) for (int column = 0; column < 7; column++) board[row][column] = ' ';
        turn = 0; status = "Red to move"; lastColumn = lastRow = -1;
    }
    @Override public int getTurn() { return turn; }
    @Override public boolean isFinished() { return status.startsWith("Won") || "Draw".equals(status); }
    @Override public String getStatus() { return status; }
    public char get(int row, int column) { return board[row][column]; }
    public int getLastColumn() { return lastColumn; }
    public int getLastRow() { return lastRow; }
    @Override public List<String> legalMoves() {
        List<String> moves = new ArrayList<String>();
        if (!isFinished()) for (int column = 0; column < 7; column++) if (board[0][column] == ' ') moves.add(String.valueOf(column + 1));
        return moves;
    }
    @Override public boolean move(String wireMove) {
        if (wireMove == null || !wireMove.trim().matches("[1-7]") || isFinished()) return false;
        int column = wireMove.trim().charAt(0) - '1';
        for (int row = 5; row >= 0; row--) if (board[row][column] == ' ') {
            char disc = turn == 0 ? 'R' : 'Y'; board[row][column] = disc; lastColumn = column; lastRow = row;
            if (wins(row, column, disc)) status = "Won by " + (disc == 'R' ? "Red" : "Yellow");
            else if (legalMovesInternal().isEmpty()) status = "Draw";
            else { turn = 1 - turn; status = turn == 0 ? "Red to move" : "Yellow to move"; }
            return true;
        }
        return false;
    }
    @Override public String chooseRobotMove(int strength, Random random) {
        List<String> moves = legalMoves(); if (moves.isEmpty()) return null;
        // Win immediately, then block an immediate loss. Higher strengths
        // also favour the centre and inspect the opponent's next reply.
        for (String move : moves) { ConnectFourState copy = copy(); copy.move(move); if (copy.isFinished()) return move; }
        for (String move : moves) { ConnectFourState copy = copy(); copy.turn = 1 - copy.turn; copy.move(move); if (copy.isFinished()) return move; }
        if (strength <= 3) return moves.get(random.nextInt(moves.size()));
        int best = Integer.MIN_VALUE; String chosen = moves.get(0);
        for (String move : moves) {
            ConnectFourState copy = copy(); copy.move(move); int score = 4 - Math.abs(3 - (move.charAt(0) - '1'));
            if (strength >= 7 && !copy.isFinished()) for (String reply : copy.legalMoves()) { ConnectFourState child = copy.copy(); child.move(reply); if (child.isFinished()) score -= 30; }
            if (score > best || score == best && random.nextBoolean()) { best = score; chosen = move; }
        }
        return chosen;
    }
    private boolean wins(int row, int col, char disc) { return count(row, col, 1, 0, disc) + count(row, col, -1, 0, disc) >= 3
            || count(row, col, 0, 1, disc) + count(row, col, 0, -1, disc) >= 3
            || count(row, col, 1, 1, disc) + count(row, col, -1, -1, disc) >= 3
            || count(row, col, 1, -1, disc) + count(row, col, -1, 1, disc) >= 3; }
    private int count(int row, int col, int dr, int dc, char disc) { int count = 0; for (row += dr, col += dc; row >= 0 && row < 6 && col >= 0 && col < 7 && board[row][col] == disc; row += dr, col += dc) count++; return count; }
    private List<String> legalMovesInternal() { return legalMoves(); }
    private ConnectFourState copy() { ConnectFourState result = new ConnectFourState(); for (int row = 0; row < 6; row++) System.arraycopy(board[row], 0, result.board[row], 0, 7); result.turn = turn; result.status = status; result.lastColumn = lastColumn; result.lastRow = lastRow; return result; }
}

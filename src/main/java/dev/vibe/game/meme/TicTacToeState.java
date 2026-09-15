package dev.vibe.game.meme;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class TicTacToeState implements MemeGameState {
    private final char[][] board = new char[3][3];
    private int turn;
    private String status;

    public TicTacToeState() { reset(); }

    @Override public void reset() {
        for (int row = 0; row < 3; row++) for (int col = 0; col < 3; col++) board[row][col] = ' ';
        turn = 0; status = "First player to move";
    }
    @Override public int getTurn() { return turn; }
    @Override public boolean isFinished() { return status.startsWith("Won") || "Draw".equals(status); }
    @Override public String getStatus() { return status; }
    public char get(int row, int column) { return board[row][column]; }

    @Override public List<String> legalMoves() {
        List<String> result = new ArrayList<String>();
        if (isFinished()) return result;
        for (int row = 0; row < 3; row++) for (int col = 0; col < 3; col++) if (board[row][col] == ' ')
            result.add("" + (char) ('a' + col) + (char) ('1' + (2 - row)));
        return result;
    }

    @Override public boolean move(String wireMove) {
        if (wireMove == null) return false;
        String move = wireMove.trim().toLowerCase(java.util.Locale.ROOT);
        if (move.matches("[1-9]")) {
            int value = Integer.parseInt(move) - 1; move = "" + (char) ('a' + value % 3) + (char) ('3' - value / 3);
        }
        if (!move.matches("[a-c][1-3]") || isFinished()) return false;
        int col = move.charAt(0) - 'a', row = 2 - (move.charAt(1) - '1');
        if (board[row][col] != ' ') return false;
        board[row][col] = turn == 0 ? 'X' : 'O';
        if (wins(board[row][col])) status = "Won by " + board[row][col];
        else if (legalMovesInternal().isEmpty()) status = "Draw";
        else { turn = 1 - turn; status = (turn == 0 ? "X" : "O") + " to move"; }
        return true;
    }

    @Override public String chooseRobotMove(int strength, Random random) {
        List<String> moves = legalMoves();
        if (moves.isEmpty()) return null;
        if (strength <= 3) return moves.get(random.nextInt(moves.size()));
        int best = Integer.MIN_VALUE; String selected = moves.get(0);
        for (String move : moves) {
            TicTacToeState copy = copy(); copy.move(move);
            int score = minimax(copy, false, turn, Integer.MIN_VALUE, Integer.MAX_VALUE);
            if (score > best || score == best && random.nextBoolean()) { best = score; selected = move; }
        }
        return selected;
    }

    private int minimax(TicTacToeState state, boolean maximize, int robot, int alpha, int beta) {
        if (state.isFinished()) {
            if ("Draw".equals(state.status)) return 0;
            char winner = state.status.charAt(state.status.length() - 1);
            return winner == (robot == 0 ? 'X' : 'O') ? 10 : -10;
        }
        int value = maximize ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        for (String move : state.legalMoves()) {
            TicTacToeState child = state.copy(); child.move(move);
            int score = minimax(child, child.turn == robot, robot, alpha, beta);
            if (maximize) { value = Math.max(value, score); alpha = Math.max(alpha, value); }
            else { value = Math.min(value, score); beta = Math.min(beta, value); }
            if (beta <= alpha) break;
        }
        return value;
    }

    private boolean wins(char mark) {
        for (int row = 0; row < 3; row++) if (board[row][0] == mark && board[row][1] == mark && board[row][2] == mark) return true;
        for (int col = 0; col < 3; col++) if (board[0][col] == mark && board[1][col] == mark && board[2][col] == mark) return true;
        return board[0][0] == mark && board[1][1] == mark && board[2][2] == mark
                || board[0][2] == mark && board[1][1] == mark && board[2][0] == mark;
    }
    private List<String> legalMovesInternal() { return legalMoves(); }
    private TicTacToeState copy() {
        TicTacToeState result = new TicTacToeState();
        for (int row = 0; row < 3; row++) System.arraycopy(board[row], 0, result.board[row], 0, 3);
        result.turn = turn; result.status = status; return result;
    }
}

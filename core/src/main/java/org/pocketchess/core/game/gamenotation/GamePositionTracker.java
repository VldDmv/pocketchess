package org.pocketchess.core.game.gamenotation;

import org.pocketchess.core.game.utils.FenUtils;
import org.pocketchess.core.general.Board;

import java.util.HashMap;
import java.util.Map;

/**
 * Tracks positions to determine the threefold repetition and 50-move rule.
 */
public class GamePositionTracker {
    /**
     * Position history: FEN string -> number of repetitions
     * Used to determine whether a string is repeated three times
     */
    private Map<String, Integer> positionHistory = new HashMap<>();

    /**
     * Half-move counter without captures or pawn movements
     * Used for the 50-move rule (100 half-moves = 50 moves)
     */
    private int halfMovesSincePawnOrCapture = 0;

    public GamePositionTracker() {
    }

    /**
     * Copy constructor.
     * Necessary for creating copies of the game
     */
    public GamePositionTracker(GamePositionTracker other) {
        this.positionHistory = new HashMap<>(other.positionHistory);
        this.halfMovesSincePawnOrCapture = other.halfMovesSincePawnOrCapture;
    }

    /**
     * Resets the entire position history
     */
    public void reset() {
        positionHistory.clear();
        halfMovesSincePawnOrCapture = 0;
    }

    /**
     * Saves the current position to the history
     * Converts the board to a FEN string and increments the iteration counter
     * Called after each move
     */
    public void recordPosition(Board board, boolean isWhiteTurn) {
        String fen = FenUtils.generateFEN(board, isWhiteTurn);
        positionHistory.put(fen, positionHistory.getOrDefault(fen, 0) + 1);
    }

    /**
     * Records the starting position of the game
     */
    public void recordInitialPosition(Board board) {
        String fen = FenUtils.generateFEN(board, true);
        positionHistory.put(fen, 1);
    }

    /**
     * Deletes the last recorded position.
     * Used when undoing a temporary step.
     * Decrements the repeat counter if the position was recorded.
     */
    public void removeLastPosition(Board board, boolean isWhiteTurn) {
        String fen = FenUtils.generateFEN(board, isWhiteTurn);
        Integer count = positionHistory.get(fen);

        if (count != null) {
            if (count <= 1) {
                positionHistory.remove(fen);
            } else {

                positionHistory.put(fen, count - 1);
            }
        }
    }

    /**
     * Returns the number of repetitions of the current position.
     */
    public int getPositionCount(Board board, boolean isWhiteTurn) {
        String fen = FenUtils.generateFEN(board, isWhiteTurn);
        return positionHistory.getOrDefault(fen, 0);
    }


    public boolean isThreefoldRepetition(Board board, boolean isWhiteTurn) {
        return getPositionCount(board, isWhiteTurn) >= 3;
    }


    public int getHalfMoves() {
        return halfMovesSincePawnOrCapture;
    }

    public void setHalfMoves(int moves) {
        this.halfMovesSincePawnOrCapture = moves;
    }

    public boolean isFiftyMoveRule() {
        return halfMovesSincePawnOrCapture >= 100;
    }
}
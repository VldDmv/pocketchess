package org.pocketchess.core.game.gamenotation;

import org.pocketchess.core.game.moveanalyze.Move;
import org.pocketchess.core.game.status.GameMoveExecutor;
import org.pocketchess.core.general.Board;

import java.util.ArrayList;
import java.util.List;

/**
 * Manager of move history and game navigation.
 */
public class GameHistoryManager {
    private final Board            board;
    private final GameMoveExecutor moveExecutor;
    private final GamePositionTracker positionTracker;

    private final List<Move> fullGameHistory = new ArrayList<>();
    private int currentMoveIndex = -1;

    public GameHistoryManager(Board board, GameMoveExecutor moveExecutor,
                              GamePositionTracker positionTracker) {
        this.board            = board;
        this.moveExecutor     = moveExecutor;
        this.positionTracker  = positionTracker;
    }

    public void addMove(Move move) {
        fullGameHistory.add(move);
        currentMoveIndex = fullGameHistory.size() - 1;
    }

    public void removeLastMove() {
        if (!fullGameHistory.isEmpty()) {
            fullGameHistory.remove(fullGameHistory.size() - 1);
            currentMoveIndex = fullGameHistory.size() - 1;
        }
    }

    public void clearHistory() {
        fullGameHistory.clear();
        currentMoveIndex = -1;
    }

    public Move getLastMove() {
        if (currentMoveIndex >= 0 && currentMoveIndex < fullGameHistory.size()) {
            return fullGameHistory.get(currentMoveIndex);
        }
        return null;
    }

    public List<Move> getMoveHistory()   { return fullGameHistory; }
    public int        getCurrentMoveIndex() { return currentMoveIndex; }
    public boolean    isLive()           { return currentMoveIndex == fullGameHistory.size() - 1; }

    /**
     * Replays the game up to moveIndex.
     */
    public void goToMove(int moveIndex) {
        if (moveIndex < -1 || moveIndex >= fullGameHistory.size()) return;

        // ── Restore to the game's own starting position ───────────────────────
        board.resetToInitial();
        moveExecutor.clearCapturedPieces();
        positionTracker.reset();
        positionTracker.recordInitialPosition(board);

        boolean tempTurn = true;
        for (int i = 0; i <= moveIndex; i++) {
            Move m = fullGameHistory.get(i);
            moveExecutor.replayMove(m);
            tempTurn = !tempTurn;
            positionTracker.recordPosition(board, tempTurn);
        }

        currentMoveIndex = moveIndex;

        if (currentMoveIndex >= 0) {
            positionTracker.setHalfMoves(fullGameHistory.get(currentMoveIndex).halfMovesAfterMove);
        } else {
            positionTracker.setHalfMoves(0);
        }
    }
}
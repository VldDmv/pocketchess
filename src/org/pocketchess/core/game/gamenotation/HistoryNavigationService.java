package org.pocketchess.core.game.gamenotation;


import org.pocketchess.core.game.model.GameMode;
import org.pocketchess.core.game.status.GameStateManager;
import org.pocketchess.core.game.status.GameStatusManager;
import org.pocketchess.core.game.status.GameTimeManager;
import org.pocketchess.core.general.Board;

/**
 * A service for navigating through the history of moves
 */
public class HistoryNavigationService {
    private final GameHistoryManager historyManager;
    private final GameStateManager stateManager;
    private final GameTimeManager timeManager;
    private final GameStatusManager statusManager;
    private final Board board;

    public HistoryNavigationService(GameHistoryManager historyManager,
                                    GameStateManager stateManager,
                                    GameTimeManager timeManager,
                                    GameStatusManager statusManager,
                                    Board board) {
        this.historyManager = historyManager;
        this.stateManager = stateManager;
        this.timeManager = timeManager;
        this.statusManager = statusManager;
        this.board = board;
    }

    /**
     * Undo your last move
     */
    public void undoMove() {
        if (!historyManager.isLive() || historyManager.getMoveHistory().isEmpty()) {
            return;
        }

        int movesToUndo = calculateMovesToUndo();

        if (historyManager.getMoveHistory().size() < movesToUndo) {
            return;
        }

        for (int i = 0; i < movesToUndo; i++) {
            historyManager.removeLastMove();
        }

        goToMove(historyManager.getCurrentMoveIndex());
    }

    /**
     * Go to specified move in the history
     */
    public void goToMove(int moveIndex) {
        if (moveIndex < -1 || moveIndex >= historyManager.getMoveHistory().size()) {
            return;
        }

        historyManager.goToMove(moveIndex);

        updateTurnState(moveIndex);
        updateGameStatus();
        updateTimer();
    }


    private int calculateMovesToUndo() {
        int movesToUndo = 1;

        if (stateManager.getGameMode() == GameMode.PVE &&
                stateManager.isHumansTurn() &&
                historyManager.getMoveHistory().size() >= 2) {
            movesToUndo = 2;
        }

        return movesToUndo;
    }

    private void updateTurnState(int moveIndex) {
        stateManager.setWhiteTurn(true);
        for (int i = 0; i <= moveIndex; i++) {
            stateManager.toggleTurn();
        }
    }

    private void updateGameStatus() {
        stateManager.setStatus(
                statusManager.calculateStatus(board, stateManager.isWhiteTurn())
        );
    }

    private void updateTimer() {
        if (!historyManager.isLive() || stateManager.isGameOver()) {
            timeManager.stopTimer();
        } else {
            timeManager.startTimer();
        }
    }
}
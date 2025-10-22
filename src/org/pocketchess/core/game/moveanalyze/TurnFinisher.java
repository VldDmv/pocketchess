package org.pocketchess.core.game.moveanalyze;

import org.pocketchess.core.game.gamenotation.GamePositionTracker;
import org.pocketchess.core.game.status.GameStateManager;
import org.pocketchess.core.game.status.GameStatusManager;
import org.pocketchess.core.game.status.GameTimeManager;
import org.pocketchess.core.general.Board;

/**
 * Ends a player's turn - updates all associated states.
 * ACTIONS:
 * 1. Add a time increment (if used)
 * 2. Record time per turn (for history)
 * 3. Change turn order (white → black or vice versa)
 * 4. Record position (for tracking repetitions)
 * 5. Update game status (check for checkmate, stalemate, draw)
 * 6. Stop the timer if the game is over
 */
public class TurnFinisher {
    private final GameStateManager stateManager;
    private final GameTimeManager timeManager;
    private final GamePositionTracker positionTracker;
    private final GameStatusManager statusManager;
    private final Board board;

    public TurnFinisher(GameStateManager stateManager, GameTimeManager timeManager,
                        GamePositionTracker positionTracker, GameStatusManager statusManager,
                        Board board) {
        this.stateManager = stateManager;
        this.timeManager = timeManager;
        this.positionTracker = positionTracker;
        this.statusManager = statusManager;
        this.board = board;
    }

    /**
     * Ends a turn: updates all game states.
     */
    public void finishTurn(Move move) {

        timeManager.addIncrement(stateManager.isWhiteTurn());

        move.whiteTimeMillisAfterMove = timeManager.getWhiteTimeMillis();
        move.blackTimeMillisAfterMove = timeManager.getBlackTimeMillis();

        stateManager.toggleTurn();


        positionTracker.recordPosition(board, stateManager.isWhiteTurn());


        updateGameStatus();

        move.statusAfterMove = stateManager.getStatus();
    }

    /**
     * Updates the game status via StatusManager.
     * Checks:
     * - Checkmate
     * - Anykind of draw
     * - Check
     * If the game is over, stops the timer.
     */
    private void updateGameStatus() {
        stateManager.setStatus(
                statusManager.calculateStatus(board, stateManager.isWhiteTurn())
        );

        if (stateManager.isGameOver()) {
            timeManager.stopTimer();
        }
    }
}
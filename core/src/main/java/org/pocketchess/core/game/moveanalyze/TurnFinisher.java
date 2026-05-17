package org.pocketchess.core.game.moveanalyze;

import org.pocketchess.core.game.gamenotation.GamePositionTracker;
import org.pocketchess.core.game.status.GameStateManager;
import org.pocketchess.core.game.status.GameStatusManager;
import org.pocketchess.core.game.status.GameTimeManager;
import org.pocketchess.core.general.Board;

/**
 * Ends a player's turn – updates all associated states.
 *
 * ACTIONS:
 * 1. Add time increment (if used)
 * 2. Record time per turn (for history)
 * 3. Toggle turn order (white ↔ black)
 * 4. Record position (for repetition tracking)
 * 5. Run postMoveCallback – used to apply lava effects BEFORE status check
 * 6. Update game status (checkmate, stalemate, draw …)
 * 7. Stop timer if game is over
 */
public class TurnFinisher {
    private final GameStateManager stateManager;
    private final GameTimeManager timeManager;
    private final GamePositionTracker positionTracker;
    private final GameStatusManager statusManager;
    private final Board board;

    /**
     * Optional callback executed after the turn is toggled but BEFORE status is
     * recalculated.  Used by Game to apply lava effects so that piece removal is
     * taken into account when deciding checkmate / stalemate.
     */
    private Runnable postMoveCallback;

    public TurnFinisher(GameStateManager stateManager, GameTimeManager timeManager,
                        GamePositionTracker positionTracker, GameStatusManager statusManager,
                        Board board) {
        this.stateManager    = stateManager;
        this.timeManager     = timeManager;
        this.positionTracker = positionTracker;
        this.statusManager   = statusManager;
        this.board           = board;
    }

    /** Set by Game to hook in lava-effect processing. */
    public void setPostMoveCallback(Runnable callback) {
        this.postMoveCallback = callback;
    }

    /** Ends a turn: updates all game states. */
    public void finishTurn(Move move) {
        // 1 & 2: time
        timeManager.addIncrement(stateManager.isWhiteTurn());
        move.whiteTimeMillisAfterMove = timeManager.getWhiteTimeMillis();
        move.blackTimeMillisAfterMove = timeManager.getBlackTimeMillis();

        // 3: flip turn
        stateManager.toggleTurn();

        // 4: record position for repetition / 50-move rule
        positionTracker.recordPosition(board, stateManager.isWhiteTurn());

        // 5: lava effect (may remove pieces, may end game)
        if (postMoveCallback != null) {
            postMoveCallback.run();
        }

        // 6: recalculate status (incorporates any lava removals)
        updateGameStatus();

        // 7: save status into the move record
        move.statusAfterMove = stateManager.getStatus();
    }

    private void updateGameStatus() {
        if (stateManager.isGameOver()) return;

        stateManager.setStatus(
                statusManager.calculateStatus(board, stateManager.isWhiteTurn())
        );

        if (stateManager.isGameOver()) {
            timeManager.stopTimer();
        }
    }
}
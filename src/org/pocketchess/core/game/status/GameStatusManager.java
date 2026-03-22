package org.pocketchess.core.game.status;

import org.pocketchess.core.game.model.GameStatus;
import org.pocketchess.core.game.gamenotation.GamePositionTracker;
import org.pocketchess.core.game.moveanalyze.ChessRules;
import org.pocketchess.core.general.Board;

/**
 * Determines the current status of the game.
 */
public class GameStatusManager {
    private final ChessRules ruleEngine;
    private final GamePositionTracker positionTracker;

    public GameStatusManager(ChessRules ruleEngine, GamePositionTracker positionTracker) {
        this.ruleEngine = ruleEngine;
        this.positionTracker = positionTracker;
    }

    public GameStatus calculateStatus(Board board, boolean isWhiteTurn) {

        if (ruleEngine.isInsufficientMaterial(board)) {
            return GameStatus.DRAW_INSUFFICIENT_MATERIAL;
        }

        if (positionTracker.isThreefoldRepetition(board, isWhiteTurn)) {
            return GameStatus.DRAW_THREEFOLD_REPETITION;
        }

        if (positionTracker.isFiftyMoveRule()) {
            return GameStatus.DRAW_50_MOVES;
        }

        if (!ruleEngine.hasLegalMoves(board, isWhiteTurn)) {
            if (ruleEngine.isKingInCheck(board, isWhiteTurn)) {

                return isWhiteTurn ? GameStatus.BLACK_WIN : GameStatus.WHITE_WIN;
            } else {

                return GameStatus.STALEMATE;
            }
        }

        if (ruleEngine.isKingInCheck(board, isWhiteTurn)) {
            return GameStatus.CHECK;
        }

        return GameStatus.ACTIVE;
    }
}

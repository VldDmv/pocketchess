package org.pocketchess.core.game.moveanalyze;

import org.pocketchess.core.game.model.GameStatus;
import org.pocketchess.core.game.gamenotation.GameHistoryManager;
import org.pocketchess.core.game.gamenotation.GamePositionTracker;
import org.pocketchess.core.game.status.GameMoveExecutor;
import org.pocketchess.core.game.status.GameStateManager;
import org.pocketchess.core.game.status.GameTimeManager;
import org.pocketchess.core.general.Board;
import org.pocketchess.core.pieces.King;
import org.pocketchess.core.pieces.Pawn;
import org.pocketchess.core.pieces.Piece;
import org.pocketchess.core.pieces.Spot;
import org.pocketchess.core.pieces.Rook;
import java.util.List;

/**
 * Service for processing player moves.
 * MAIN RESPONSIBILITIES:
 * - Validating player moves
 * - Executing normal moves
 * - Processing pawn promotions
 * - Managing "live" mode (current position vs. viewing history)
 */
public class PlayerMoveService {
    private final Board board;
    private final ChessRules ruleEngine;
    private final GameStateManager stateManager;
    private final GameMoveExecutor moveExecutor;
    private final GameHistoryManager historyManager;
    private final GamePositionTracker positionTracker;
    private final GameTimeManager timeManager;
    private final TurnFinisher turnFinisher;

    public PlayerMoveService(Board board, ChessRules ruleEngine, GameStateManager stateManager,
                             GameMoveExecutor moveExecutor, GameHistoryManager historyManager,
                             GamePositionTracker positionTracker, GameTimeManager timeManager,
                             TurnFinisher turnFinisher) {
        this.board = board;
        this.ruleEngine = ruleEngine;
        this.stateManager = stateManager;
        this.moveExecutor = moveExecutor;
        this.historyManager = historyManager;
        this.positionTracker = positionTracker;
        this.timeManager = timeManager;
        this.turnFinisher = turnFinisher;
    }

    /**
     * Performs the player's move.
     * Full algorithm:
     * 1. Check conditions (the game is not over, we are in "live" mode)
     * 2. Validate the move (correct piece, legal move)
     * 3. Perform the move on the board
     * 4. Add the move to the history
     * 5. Check for pawn promotion
     * 6. End the move (update status, time, queue)
     */
    public boolean executeMove(int startX, int startY, int endX, int endY) {

        if (stateManager.isGameOver() || !historyManager.isLive()) return false;

        stateManager.setDrawOffered(false);
        if (historyManager.getMoveHistory().isEmpty()) timeManager.startTimer();

        Spot startSpot = board.getBox(startX, startY);
        Spot endSpot = board.getBox(endX, endY);
        Piece sourcePiece = startSpot.getPiece();

        if (!isValidPiece(sourcePiece)) return false;

        boolean isCastling = false;
        if (sourcePiece instanceof King && !((King) sourcePiece).hasMoved()) {
            Piece targetPiece = endSpot.getPiece();

            // 1. The player set the king onto his own unmoved rook (UI for Chess960)
            if (targetPiece instanceof Rook && targetPiece.isWhite() == sourcePiece.isWhite()) {
                isCastling = true;
                int direction = (endY > startY) ? 1 : -1;
                endY = (direction > 0) ? 6 : 2;
                endSpot = board.getBox(endX, endY);
            }
            // 2. The player set the king on the g- or c-file
            // Only treat as castling if king moves MORE than 1 square.
            // This prevents a king on d1 moving one step to c1 from being
            // misidentified as queenside castling.
            else if ((endY == 6 || endY == 2) && Math.abs(endY - startY) > 1) {
                if (ruleEngine.isCastlingMoveLegal(board, startSpot, endSpot)) {
                    isCastling = true;
                }
            }
        }

        if (!isMoveLegal(startSpot, endSpot, isCastling)) return false;

        Move move = moveExecutor.createAndExecuteMove(
                startSpot, endSpot, isCastling, positionTracker.getHalfMoves()
        );

        historyManager.addMove(move);
        positionTracker.setHalfMoves(move.halfMovesAfterMove);


        if (isPromotionRequired(sourcePiece, endX)) {
            stateManager.setStatus(GameStatus.AWAITING_PROMOTION);
            stateManager.setPromotionSpot(endSpot);
            return true;
        }


        turnFinisher.finishTurn(move);
        return true;
    }

    /**
     * Performs a move with immediate promotion of a pawn
     */
    public void executeMoveWithPromotion(int startX, int startY, int endX, int endY,
                                         Piece promotionPiece) {


        Spot startSpot = board.getBox(startX, startY);
        Spot endSpot = board.getBox(endX, endY);
        Piece sourcePiece = startSpot.getPiece();

        boolean isCastling = (sourcePiece instanceof King) && Math.abs(endY - startY) == 2;
        Move move = moveExecutor.createAndExecuteMove(
                startSpot, endSpot, isCastling, positionTracker.getHalfMoves()
        );


        if (promotionPiece != null) {
            endSpot.setPiece(promotionPiece);
            move.promotedTo = promotionPiece;
        }

        historyManager.addMove(move);
        positionTracker.setHalfMoves(move.halfMovesAfterMove);
        turnFinisher.finishTurn(move);
    }

    /**
     * Promotes a pawn to the selected piece.
     * Called after the player has selected a piece to promote
     * to the UI (queen, rook, bishop, or knight)
     */
    public void promotePawn(Piece newPiece) {
        if (stateManager.getStatus() != GameStatus.AWAITING_PROMOTION ||
                stateManager.getPromotionSpot() == null) {
            return;
        }

        stateManager.getPromotionSpot().setPiece(newPiece);

        if (!historyManager.getMoveHistory().isEmpty()) {
            List<Move> history = historyManager.getMoveHistory();
            history.get(history.size() - 1).promotedTo = newPiece;
        }

        stateManager.setPromotionSpot(null);
        turnFinisher.finishTurn(historyManager.getLastMove());
    }

    // !!!!!!!!!!!!!!! PRIVATE !!!!!!!!!!!!!!!

    /**
     * Checks whether the piece belongs to the current player.
     */
    private boolean isValidPiece(Piece piece) {
        return piece != null && piece.isWhite() == stateManager.isWhiteTurn();
    }

    /**
     * Checks the legality of a move using the rules engine.
     */
    private boolean isMoveLegal(Spot startSpot, Spot endSpot, boolean isCastling) {
        if (isCastling) {
            return ruleEngine.isCastlingMoveLegal(board, startSpot, endSpot);
        }
        return ruleEngine.isMoveLegal(board, startSpot, endSpot);
    }

    /**
     * Checks whether a pawn promotion is required.
     */
    private boolean isPromotionRequired(Piece piece, int endX) {
        return piece instanceof Pawn && (endX == 0 || endX == 7);
    }
}
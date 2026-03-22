package org.pocketchess.core.game.moveanalyze;

import org.pocketchess.core.ai.search.AIPlayer;
import org.pocketchess.core.ai.difficulty.EvaluationParameters;
import org.pocketchess.core.game.model.GameStatus;
import org.pocketchess.core.game.status.GameStateManager;
import org.pocketchess.core.general.Game;
import org.pocketchess.core.pieces.Pawn;
import org.pocketchess.core.pieces.Piece;
import org.pocketchess.core.pieces.Queen;

import javax.swing.*;

/**
 * Service for processing AI moves.
 * Communicates results back via {@link AICallback} — no UI imports needed.
 */
public class AIMoveService {
    private final Game game;
    private final GameStateManager stateManager;
    private final PlayerMoveService playerMoveService;
    private AICallback callback;

    public AIMoveService(Game game, GameStateManager stateManager,
                         PlayerMoveService playerMoveService) {
        this.game = game;
        this.stateManager = stateManager;
        this.playerMoveService = playerMoveService;
    }

    public void setCallback(AICallback callback) {
        this.callback = callback;
    }

    /**
     * Starts searching and executing the AI move in a background thread.
     */
    public void makeAIMove() {
        if (stateManager.isGameOver()) return;

        EvaluationParameters params = new EvaluationParameters();
        final AIPlayer ai = new AIPlayer(params, stateManager.getAiDifficulty());

        SwingWorker<Move, Void> worker = new SwingWorker<>() {
            @Override
            protected Move doInBackground() {
                return ai.findBestMove(game);
            }

            @Override
            protected void done() {
                if (stateManager.isGameOver()) {
                    if (callback != null) callback.onAIMoveCompleted(false);
                    return;
                }
                try {
                    Move bestMove = get();
                    if (bestMove != null) {
                        executeAIMove(bestMove);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    if (callback != null) callback.onAIMoveCompleted(false);
                } catch (java.util.concurrent.ExecutionException e) {
                    if (callback != null) callback.onAIMoveCompleted(false);
                }
            }
        };
        worker.execute();
    }

    public void makeAIMoveWithDelay() {
        Timer aiTimer = new Timer(250, e -> makeAIMove());
        aiTimer.setRepeats(false);
        aiTimer.start();
    }

    private void executeAIMove(Move bestMove) {
        Piece movingPiece = bestMove.start.getPiece();
        boolean isPromotionMove = isPromotionMove(movingPiece, bestMove);

        if (isPromotionMove && bestMove.promotedTo == null) {
            bestMove.promotedTo = new Queen(movingPiece.isWhite());
        }

        boolean moveSuccessful = playerMoveService.executeMove(
                bestMove.start.getX(), bestMove.start.getY(),
                bestMove.end.getX(), bestMove.end.getY()
        );

        if (moveSuccessful && stateManager.getStatus() == GameStatus.AWAITING_PROMOTION) {
            Piece promotionPiece = bestMove.promotedTo != null
                    ? bestMove.promotedTo : new Queen(movingPiece.isWhite());
            playerMoveService.promotePawn(promotionPiece);
        }

        if (callback != null) {
            if (moveSuccessful) callback.playSoundForLastMove();
            callback.onAIMoveCompleted(moveSuccessful);
        }
    }

    private boolean isPromotionMove(Piece piece, Move move) {
        if (!(piece instanceof Pawn)) return false;
        return (piece.isWhite() && move.end.getX() == 0) ||
                (!piece.isWhite() && move.end.getX() == 7);
    }
}
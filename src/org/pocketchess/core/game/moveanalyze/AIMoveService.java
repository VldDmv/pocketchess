package org.pocketchess.core.game.moveanalyze;

import org.pocketchess.core.ai.AIPlayer;
import org.pocketchess.core.ai.difficulty.EvaluationParameters;
import org.pocketchess.core.game.GameStatus;
import org.pocketchess.core.game.status.GameStateManager;
import org.pocketchess.core.general.Game;
import org.pocketchess.core.pieces.Pawn;
import org.pocketchess.core.pieces.Piece;
import org.pocketchess.core.pieces.Queen;
import org.pocketchess.ui.gameframepack.GameFrame;

import javax.swing.*;

/**
 * Service for processing AI moves.
 * MAIN FUNCTIONS:
 * - Runs AI in a background thread (SwingWorker)
 * - Processes AI results
 * - Manages pawn promotions for AI
 * - Updates the UI after an AI move
 */
public class AIMoveService {
    private final Game game;
    private final GameStateManager stateManager;
    private final PlayerMoveService playerMoveService;
    private GameFrame gameFrame;

    public AIMoveService(Game game, GameStateManager stateManager,
                         PlayerMoveService playerMoveService) {
        this.game = game;
        this.stateManager = stateManager;
        this.playerMoveService = playerMoveService;
    }

    public void setGameFrame(GameFrame gameFrame) {
        this.gameFrame = gameFrame;
    }

    /**
     * Starts searching and executing the AI move.
     * - doInBackground(): search for the move (long-running operation)
     * - done(): execute the move on the UI thread
     */
    public void makeAIMove() {
        if (stateManager.isGameOver()) {
            return;
        }

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
                    gameFrame.updateUI();
                    return;
                }
                try {
                    Move bestMove = get();
                    if (bestMove != null) {
                        executeAIMove(bestMove);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
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

    /**
     * Executes the move detected by the AI on the board.
     * Handles two cases:
     * 1. A normal move - simply executes it
     * 2. A pawn promotion - the AI always chooses the queen
     */
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
            Piece promotionPiece = bestMove.promotedTo != null ?
                    bestMove.promotedTo : new Queen(movingPiece.isWhite());
            playerMoveService.promotePawn(promotionPiece);
        }

        updateUI(moveSuccessful);
    }


    private boolean isPromotionMove(Piece piece, Move move) {
        if (!(piece instanceof Pawn)) {
            return false;
        }
        return (piece.isWhite() && move.end.getX() == 0) ||
                (!piece.isWhite() && move.end.getX() == 7);
    }

    /**
     * Updates the UI and plays sound after the AI moves.
     */
    private void updateUI(boolean moveSuccessful) {
        if (gameFrame != null) {
            gameFrame.updateUI();
            if (moveSuccessful) {
                gameFrame.playSoundForLastMove();
            }
        }
    }
}
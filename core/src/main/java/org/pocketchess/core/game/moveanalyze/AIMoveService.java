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
        boolean whiteMoving = stateManager.isWhiteTurn();

        Move applied = tryApply(bestMove);
        if (applied == null) {
            // The engine's legality check occasionally disagrees with the move
            // generator (e.g. a Chess960 king step, or a check-detection edge
            // case). Rather than freeze on the bot's turn, play the first move
            // the executor actually accepts.
            for (Move alt : new org.pocketchess.core.ai.search.FastMoveGenerator()
                    .generateLegalMoves(game)) {
                applied = tryApply(alt);
                if (applied != null) break;
            }
        }
        boolean moveSuccessful = applied != null;

        if (moveSuccessful && stateManager.getStatus() == GameStatus.AWAITING_PROMOTION) {
            Piece promotionPiece = applied.promotedTo != null
                    ? applied.promotedTo : new Queen(whiteMoving);
            playerMoveService.promotePawn(promotionPiece);
        }

        if (callback != null) {
            if (moveSuccessful) callback.playSoundForLastMove();
            callback.onAIMoveCompleted(moveSuccessful);
        }
    }

    /**
     * Applies a move (Chess960 castles as king-takes-rook so any king file
     * works); returns the move if the executor accepted it, else {@code null}
     * with no change to the board.
     */
    private Move tryApply(Move m) {
        boolean ok;
        if (m.wasCastlingMove
                && game.getGameModeType() == org.pocketchess.core.gamemode.GameModeType.CHESS960
                && m.chess960RookFromCol >= 0) {
            ok = playerMoveService.executeMove(m.start.getX(), m.start.getY(),
                    m.start.getX(), m.chess960RookFromCol);
        } else {
            ok = playerMoveService.executeMove(m.start.getX(), m.start.getY(),
                    m.end.getX(), m.end.getY());
        }
        return ok ? m : null;
    }
}
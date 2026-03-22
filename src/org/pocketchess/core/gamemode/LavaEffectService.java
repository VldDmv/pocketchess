package org.pocketchess.core.gamemode;

import org.pocketchess.core.game.model.GameStatus;
import org.pocketchess.core.game.moveanalyze.ChessRules;
import org.pocketchess.core.game.moveanalyze.SoundEventCallback;
import org.pocketchess.core.game.status.GameMoveExecutor;
import org.pocketchess.core.game.status.GameStateManager;
import org.pocketchess.core.game.status.GameTimeManager;
import org.pocketchess.core.general.Board;
import org.pocketchess.core.pieces.*;

import java.util.List;

/**
 * Handles lava effects after each move:
 * - converts warning squares to lava
 * - removes pieces that land on lava
 * - checks for king destruction / checkmate by lava
 */
public class LavaEffectService {

    private final LavaManager lavaManager;
    private final Board board;
    private final GameStateManager stateManager;
    private final GameTimeManager timeManager;
    private final GameMoveExecutor moveExecutor;
    private final ChessRules ruleEngine;
    private SoundEventCallback soundCallback;

    public LavaEffectService(LavaManager lavaManager,
                             Board board,
                             GameStateManager stateManager,
                             GameTimeManager timeManager,
                             GameMoveExecutor moveExecutor,
                             ChessRules ruleEngine) {
        this.lavaManager  = lavaManager;
        this.board        = board;
        this.stateManager = stateManager;
        this.timeManager  = timeManager;
        this.moveExecutor = moveExecutor;
        this.ruleEngine   = ruleEngine;
    }

    /** Wired by Game after construction. AI copies leave this null (no sounds needed). */
    public void setSoundCallback(SoundEventCallback callback) {
        this.soundCallback = callback;
    }

    /**
     * Called after every half-move via TurnFinisher.postMoveCallback.
     *
     * @param totalHalfMoves total number of half-moves played so far
     */
    public void apply(int totalHalfMoves) {
        if (!lavaManager.isEnabled()) return;

        int snapshotKey = lavaManager.saveSnapshot();
        List<Integer> newLavaPositions = lavaManager.onMoveCompleted(board, totalHalfMoves);

        if (newLavaPositions.isEmpty()) return;

        boolean piecesEaten    = false;
        boolean kingEaten      = false;
        GameStatus kingEatenStatus = null;

        for (int encoded : newLavaPositions) {
            int[] pos   = LavaManager.decode(encoded);
            Spot  spot  = board.getBox(pos[0], pos[1]);
            Piece piece = spot.getPiece();

            if (piece == null) continue;

            spot.setPiece(null);
            piecesEaten = true;
            lavaManager.recordEatenPiece(snapshotKey, encoded, piece);

            if (piece.isWhite()) moveExecutor.getBlackCapturedPieces().add(piece);
            else                 moveExecutor.getWhiteCapturedPieces().add(piece);

            if (piece instanceof King) {
                kingEaten       = true;
                kingEatenStatus = piece.isWhite()
                        ? GameStatus.BLACK_WIN
                        : GameStatus.WHITE_WIN;
            }
        }
        if (piecesEaten && soundCallback != null) {
            soundCallback.onCapture();
        }

        if (kingEaten) {
            stateManager.setStatus(kingEatenStatus);
            timeManager.stopTimer();
            if (soundCallback != null) soundCallback.onCheckmate();
            return;
        }

        boolean prevPlayerIsWhite = !stateManager.isWhiteTurn();
        if (ruleEngine.isKingInCheck(board, prevPlayerIsWhite)) {
            stateManager.setStatus(prevPlayerIsWhite
                    ? GameStatus.BLACK_WIN
                    : GameStatus.WHITE_WIN);
            timeManager.stopTimer();
            if (soundCallback != null) soundCallback.onCheckmate();
        }
    }
}
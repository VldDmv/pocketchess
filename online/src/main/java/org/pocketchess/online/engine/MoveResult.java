package org.pocketchess.online.engine;

import org.pocketchess.core.game.model.GameStatus;

/**
 * Outcome of {@link ChessEngineAdapter#applyMove(String)} or
 * {@link ChessEngineAdapter#requestAiMove()}.
 *
 * <p>On rejection {@code uci}, {@code fen}, {@code status} are {@code null}
 * and {@code error} explains why the move was refused.
 */
public record MoveResult(boolean accepted,
                         String uci,
                         String fen,
                         GameStatus status,
                         boolean whiteToMove,
                         String error) {

    public static MoveResult ok(String uci, String fen, GameStatus status, boolean whiteToMove) {
        return new MoveResult(true, uci, fen, status, whiteToMove, null);
    }

    public static MoveResult reject(String error) {
        return new MoveResult(false, null, null, null, false, error);
    }
}

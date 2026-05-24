package org.pocketchess.online.engine;

import org.pocketchess.core.game.model.GameStatus;

import java.util.List;

/**
 * Outcome of {@link ChessEngineAdapter#applyMove(String)} or
 * {@link ChessEngineAdapter#requestAiMove()}.
 *
 * <p>On rejection {@code uci}, {@code fen}, {@code status} are {@code null}
 * and {@code error} explains why the move was refused.
 *
 * <p>{@code lavaSquares}/{@code warningSquares} capture the lava state right
 * after this move so the session can build a per-ply history for replay.
 */
public record MoveResult(boolean accepted,
                         String uci,
                         String fen,
                         GameStatus status,
                         boolean whiteToMove,
                         List<String> lavaSquares,
                         List<String> warningSquares,
                         String error) {

    public static MoveResult ok(String uci, String fen, GameStatus status, boolean whiteToMove,
                                List<String> lavaSquares, List<String> warningSquares) {
        return new MoveResult(true, uci, fen, status, whiteToMove,
                List.copyOf(lavaSquares), List.copyOf(warningSquares), null);
    }

    public static MoveResult reject(String error) {
        return new MoveResult(false, null, null, null, false, List.of(), List.of(), error);
    }
}

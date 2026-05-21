package org.pocketchess.online.game;

import org.pocketchess.core.game.model.GameStatus;

import java.util.List;

/**
 * Snapshot pushed to clients after every game event. Everything a UI needs
 * to render the board, clocks, captures, action buttons and history is in
 * here so the client never has to assemble state from multiple deltas.
 */
public record GameView(
        String gameId,
        String fen,
        String whiteName,
        String blackName,
        boolean whiteIsBot,
        boolean blackIsBot,
        boolean whiteToMove,
        GameStatus status,
        String stage,
        long whiteMillisLeft,
        long blackMillisLeft,
        boolean unlimitedTime,
        String lastMove,
        List<String> moveHistory,          // UCI — used by the client for game logic
        List<String> sanHistory,           // SAN — used for display
        List<String> fenHistory,           // FEN per ply (ply 0 = starting position) for replay
        List<String> capturedByWhite,
        List<String> capturedByBlack,
        String drawOfferBy,
        String undoRequestBy,
        String rematchOfferBy,
        String rematchToGameId,
        String variant,
        String aiDifficulty,
        String soundEvent,                 // "move" / "capture" / "castle" / "check" / "checkmate" / "draw" / "start" / null
        List<String> legalMoves,           // every legal half-move for the side to move, in UCI
        List<String> lavaSquares,
        List<String> warningSquares,
        String kingInCheckSquare,
        long whiteDisconnectedAt,
        long blackDisconnectedAt,
        long disconnectForfeitMillis,
        Integer whiteRating,
        Integer blackRating,
        long eventSeq
) {

    public static GameView of(GameSession s, String lastMove, String soundEvent) {
        long[] times = s.livePresentation();
        return new GameView(
                s.id(),
                s.fen(),
                s.white() == null ? null : s.white().name(),
                s.black() == null ? null : s.black().name(),
                s.white() != null && s.white().bot(),
                s.black() != null && s.black().bot(),
                s.whiteToMove(),
                s.status(),
                s.stage().name(),
                times[0],
                times[1],
                s.timeControl().isUnlimited(),
                lastMove,
                List.copyOf(s.moveHistory()),
                s.engine().sanHistory(),
                List.copyOf(s.fenHistory()),
                s.engine().capturedByWhite(),
                s.engine().capturedByBlack(),
                s.drawOfferBy(),
                s.undoRequestBy(),
                s.rematchOfferBy(),
                s.rematchToGameId(),
                s.variant().name(),
                s.aiDifficulty().name(),
                soundEvent,
                s.engine().legalMoves(),
                s.engine().lavaSquares(),
                s.engine().warningSquares(),
                s.engine().kingInCheckSquare(),
                s.whiteDisconnectedAt(),
                s.blackDisconnectedAt(),
                GameService.DISCONNECT_FORFEIT_MILLIS,
                s.whiteRating(),
                s.blackRating(),
                s.nextBroadcastSeq()
        );
    }
}

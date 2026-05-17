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
        String stage,                // GameSession.LifecycleStage name
        long whiteMillisLeft,
        long blackMillisLeft,
        boolean unlimitedTime,
        String lastMove,
        List<String> moveHistory,
        List<String> capturedByWhite,
        List<String> capturedByBlack,
        String drawOfferBy,
        String undoRequestBy,
        String variant,
        String aiDifficulty,
        String soundEvent           // "move" / "capture" / "check" / "checkmate" / "draw" / null
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
                s.engine().capturedByWhite(),
                s.engine().capturedByBlack(),
                s.drawOfferBy(),
                s.undoRequestBy(),
                s.variant().name(),
                s.aiDifficulty().name(),
                soundEvent
        );
    }
}

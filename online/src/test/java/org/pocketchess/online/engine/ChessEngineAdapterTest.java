package org.pocketchess.online.engine;

import org.junit.jupiter.api.Test;
import org.pocketchess.core.ai.difficulty.AIDifficulty;
import org.pocketchess.core.game.model.GameStatus;
import org.pocketchess.core.game.model.TimeControl;

import static org.assertj.core.api.Assertions.assertThat;

class ChessEngineAdapterTest {

    private static final TimeControl FIVE_MIN = new TimeControl(5 * 60, 0);

    @Test
    void appliesLegalPawnMoveAndFlipsTurn() {
        ChessEngineAdapter adapter =
                ChessEngineAdapter.newClassicGame(FIVE_MIN, AIDifficulty.MEDIUM);

        assertThat(adapter.isWhiteTurn()).isTrue();
        assertThat(adapter.fen()).startsWith("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w");

        MoveResult result = adapter.applyMove("e2e4");

        assertThat(result.accepted()).isTrue();
        assertThat(result.error()).isNull();
        assertThat(result.status()).isEqualTo(GameStatus.ACTIVE);
        assertThat(result.whiteToMove()).isFalse();
        assertThat(result.fen())
                .as("white pawn now on e4, black to move, en-passant target e3")
                .startsWith("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b")
                .endsWith(" e3");
        assertThat(adapter.isWhiteTurn()).isFalse();
    }

    @Test
    void rejectsIllegalMove() {
        ChessEngineAdapter adapter =
                ChessEngineAdapter.newClassicGame(FIVE_MIN, AIDifficulty.MEDIUM);

        MoveResult result = adapter.applyMove("e2e5");

        assertThat(result.accepted()).isFalse();
        assertThat(result.error()).contains("Illegal move");
        assertThat(adapter.isWhiteTurn()).isTrue();
        assertThat(adapter.fen()).startsWith("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w");
    }

    @Test
    void illegalMoveErrorUsesAlgebraicLabel() {
        ChessEngineAdapter adapter =
                ChessEngineAdapter.newClassicGame(FIVE_MIN, AIDifficulty.MEDIUM);

        // King can't leap e1→e3; the error should read "Ke3", not "e1e3".
        MoveResult result = adapter.applyMove("e1e3");

        assertThat(result.accepted()).isFalse();
        assertThat(result.error()).isEqualTo("Illegal move: Ke3");
    }

    @Test
    void engineReturnsLegalMoveFromInitialPosition() {
        ChessEngineAdapter adapter =
                ChessEngineAdapter.newClassicGame(FIVE_MIN, AIDifficulty.EASY);

        MoveResult ai = adapter.requestAiMove();

        assertThat(ai.accepted()).isTrue();
        assertThat(ai.uci()).matches("[a-h][1-8][a-h][1-8][qrbn]?");
        assertThat(ai.status()).isEqualTo(GameStatus.ACTIVE);
        assertThat(ai.whiteToMove()).isFalse();
    }
}

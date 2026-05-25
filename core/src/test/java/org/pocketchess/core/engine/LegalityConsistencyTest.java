package org.pocketchess.core.engine;

import org.junit.jupiter.api.Test;
import org.pocketchess.core.ai.difficulty.AIDifficulty;
import org.pocketchess.core.ai.search.FastMoveGenerator;
import org.pocketchess.core.game.model.GameMode;
import org.pocketchess.core.game.model.TimeControl;
import org.pocketchess.core.game.moveanalyze.Move;
import org.pocketchess.core.game.utils.FenUtils;
import org.pocketchess.core.gamemode.GameModeType;
import org.pocketchess.core.general.Game;
import org.pocketchess.core.pieces.Piece;

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards that the move GENERATOR and the move EXECUTOR agree on legality.
 *
 * <p>The engine has two independent legality checks: the generator filters with
 * {@code makeTemporaryMove + isKingInCheck}, while the executor uses
 * {@code RuleEngine.isMoveLegal}. If they diverge, the AI (and any UI) can pick
 * a move the executor then rejects — which froze the bot. This test asserts the
 * critical direction: every move the generator reports as legal is accepted by
 * the executor, applied exactly the way the services apply it (Chess960 castles
 * as king-takes-rook).
 */
class LegalityConsistencyTest {

    private final FastMoveGenerator gen = new FastMoveGenerator();

    @Test
    void everyGeneratedMoveIsAcceptedByTheExecutor_classic() {
        assertGeneratorAndExecutorAgree(GameModeType.CLASSIC, 15, 30);
    }

    @Test
    void everyGeneratedMoveIsAcceptedByTheExecutor_chess960() {
        assertGeneratorAndExecutorAgree(GameModeType.CHESS960, 30, 30);
    }

    private void assertGeneratorAndExecutorAgree(GameModeType variant, int games, int maxPlies) {
        Random rnd = new Random(424242L);
        for (int gameNo = 0; gameNo < games; gameNo++) {
            Game g = new Game();
            g.resetGame(new TimeControl(300, 0), GameMode.PVP, Piece.Color.WHITE,
                    AIDifficulty.MEDIUM, variant);
            for (int ply = 0; ply < maxPlies; ply++) {
                List<Move> moves = gen.generateLegalMoves(g);
                if (moves.isEmpty()) break;
                String fen = FenUtils.generateFEN(g.getBoard(), g.isWhiteTurn());

                for (Move m : moves) {
                    Game copy = new Game(g);
                    boolean accepted = applyLikeService(copy, m);
                    assertThat(accepted)
                            .as("executor must accept generated move %s in %s",
                                    describe(m), fen)
                            .isTrue();
                }
                g.makeTemporaryMove(moves.get(rnd.nextInt(moves.size())));
            }
        }
    }

    /** Applies a move through the real executor, the way the services do. */
    private boolean applyLikeService(Game g, Move m) {
        if (m.wasCastlingMove && m.chess960RookFromCol >= 0) {
            return g.playerMove(m.start.getX(), m.start.getY(),
                    m.start.getX(), m.chess960RookFromCol);
        }
        // The 4-arg path validates legality for every other move type (incl. a
        // promotion, which it accepts as a legal pawn advance and parks in
        // AWAITING_PROMOTION). The promotion piece itself doesn't affect legality.
        return g.playerMove(m.start.getX(), m.start.getY(), m.end.getX(), m.end.getY());
    }

    private static String describe(Move m) {
        return "(" + m.start.getX() + "," + m.start.getY() + ")->("
                + m.end.getX() + "," + m.end.getY() + ")"
                + (m.wasCastlingMove ? " castle rook=" + m.chess960RookFromCol : "")
                + (m.promotedTo != null ? " =" + m.promotedTo.getClass().getSimpleName() : "");
    }
}

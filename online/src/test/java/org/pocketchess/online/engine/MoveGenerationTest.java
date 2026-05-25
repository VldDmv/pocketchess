package org.pocketchess.online.engine;

import org.junit.jupiter.api.Test;
import org.pocketchess.core.ai.difficulty.AIDifficulty;
import org.pocketchess.core.ai.search.FastMoveGenerator;
import org.pocketchess.core.game.model.GameMode;
import org.pocketchess.core.game.model.TimeControl;
import org.pocketchess.core.game.moveanalyze.Move;
import org.pocketchess.core.gamemode.GameModeType;
import org.pocketchess.core.general.Board;
import org.pocketchess.core.general.Game;
import org.pocketchess.core.pieces.King;
import org.pocketchess.core.pieces.Piece;
import org.pocketchess.core.pieces.Rook;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Move-generator behaviours that bit us in lava + chess960 modes:
 *   - lava WARNING squares must remain legal targets (only active lava blocks),
 *   - chess960 castling must be generated as "king takes rook" so it works
 *     for any king/rook starting columns.
 */
class MoveGenerationTest {

    @Test
    void chess960CastlingIsEmittedAsKingTakesRook() {
        Game g = new Game();
        g.resetGame(new TimeControl(300, 0), GameMode.PVP, Piece.Color.WHITE,
                AIDifficulty.MEDIUM, GameModeType.CHESS960);

        // Hand-build a minimal kingside-castle position: white K f1 / R h1,
        // black K f8 / R h8, everything else cleared.
        Board b = g.getBoard();
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) b.getBox(r, c).setPiece(null);
        }
        b.getBox(7, 5).setPiece(new King(true));   // f1
        b.getBox(7, 7).setPiece(new Rook(true));   // h1
        b.getBox(0, 5).setPiece(new King(false));  // f8
        b.getBox(0, 7).setPiece(new Rook(false));  // h8
        b.saveAsInitial();

        List<Move> moves = new FastMoveGenerator().generateLegalMoves(g);

        // Internally the move's end is the king's final square (g1, col 6),
        // and chess960RookFromCol points at the h1 rook (col 7).
        boolean castle = moves.stream().anyMatch(m ->
                m.wasCastlingMove
                && m.start.getX() == 7 && m.start.getY() == 5    // from f1
                && m.end.getX() == 7   && m.end.getY() == 6      // king's final square g1
                && m.chess960RookFromCol == 7);                   // rook on h1
        assertThat(castle)
                .as("chess960 kingside castle should be generated (king f1, rook h1)")
                .isTrue();
    }

    @Test
    void chess960QueensideCastleFromBFileGeneratesAndExecutes() {
        // Reproduces the reported bug: king on the b-file castling queenside
        // (king b1 → c1, rook a1 → d1). The king travels only ONE square, so
        // it must be expressed as king-takes-rook, never king→c-file.
        Game g = new Game();
        g.resetGame(new TimeControl(300, 0), GameMode.PVP, Piece.Color.WHITE,
                AIDifficulty.MEDIUM, GameModeType.CHESS960);

        Board b = g.getBoard();
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) b.getBox(r, c).setPiece(null);
        }
        b.getBox(7, 0).setPiece(new Rook(true));   // a1
        b.getBox(7, 1).setPiece(new King(true));   // b1
        b.getBox(0, 0).setPiece(new Rook(false));  // a8
        b.getBox(0, 1).setPiece(new King(false));  // b8
        b.saveAsInitial();

        List<Move> moves = new FastMoveGenerator().generateLegalMoves(g);
        boolean castle = moves.stream().anyMatch(m ->
                m.wasCastlingMove
                && m.start.getX() == 7 && m.start.getY() == 1    // from b1
                && m.end.getX() == 7   && m.end.getY() == 2      // king's final square c1
                && m.chess960RookFromCol == 0);                   // rook on a1
        assertThat(castle)
                .as("chess960 queenside castle should be generated (king b1, rook a1)")
                .isTrue();

        // Apply it the way the engine receives it — king onto its own rook.
        boolean ok = g.playerMove(7, 1, 7, 0);
        assertThat(ok).as("king-takes-rook castle is accepted").isTrue();
        assertThat(b.getBox(7, 2).getPiece()).as("king lands on c1").isInstanceOf(King.class);
        assertThat(b.getBox(7, 3).getPiece()).as("rook lands on d1").isInstanceOf(Rook.class);
        assertThat(b.getBox(7, 0).getPiece()).as("a1 vacated").isNull();
        assertThat(b.getBox(7, 1).getPiece()).as("b1 vacated").isNull();
    }

    @Test
    void chess960StartAlwaysHasBothRooksWithKingBetween() {
        for (int i = 0; i < 400; i++) {
            ChessEngineAdapter a = ChessEngineAdapter.newGame(
                    new TimeControl(300, 0), AIDifficulty.MEDIUM, GameModeType.CHESS960);
            String rank1 = a.fen().split(" ")[0].split("/")[7];   // white back rank
            StringBuilder expanded = new StringBuilder();
            for (char ch : rank1.toCharArray()) {
                if (Character.isDigit(ch)) for (int k = 0; k < ch - '0'; k++) expanded.append('.');
                else expanded.append(ch);
            }
            String row = expanded.toString();
            long rooks = row.chars().filter(c -> c == 'R').count();
            int firstR = row.indexOf('R'), lastR = row.lastIndexOf('R'), king = row.indexOf('K');
            assertThat(rooks).as("white has two rooks at start: %s", row).isEqualTo(2);
            assertThat(king).as("king between the rooks: %s", row)
                    .isGreaterThan(firstR).isLessThan(lastR);
        }
    }

    @Test
    void chess960KingsideCastleWhereKingAndRookSwap() {
        // King f1, rook g1 — kingside castle ends with king g1, rook f1, i.e.
        // they effectively swap. The rook must survive (reported: it vanished).
        Game g = new Game();
        g.resetGame(new TimeControl(300, 0), GameMode.PVP, Piece.Color.WHITE,
                AIDifficulty.MEDIUM, GameModeType.CHESS960);

        Board b = g.getBoard();
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) b.getBox(r, c).setPiece(null);
        }
        b.getBox(7, 5).setPiece(new King(true));   // f1
        b.getBox(7, 6).setPiece(new Rook(true));   // g1
        b.getBox(0, 4).setPiece(new King(false));  // e8 (away from the g-file)
        b.getBox(0, 0).setPiece(new Rook(false));  // a8 (doesn't attack f1/g1)
        b.saveAsInitial();

        boolean ok = g.playerMove(7, 5, 7, 6);     // king takes rook (g1)
        assertThat(ok).as("king-takes-rook castle is accepted").isTrue();
        assertThat(b.getBox(7, 6).getPiece()).as("king lands on g1").isInstanceOf(King.class);
        assertThat(b.getBox(7, 5).getPiece()).as("rook lands on f1 (must not vanish)").isInstanceOf(Rook.class);
    }

    @Test
    void lavaStrictGenerationIsNoStricterThanExactRules() {
        // In a fresh lava game two central squares are flagged as WARNING.
        // The AI generator avoids them; the exact-rules generator does not,
        // so it must yield at least as many moves (warnings are legal targets).
        Game g = new Game();
        g.resetGame(new TimeControl(300, 0), GameMode.PVP, Piece.Color.WHITE,
                AIDifficulty.MEDIUM, GameModeType.LAVA);

        FastMoveGenerator gen = new FastMoveGenerator();
        int strict = gen.generateLegalMoves(g).size();   // warnings passable
        int aiSafe = gen.generateMoves(g).size();         // warnings avoided

        assertThat(strict)
                .as("exact-rules generation never drops a legal warning-square move")
                .isGreaterThanOrEqualTo(aiSafe);
    }
}

package org.pocketchess.core.engine;

import org.junit.jupiter.api.Test;
import org.pocketchess.core.ai.difficulty.AIDifficulty;
import org.pocketchess.core.ai.search.FastMoveGenerator;
import org.pocketchess.core.game.model.GameMode;
import org.pocketchess.core.game.model.TimeControl;
import org.pocketchess.core.game.moveanalyze.Move;
import org.pocketchess.core.game.utils.FenUtils;
import org.pocketchess.core.gamemode.GameModeType;
import org.pocketchess.core.general.Board;
import org.pocketchess.core.general.Game;
import org.pocketchess.core.pieces.Bishop;
import org.pocketchess.core.pieces.King;
import org.pocketchess.core.pieces.Knight;
import org.pocketchess.core.pieces.Pawn;
import org.pocketchess.core.pieces.Piece;
import org.pocketchess.core.pieces.Queen;
import org.pocketchess.core.pieces.Rook;

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Perft (performance test) — counts the number of leaf nodes in the legal-move
 * tree to a given depth. It is the gold-standard correctness check for a chess
 * move generator + make/undo: the counts must match known reference values, so
 * any move-generation, castling, en-passant, promotion or pin bug shows up as a
 * wrong total.
 */
class PerftTest {

    private final FastMoveGenerator gen = new FastMoveGenerator();

    private long perft(Game g, int depth) {
        List<Move> moves = gen.generateLegalMoves(g);
        if (depth <= 1) return moves.size();
        long nodes = 0;
        for (Move m : moves) {
            g.makeTemporaryMove(m);
            nodes += perft(g, depth - 1);
            g.undoTemporaryMove(m);
        }
        return nodes;
    }

    private Game classicStart() {
        Game g = new Game();
        g.resetGame(new TimeControl(300, 0), GameMode.PVP, Piece.Color.WHITE,
                AIDifficulty.MEDIUM, GameModeType.CLASSIC);
        return g;
    }

    @Test
    void perftFromTheStandardStartingPosition() {
        // Reference values are the well-known perft results for the initial
        // chess position.
        assertThat(perft(classicStart(), 1)).as("perft(1)").isEqualTo(20);
        assertThat(perft(classicStart(), 2)).as("perft(2)").isEqualTo(400);
        assertThat(perft(classicStart(), 3)).as("perft(3)").isEqualTo(8902);
        assertThat(perft(classicStart(), 4)).as("perft(4)").isEqualTo(197281);
    }

    @Test
    void perftKiwipete() {
        // "Kiwipete" — the standard perft stress position: both sides can castle
        // either way, with pins, checks and many captures. Reference perft:
        // 1=48, 2=2039, 3=97862.
        String kiwipete = "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R";
        assertThat(perft(positionFromFen(kiwipete), 1)).as("perft(1)").isEqualTo(48);
        assertThat(perft(positionFromFen(kiwipete), 2)).as("perft(2)").isEqualTo(2039);
        assertThat(perft(positionFromFen(kiwipete), 3)).as("perft(3)").isEqualTo(97862);
    }

    @Test
    void makeUndoIsIdentityForEveryLegalMove_classic() {
        assertMakeUndoIsIdentity(GameModeType.CLASSIC, 25, 40);
    }

    @Test
    void makeUndoIsIdentityForEveryLegalMove_chess960() {
        assertMakeUndoIsIdentity(GameModeType.CHESS960, 25, 40);
    }

    /**
     * Plays {@code games} random games of up to {@code maxPlies}, and at every
     * position asserts that {@code makeTemporaryMove} followed by
     * {@code undoTemporaryMove} restores the exact position for EVERY legal
     * move — so search and legal-move generation never leave the board altered.
     */
    private void assertMakeUndoIsIdentity(GameModeType variant, int games, int maxPlies) {
        Random rnd = new Random(20260525L);
        for (int gameNo = 0; gameNo < games; gameNo++) {
            Game g = new Game();
            g.resetGame(new TimeControl(300, 0), GameMode.PVP, Piece.Color.WHITE,
                    AIDifficulty.MEDIUM, variant);
            for (int ply = 0; ply < maxPlies; ply++) {
                List<Move> moves = gen.generateLegalMoves(g);
                if (moves.isEmpty()) break;
                String before = FenUtils.generateFEN(g.getBoard(), g.isWhiteTurn());
                for (Move m : moves) {
                    g.makeTemporaryMove(m);
                    g.undoTemporaryMove(m);
                    String after = FenUtils.generateFEN(g.getBoard(), g.isWhiteTurn());
                    assertThat(after)
                            .as("make+undo must restore the position (%s) for move %s",
                                    variant, FenUtils.generateFEN(g.getBoard(), g.isWhiteTurn()))
                            .isEqualTo(before);
                }
                // Advance the game by one random (permanent) move.
                g.makeTemporaryMove(moves.get(rnd.nextInt(moves.size())));
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** Builds a white-to-move position from the board field of a FEN. */
    private Game positionFromFen(String fenBoard) {
        Game g = classicStart();
        Board b = g.getBoard();
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) b.getBox(r, c).setPiece(null);
        }
        String[] ranks = fenBoard.split("/");
        for (int r = 0; r < 8; r++) {
            int col = 0;
            for (char ch : ranks[r].toCharArray()) {
                if (Character.isDigit(ch)) {
                    col += ch - '0';
                } else {
                    b.getBox(r, col).setPiece(pieceFor(ch));
                    col++;
                }
            }
        }
        b.setEnPassantTargetSquare(null);
        b.saveAsInitial();
        return g;
    }

    private static Piece pieceFor(char ch) {
        boolean white = Character.isUpperCase(ch);
        return switch (Character.toLowerCase(ch)) {
            case 'p' -> new Pawn(white);
            case 'n' -> new Knight(white);
            case 'b' -> new Bishop(white);
            case 'r' -> new Rook(white);
            case 'q' -> new Queen(white);
            case 'k' -> new King(white);
            default -> throw new IllegalArgumentException("Bad FEN piece: " + ch);
        };
    }
}

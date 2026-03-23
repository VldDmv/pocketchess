package org.pocketchess.core.ai.search;

import org.pocketchess.core.ai.evaluation.PositionEvaluator;
import org.pocketchess.core.game.moveanalyze.Move;
import org.pocketchess.core.pieces.*;

import java.util.List;

/**
 * Sorts moves for efficient alpha-beta search.
 *
 * ORDER (best to worst):
 * 1. Captures   — MVV-LVA (Most Valuable Victim – Least Valuable Attacker)
 * 2. Promotions
 * 3. Castling
 * 4. Killer moves  — quiet moves that caused a beta-cutoff at the same depth
 * 5. History heuristic — quiet moves ordered by how often they caused cutoffs
 * 6. Center control bonus
 */
public class MoveOrderer {
    private final PositionEvaluator evaluator;

    // ── Killer moves ──────────────────────────────────────────────────────────
    // Two killer slots per depth, max depth 16
    private static final int MAX_DEPTH = 16;
    private final Move[][] killerMoves = new Move[MAX_DEPTH][2];

    // ── History heuristic ─────────────────────────────────────────────────────
    // history[pieceIndex][toSquare] — incremented when a quiet move causes cutoff
    // pieceIndex: 0-5 white pieces, 6-11 black pieces (same as TranspositionTable)
    private final int[][] historyTable = new int[12][64];

    public MoveOrderer(PositionEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Sorts moves in descending order of "promisingness".
     * Call this before iterating moves in negamax/PVS.
     *
     * @param depth current search depth (used for killer-move lookup)
     */
    public void orderMoves(List<Move> moves, int depth) {
        int d = Math.min(depth, MAX_DEPTH - 1);
        moves.sort((m1, m2) -> {
            int score1 = getMoveOrderingScore(m1, d);
            int score2 = getMoveOrderingScore(m2, d);
            return Integer.compare(score2, score1);
        });
    }

    /**
     * Backwards-compatible overload — used by IterativeDeepeningSearch
     * where depth is not tracked per-call.  Killer/history still applied
     * at depth 0 (slot exists, just rarely populated at root).
     */
    public void orderMoves(List<Move> moves) {
        orderMoves(moves, 0);
    }

    /**
     * Records a killer move at the given depth.
     * Called from NegamaxEngine whenever a quiet move causes a beta cutoff.
     */
    public void recordKiller(Move move, int depth) {
        if (depth < 0 || depth >= MAX_DEPTH) return;
        // Don't record captures as killers
        if (move.pieceKilled != null || move.promotedTo != null) return;

        // Shift slot 0 → slot 1, then insert new killer in slot 0
        if (!isSameMove(move, killerMoves[depth][0])) {
            killerMoves[depth][1] = killerMoves[depth][0];
            killerMoves[depth][0] = move;
        }
    }

    /**
     * Records a history bonus for a quiet move that caused a beta cutoff.
     * The bonus is depth² so deeper cutoffs count more.
     */
    public void recordHistory(Move move, int depth) {
        if (move.pieceKilled != null || move.promotedTo != null) return;
        int pieceIdx = getPieceIndex(move.pieceMoved);
        int toSquare = move.end.getX() * 8 + move.end.getY();
        historyTable[pieceIdx][toSquare] += depth * depth;

        // Cap to avoid overflow after very long searches
        if (historyTable[pieceIdx][toSquare] > 100_000) {
            // Halve all entries (aging)
            for (int i = 0; i < 12; i++)
                for (int j = 0; j < 64; j++)
                    historyTable[i][j] /= 2;
        }
    }

    /** Clears killers and history — call at the start of each root search. */
    public void clear() {
        for (int d = 0; d < MAX_DEPTH; d++) {
            killerMoves[d][0] = null;
            killerMoves[d][1] = null;
        }
        for (int i = 0; i < 12; i++)
            for (int j = 0; j < 64; j++)
                historyTable[i][j] = 0;
    }

    // ── Scoring ───────────────────────────────────────────────────────────────

    private int getMoveOrderingScore(Move move, int depth) {
        // 1. Captures — MVV-LVA
        if (move.pieceKilled != null) {
            int victimValue   = evaluator.getPieceValue(move.pieceKilled);
            int attackerValue = evaluator.getPieceValue(move.pieceMoved);
            return 10_000 + 10 * victimValue - attackerValue;
        }

        // 2. Promotions
        if (move.promotedTo != null) {
            return 9_000 + evaluator.getPieceValue(move.promotedTo);
        }

        // 3. Castling
        if (move.wasCastlingMove) {
            return 8_000;
        }

        // 4. Killer moves (quiet)
        if (isSameMove(move, killerMoves[depth][0])) return 7_000;
        if (isSameMove(move, killerMoves[depth][1])) return 6_000;

        // 5. History heuristic
        int pieceIdx = getPieceIndex(move.pieceMoved);
        int toSquare = move.end.getX() * 8 + move.end.getY();
        int histScore = historyTable[pieceIdx][toSquare];

        // 6. Light center-control bonus on top of history
        int endX = move.end.getX();
        int endY = move.end.getY();
        int centerBonus = (endX >= 3 && endX <= 4 && endY >= 3 && endY <= 4) ? 50 : 0;

        return histScore + centerBonus;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Two moves are "the same" for killer purposes if they go from/to
     * the same squares (ignoring piece object identity across game copies).
     */
    private boolean isSameMove(Move a, Move b) {
        if (a == null || b == null) return false;
        return a.start.getX() == b.start.getX()
                && a.start.getY() == b.start.getY()
                && a.end.getX()   == b.end.getX()
                && a.end.getY()   == b.end.getY();
    }

    /**
     * Maps a piece to an index 0-11 (matches TranspositionTable convention).
     * 0-5  = white  Pawn/Knight/Bishop/Rook/Queen/King
     * 6-11 = black  Pawn/Knight/Bishop/Rook/Queen/King
     */
    private int getPieceIndex(Piece piece) {
        if (piece == null) return 0;
        int offset = piece.isWhite() ? 0 : 6;
        if (piece instanceof Pawn)   return offset;
        if (piece instanceof Knight) return 1 + offset;
        if (piece instanceof Bishop) return 2 + offset;
        if (piece instanceof Rook)   return 3 + offset;
        if (piece instanceof Queen)  return 4 + offset;
        if (piece instanceof King)   return 5 + offset;
        return offset;
    }
}
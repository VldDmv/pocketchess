package org.pocketchess.core.gamemode;

import org.pocketchess.core.pieces.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Generates a valid Chess960 (Fischer Random) back-rank arrangement.
 *
 * Rules (enforced):
 *  1. One bishop on a dark square, one on a light square.
 *  2. King is placed between the two rooks on the remaining squares.
 *  3. The same SP (starting position) number is used for both colours so
 *     the position is symmetric (white and black mirror each other).
 */
public class Chess960Setup {

    private Chess960Setup() {}

    // ─────────────────────────────────────────────────────────────────────────
    //  Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Generates a random Chess960 back-rank arrangement.
     *
     * @return array of 8 {@link Piece} objects for the back rank (index 0 = a-file).
     *         Pass {@code isWhite=true} for white, false for black.
     *         Both sides share the same SP number so the layout is symmetric.
     */
    public static Piece[] generateWhiteBackRank() {
        return buildRank(new Random().nextInt(960), true);
    }

    public static Piece[] generateBlackBackRank(Piece[] whiteRank) {
        // Mirror white's piece types — same column, opposite colour
        Piece[] black = new Piece[8];
        for (int i = 0; i < 8; i++) {
            black[i] = mirrorPiece(whiteRank[i]);
        }
        return black;
    }

    /**
     * Generates position by SP number (0–959).
     * Useful for reproducing specific positions or tests.
     */
    public static Piece[] generatePosition(int spNumber, boolean isWhite) {
        return buildRank(spNumber % 960, isWhite);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Core generation
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Deterministic SP-number → back rank mapping defined by FIDE.
     *
     * Algorithm (official Chess960 numbering):
     *  n / 4  → dark-square bishop column index  (0-3 → cols 0,2,4,6)
     *  n % 4  → light-square bishop column index (0-3 → cols 1,3,5,7)
     *  After removing bishops, remaining 6 squares:
     *    n2 = n / 16        → queen index in remaining 6 squares (0-5)
     *    n3 = (n / 96) % 10 → knight pair index (0-9, encodes 2 knight positions)
     *    Remaining 3 positions → rook, king, rook (left to right)
     */
    private static Piece[] buildRank(int sp, boolean isWhite) {
        Piece[] rank = new Piece[8];

        // ── 1. Dark-square bishop ─────────────────────────────────────────────
        // Dark squares (a1-side is dark in standard chess):
        // row 7: (7+col) % 2 == 1  →  col is even: 0,2,4,6
        int[] darkCols  = {0, 2, 4, 6};
        int[] lightCols = {1, 3, 5, 7};

        rank[darkCols [sp % 4]]       = new Bishop(isWhite);
        rank[lightCols[(sp / 4) % 4]] = new Bishop(isWhite);

        // ── 2. Collect empty slots ────────────────────────────────────────────
        List<Integer> empty = emptySlots(rank);   // 6 slots

        // ── 3. Queen ──────────────────────────────────────────────────────────
        int queenIdx = (sp / 16) % 6;
        rank[empty.remove(queenIdx)] = new Queen(isWhite);

        // ── 4. Knights (10 possible pairs from 5 remaining slots) ────────────
        int knightCode = (sp / 96) % 10;
        int[] knightPair = KNIGHT_PAIRS[knightCode];
        // Place knights at the indexed positions (higher first so indices stay valid)
        rank[empty.get(knightPair[1])] = new Knight(isWhite);
        rank[empty.get(knightPair[0])] = new Knight(isWhite);
        empty.removeIf(c -> rank[c] instanceof Knight);

        // ── 5. Remaining 3 slots → Rook – King – Rook ────────────────────────
        rank[empty.get(0)] = new Rook(isWhite);
        rank[empty.get(1)] = new King(isWhite);
        rank[empty.get(2)] = new Rook(isWhite);

        return rank;
    }

    /**
     * All 10 unordered pairs from indices {0,1,2,3,4}, listed in canonical
     * Chess960 order (same as FIDE SP numbering).
     */
    private static final int[][] KNIGHT_PAIRS = {
            {0, 1}, {0, 2}, {0, 3}, {0, 4},
            {1, 2}, {1, 3}, {1, 4},
            {2, 3}, {2, 4},
            {3, 4}
    };

    // ─────────────────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static List<Integer> emptySlots(Piece[] rank) {
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            if (rank[i] == null) slots.add(i);
        }
        return slots;
    }

    private static Piece mirrorPiece(Piece p) {
        if (p instanceof King)   return new King(false);
        if (p instanceof Queen)  return new Queen(false);
        if (p instanceof Rook)   return new Rook(false);
        if (p instanceof Bishop) return new Bishop(false);
        if (p instanceof Knight) return new Knight(false);
        return null;
    }
}
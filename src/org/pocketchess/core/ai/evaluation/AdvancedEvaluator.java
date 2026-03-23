package org.pocketchess.core.ai.evaluation;

import org.pocketchess.core.ai.difficulty.EvaluationParameters;
import org.pocketchess.core.general.Board;
import org.pocketchess.core.general.Game;
import org.pocketchess.core.pieces.*;

/**
 * Advanced positional concepts evaluator — used only at HARD level.
 *
 * Performance improvements vs original:
 *
 * 1. countDefenders() — was O(64) isMoveLegal calls per piece.
 *    Now uses ray-casting and jump-pattern checks directly on the board
 *    (no isMoveLegal, no temporary spot mutation).
 *    Complexity: O(8 directions × max 7 squares) per piece → ~5-10× faster.
 *
 * 2. getPieceCoordinationScore() — loops only over pieces, not all 64 squares
 *    for each piece.  Battery detection unchanged (already fast).
 *
 * Everything else (pawn structure, center control) is unchanged.
 */
public class AdvancedEvaluator {
    private final EvaluationParameters params;

    public AdvancedEvaluator(EvaluationParameters params) {
        this.params = params;
    }

    // ── Pawn structure ────────────────────────────────────────────────────────

    public int getAdvancedPawnStructureScore(Game game) {
        int score = 0;
        Board board = game.getBoard();

        for (int r = 1; r < 7; r++) {
            for (int c = 0; c < 8; c++) {
                Piece p = board.getBox(r, c).getPiece();
                if (!(p instanceof Pawn)) continue;

                boolean white = p.isWhite();
                int sign = white ? 1 : -1;

                if (hasConnectedPawn(board, r, c, white))
                    score += sign * params.connectedPawnBonus;
                if (isPawnChain(board, r, c, white))
                    score += sign * params.pawnChainBonus;
                if (isBackwardPawn(board, r, c, white))
                    score += sign * params.backwardPawnPenalty;
            }
        }
        return score;
    }

    private boolean hasConnectedPawn(Board board, int r, int c, boolean isWhite) {
        if (c > 0) {
            Piece left = board.getBox(r, c - 1).getPiece();
            if (left instanceof Pawn && left.isWhite() == isWhite) return true;
        }
        if (c < 7) {
            Piece right = board.getBox(r, c + 1).getPiece();
            if (right instanceof Pawn && right.isWhite() == isWhite) return true;
        }
        return false;
    }

    private boolean isPawnChain(Board board, int r, int c, boolean isWhite) {
        int backRank = isWhite ? r + 1 : r - 1;
        if (backRank < 0 || backRank > 7) return false;
        if (c > 0) {
            Piece d = board.getBox(backRank, c - 1).getPiece();
            if (d instanceof Pawn && d.isWhite() == isWhite) return true;
        }
        if (c < 7) {
            Piece d = board.getBox(backRank, c + 1).getPiece();
            if (d instanceof Pawn && d.isWhite() == isWhite) return true;
        }
        return false;
    }

    private boolean isBackwardPawn(Board board, int r, int c, boolean isWhite) {
        int forwardRank = isWhite ? r - 1 : r + 1;
        if (forwardRank < 0 || forwardRank > 7) return false;
        Piece blocker = board.getBox(forwardRank, c).getPiece();
        if (blocker != null) {
            return !isPawnChain(board, r, c, isWhite);
        }
        return false;
    }

    // ── Center control ────────────────────────────────────────────────────────

    public int getCenterControlScore(Game game) {
        int score = 0;
        Board board = game.getBoard();

        int[][] center         = {{3,3},{3,4},{4,3},{4,4}};
        int[][] extendedCenter = {{2,2},{2,3},{2,4},{2,5},{3,2},{3,5},
                {4,2},{4,5},{5,2},{5,3},{5,4},{5,5}};

        int cw = params.centerControlWeight;
        int ew = Math.max(1, cw / 3);

        for (int[] pos : center) {
            if (game.isSquareUnderAttack(board.getBox(pos[0], pos[1]), true))  score += cw;
            if (game.isSquareUnderAttack(board.getBox(pos[0], pos[1]), false)) score -= cw;

            Piece p = board.getBox(pos[0], pos[1]).getPiece();
            if (p != null) {
                int val = (p instanceof Pawn) ? cw * 2 : cw;
                score += p.isWhite() ? val : -val;
            }
        }

        for (int[] pos : extendedCenter) {
            if (game.isSquareUnderAttack(board.getBox(pos[0], pos[1]), true))  score += ew;
            if (game.isSquareUnderAttack(board.getBox(pos[0], pos[1]), false)) score -= ew;
        }

        return score;
    }

    // ── Piece coordination ────────────────────────────────────────────────────

    /**
     * Evaluates piece coordination using fast ray-casting instead of isMoveLegal.
     *
     * For each non-pawn, non-king piece we count defenders using direct
     * attack-pattern checks.  This avoids creating temporary game states
     * and cuts evaluation time significantly on HARD level.
     */
    public int getPieceCoordinationScore(Game game) {
        int score = 0;
        Board board = game.getBoard();
        int coordBonus = params.pieceCoordinationBonus;

        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece piece = board.getBox(r, c).getPiece();
                if (piece == null || piece instanceof King || piece instanceof Pawn) continue;

                int defenders = countDefendersFast(board, r, c, piece.isWhite());
                score += piece.isWhite()
                        ? defenders * coordBonus
                        : -defenders * coordBonus;

                // Battery bonus for rooks and queens (unchanged — already fast)
                if (piece instanceof Rook || piece instanceof Queen) {
                    if (hasBattery(board, r, c, piece.isWhite())) {
                        score += piece.isWhite() ? coordBonus * 7 : -coordBonus * 7;
                    }
                }
            }
        }

        return score;
    }

    /**
     * Counts allied pieces that ATTACK this square using direct pattern checks.
     *
     * Instead of calling isMoveLegal (which internally sets up a full board
     * position and checks king safety), we check raw attack patterns:
     *   - Pawns:   diagonal attack direction
     *   - Knights: L-shape jumps
     *   - Bishops: diagonal rays
     *   - Rooks:   straight rays
     *   - Queens:  both diagonal and straight rays
     *
     * This is ~5-10× faster than the original isMoveLegal loop.
     * We deliberately exclude king defenders (same as original) because
     * king-to-king "defence" is not a real coordination concept.
     */
    private int countDefendersFast(Board board, int r, int c, boolean isWhite) {
        int defenders = 0;

        // ── Pawns ─────────────────────────────────────────────────────────────
        // A white pawn on (r+1, c±1) attacks (r, c) — and vice-versa for black
        int pawnDir = isWhite ? 1 : -1;  // direction FROM which allied pawns attack
        int pr = r + pawnDir;
        if (pr >= 0 && pr < 8) {
            if (c > 0) {
                Piece p = board.getBox(pr, c - 1).getPiece();
                if (p instanceof Pawn && p.isWhite() == isWhite) defenders++;
            }
            if (c < 7) {
                Piece p = board.getBox(pr, c + 1).getPiece();
                if (p instanceof Pawn && p.isWhite() == isWhite) defenders++;
            }
        }

        // ── Knights ───────────────────────────────────────────────────────────
        int[][] knightOffsets = {{-2,-1},{-2,1},{-1,-2},{-1,2},{1,-2},{1,2},{2,-1},{2,1}};
        for (int[] off : knightOffsets) {
            int nr = r + off[0], nc = c + off[1];
            if (nr < 0 || nr > 7 || nc < 0 || nc > 7) continue;
            Piece p = board.getBox(nr, nc).getPiece();
            if (p instanceof Knight && p.isWhite() == isWhite) defenders++;
        }

        // ── Sliding pieces (Bishop / Rook / Queen) ────────────────────────────
        // Diagonals — defended by Bishop or Queen
        int[][] diagDirs = {{-1,-1},{-1,1},{1,-1},{1,1}};
        for (int[] dir : diagDirs) {
            defenders += countSliderDefender(board, r, c, isWhite,
                    dir[0], dir[1], true, false);
        }

        // Straights — defended by Rook or Queen
        int[][] straightDirs = {{-1,0},{1,0},{0,-1},{0,1}};
        for (int[] dir : straightDirs) {
            defenders += countSliderDefender(board, r, c, isWhite,
                    dir[0], dir[1], false, true);
        }

        return defenders;
    }

    /**
     * Walks a ray from (r,c) in direction (dr,dc).
     * Returns 1 if the first piece found along that ray is an allied slider
     * of the expected type, 0 otherwise.
     *
     * @param diagOk   count if piece is a Bishop or Queen
     * @param straightOk count if piece is a Rook or Queen
     */
    private int countSliderDefender(Board board, int r, int c, boolean isWhite,
                                    int dr, int dc, boolean diagOk, boolean straightOk) {
        int nr = r + dr, nc = c + dc;
        while (nr >= 0 && nr < 8 && nc >= 0 && nc < 8) {
            Piece p = board.getBox(nr, nc).getPiece();
            if (p == null) { nr += dr; nc += dc; continue; }

            // First piece found — check if it's a matching allied slider
            if (p.isWhite() == isWhite) {
                boolean isBishopOrQueen = (p instanceof Bishop || p instanceof Queen);
                boolean isRookOrQueen   = (p instanceof Rook   || p instanceof Queen);
                if ((diagOk && isBishopOrQueen) || (straightOk && isRookOrQueen)) {
                    return 1;
                }
            }
            break; // blocked by any piece (enemy or wrong type)
        }
        return 0;
    }

    /**
     * Checks if a rook/queen creates a battery with another heavy piece
     * on the same rank or file.  Unchanged from original.
     */
    private boolean hasBattery(Board board, int r, int c, boolean isWhite) {
        int[][] directions = {{-1,0},{1,0},{0,-1},{0,1}};
        for (int[] dir : directions) {
            int nr = r + dir[0], nc = c + dir[1];
            while (nr >= 0 && nr < 8 && nc >= 0 && nc < 8) {
                Piece p = board.getBox(nr, nc).getPiece();
                if (p != null) {
                    if (p.isWhite() == isWhite && (p instanceof Queen || p instanceof Rook))
                        return true;
                    break;
                }
                nr += dir[0]; nc += dir[1];
            }
        }
        return false;
    }
}
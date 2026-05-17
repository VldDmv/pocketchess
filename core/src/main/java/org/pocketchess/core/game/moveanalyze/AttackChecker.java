package org.pocketchess.core.game.moveanalyze;

import org.pocketchess.core.general.Board;
import org.pocketchess.core.pieces.*;

/**
 * Checks whether a square is under attack by enemy pieces.
 * Used for:
 * - Checking the legality of castling (the king cannot move under attack)
 * - Determining check (the king is under attack)
 * - Assessing the king's safety in AI
 */
public class AttackChecker {

    /**
     * Main method - checks all types of attacks on the square.
     */
    public boolean isSquareUnderAttack(Board board, Spot spot, boolean isAttackerWhite) {
        return isPawnAttacking(board, spot, isAttackerWhite) ||
                isKnightAttacking(board, spot, isAttackerWhite) ||
                isDiagonalAttacking(board, spot, isAttackerWhite) ||
                isStraightAttacking(board, spot, isAttackerWhite) ||
                isKingAttacking(board, spot, isAttackerWhite);
    }

    /**
     * Checks pawn attack
     */
    private boolean isPawnAttacking(Board board, Spot spot, boolean isAttackerWhite) {
        int r = spot.getX();
        int c = spot.getY();

        // Direction of pawn attack (white goes up, black goes down)
        int pawnDirection = isAttackerWhite ? 1 : -1;

        if (r + pawnDirection >= 0 && r + pawnDirection < 8) {
            // left diagonal
            if (c > 0) {
                Piece p = board.getBox(r + pawnDirection, c - 1).getPiece();
                if (p instanceof Pawn && p.isWhite() == isAttackerWhite) return true;
            }
            // right diagonal
            if (c < 7) {
                Piece p = board.getBox(r + pawnDirection, c + 1).getPiece();
                return p instanceof Pawn && p.isWhite() == isAttackerWhite;
            }
        }
        return false;
    }


    private boolean isKnightAttacking(Board board, Spot spot, boolean isAttackerWhite) {
        int r = spot.getX();
        int c = spot.getY();

        // 8 possible knight moves
        int[][] knightMoves = {{-2, -1}, {-2, 1}, {-1, -2}, {-1, 2}, {1, -2}, {1, 2}, {2, -1}, {2, 1}};

        for (int[] move : knightMoves) {
            int nr = r + move[0];
            int nc = c + move[1];
            if (nr >= 0 && nr < 8 && nc >= 0 && nc < 8) {
                Piece p = board.getBox(nr, nc).getPiece();
                if (p instanceof Knight && p.isWhite() == isAttackerWhite) return true;
            }
        }
        return false;
    }


    private boolean isDiagonalAttacking(Board board, Spot spot, boolean isAttackerWhite) {
        int r = spot.getX();
        int c = spot.getY();

        // 4 diagonal directions
        int[][] bishopDirs = {{-1, -1}, {-1, 1}, {1, -1}, {1, 1}};

        for (int[] dir : bishopDirs) {
            for (int i = 1; i < 8; i++) {
                int sr = r + i * dir[0];
                int sc = c + i * dir[1];
                if (sr >= 0 && sr < 8 && sc >= 0 && sc < 8) {
                    Piece p = board.getBox(sr, sc).getPiece();
                    if (p != null) {
                        // Found a bishop or queen of the desired color
                        if (p.isWhite() == isAttackerWhite &&
                                (p instanceof Bishop || p instanceof Queen)) {
                            return true;
                        }
                        break; // piece blocking path
                    }
                } else break; // out of bounds
            }
        }
        return false;
    }


    private boolean isStraightAttacking(Board board, Spot spot, boolean isAttackerWhite) {
        int r = spot.getX();
        int c = spot.getY();

        // 4 straight directions
        int[][] rookDirs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

        for (int[] dir : rookDirs) {
            for (int i = 1; i < 8; i++) {
                int sr = r + i * dir[0];
                int sc = c + i * dir[1];
                if (sr >= 0 && sr < 8 && sc >= 0 && sc < 8) {
                    Piece p = board.getBox(sr, sc).getPiece();
                    if (p != null) {
                        if (p.isWhite() == isAttackerWhite &&
                                (p instanceof Rook || p instanceof Queen)) {
                            return true;
                        }
                        break;
                    }
                } else break;
            }
        }
        return false;
    }


    private boolean isKingAttacking(Board board, Spot spot, boolean isAttackerWhite) {
        int r = spot.getX();
        int c = spot.getY();


        int[][] kingMoves = {{-1, -1}, {-1, 0}, {-1, 1}, {0, -1}, {0, 1}, {1, -1}, {1, 0}, {1, 1}};

        for (int[] move : kingMoves) {
            int kr = r + move[0];
            int kc = c + move[1];
            if (kr >= 0 && kr < 8 && kc >= 0 && kc < 8) {
                Piece p = board.getBox(kr, kc).getPiece();
                if (p instanceof King && p.isWhite() == isAttackerWhite) return true;
            }
        }
        return false;
    }
}

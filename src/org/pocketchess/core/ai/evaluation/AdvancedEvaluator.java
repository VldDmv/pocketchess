package org.pocketchess.core.ai.evaluation;

import org.pocketchess.core.ai.difficulty.EvaluationParameters;
import org.pocketchess.core.general.Board;
import org.pocketchess.core.general.Game;
import org.pocketchess.core.pieces.*;

/**
 * Advanced positional concepts evaluator.
 * Used only at the HARD level to evaluate subtle strategic factors:
 * 1) Pawn chains and connected pawns
 * 2) Backward pawns
 * 3) Control of the center
 * 4) Piece coordination
 * 5) Batteries of heavy pieces
 */
public class AdvancedEvaluator {
    private final EvaluationParameters params;

    public AdvancedEvaluator(EvaluationParameters params) {
        this.params = params;
    }

    /**
     * Evaluates advanced aspects of pawn structure.
     * Analyzes:
     * 1) Connected pawns (horizontally adjacent)
     * 2) Pawn chains (protecting each other diagonally)
     * 3) Backward pawns (backward and blocked)
     */
    public int getAdvancedPawnStructureScore(Game game) {
        int score = 0;
        Board board = game.getBoard();

        //pass through all the pawns (except the edges of the board)
        for (int r = 1; r < 7; r++) {
            for (int c = 0; c < 8; c++) {
                Piece p = board.getBox(r, c).getPiece();
                if (p instanceof Pawn) {

                    if (hasConnectedPawn(board, r, c, p.isWhite())) {
                        score += p.isWhite() ? params.connectedPawnBonus : -params.connectedPawnBonus;
                    }

                    if (isPawnChain(board, r, c, p.isWhite())) {
                        score += p.isWhite() ? params.pawnChainBonus : -params.pawnChainBonus;
                    }

                    if (isBackwardPawn(board, r, c, p.isWhite())) {
                        score += p.isWhite() ? params.backwardPawnPenalty : -params.backwardPawnPenalty;
                    }
                }
            }
        }

        return score;
    }

    /**
     * Checks if a pawn has an allied pawn adjacent horizontally
     */
    private boolean hasConnectedPawn(Board board, int r, int c, boolean isWhite) {
        // Checkin left
        if (c > 0) {
            Piece left = board.getBox(r, c - 1).getPiece();
            if (left instanceof Pawn && left.isWhite() == isWhite) return true;
        }
        // Checkin right
        if (c < 7) {
            Piece right = board.getBox(r, c + 1).getPiece();
            return right instanceof Pawn && right.isWhite() == isWhite;
        }
        return false;
    }

    /**
     * Checks whether the pawn is part of a pawn chain
     * Example: The pawn on e4 is protected by a pawn on d3 or f3
     */
    private boolean isPawnChain(Board board, int r, int c, boolean isWhite) {
        // The row behind the pawn
        int backRank = isWhite ? r + 1 : r - 1;
        if (backRank < 0 || backRank > 7) return false;

        // Check the left diagonal
        if (c > 0) {
            Piece diag1 = board.getBox(backRank, c - 1).getPiece();
            if (diag1 instanceof Pawn && diag1.isWhite() == isWhite) return true;
        }
        //Check the right diagonal
        if (c < 7) {
            Piece diag2 = board.getBox(backRank, c + 1).getPiece();
            return diag2 instanceof Pawn && diag2.isWhite() == isWhite;
        }
        return false;
    }

    /**
     * Checks whether the pawn is backward.
     * 1. Cannot advance safely (the square ahead is occupied)
     * 2. Not protected by other pawns
     */
    private boolean isBackwardPawn(Board board, int r, int c, boolean isWhite) {
        int forwardRank = isWhite ? r - 1 : r + 1;
        if (forwardRank < 0 || forwardRank > 7) return false;

        Piece blockingPiece = board.getBox(forwardRank, c).getPiece();
        if (blockingPiece != null) {
            return !isPawnChain(board, r, c, isWhite);
        }

        return false;
    }

    /**
     * Evaluates control of the center of the board.
     * Evaluates:
     * - 4 central squares (d4, d5, e4, e5) - high weight
     * - 12 squares of the extended center - lower weight
     * - Pieces in the center are worth more
     * - Attacks in the center are also valued
     */
    public int getCenterControlScore(Game game) {
        int score = 0;
        Board board = game.getBoard();

        // Main center (4 fields)
        int[][] center = {{3, 3}, {3, 4}, {4, 3}, {4, 4}};

        // Extended center (12 fields around the main one)
        int[][] extendedCenter = {{2, 2}, {2, 3}, {2, 4}, {2, 5},
                {3, 2}, {3, 5},
                {4, 2}, {4, 5},
                {5, 2}, {5, 3}, {5, 4}, {5, 5}};

        int centerWeight = params.centerControlWeight;
        int extendedWeight = Math.max(1, centerWeight / 3);


        for (int[] pos : center) {

            if (game.isSquareUnderAttack(board.getBox(pos[0], pos[1]), true)) {
                score += centerWeight;
            }
            if (game.isSquareUnderAttack(board.getBox(pos[0], pos[1]), false)) {
                score -= centerWeight;
            }

            // Bonus for a piece in the center (pawns cost twice as much)
            Piece p = board.getBox(pos[0], pos[1]).getPiece();
            if (p != null) {
                int value = (p instanceof Pawn) ? centerWeight * 2 : centerWeight;
                score += p.isWhite() ? value : -value;
            }
        }

        // Evaluate the extended center (only attacks, not pieces)
        for (int[] pos : extendedCenter) {
            if (game.isSquareUnderAttack(board.getBox(pos[0], pos[1]), true)) {
                score += extendedWeight;
            }
            if (game.isSquareUnderAttack(board.getBox(pos[0], pos[1]), false)) {
                score -= extendedWeight;
            }
        }

        return score;
    }

    /**
     * Assesses the coordination of pieces.
     * Well-coordinated pieces:
     * - Protect each other
     * - Create batteries (rook + queen or two rooks on a line)
     */
    public int getPieceCoordinationScore(Game game) {
        int score = 0;
        Board board = game.getBoard();
        int coordBonus = params.pieceCoordinationBonus;

        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece piece = board.getBox(r, c).getPiece();

                // evaluate only the pieces (not kings or pawns)
                if (piece != null && !(piece instanceof King) && !(piece instanceof Pawn)) {
                    // Bonus for each defender
                    int defenders = countDefenders(game, board.getBox(r, c), piece.isWhite());
                    score += piece.isWhite() ? defenders * coordBonus : -defenders * coordBonus;

                    // Special bonus for batteries with rooks and queens
                    if ((piece instanceof Rook || piece instanceof Queen)) {
                        if (hasBattery(board, r, c, piece.isWhite())) {
                            // Battery = ~7x normal coordination bonus
                            score += piece.isWhite() ? coordBonus * 7 : -coordBonus * 7;
                        }
                    }
                }
            }
        }

        return score;
    }

    /**
     * Counts the number of allied pieces defending this square.
     * Temporarily removes a piece from the square to check whether
     * allied pieces can enter this square.
     */
    private int countDefenders(Game game, Spot spot, boolean isWhite) {
        int defenders = 0;
        Piece temp = spot.getPiece();
        spot.setPiece(null);

        // Check which allied pieces can move to this square
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece defender = game.getBoard().getBox(r, c).getPiece();
                if (defender != null && defender.isWhite() == isWhite) {
                    if (game.isMoveLegal(game.getBoard().getBox(r, c), spot)) {
                        defenders++;
                    }
                }
            }
        }

        spot.setPiece(temp); // piece is back
        return defenders;
    }

    /**
     * Checks if a piece creates a battery with another heavy piece.
     */
    private boolean hasBattery(Board board, int r, int c, boolean isWhite) {
        // Four directions: up, down, left, right
        int[][] directions = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

        for (int[] dir : directions) {
            int nr = r + dir[0];
            int nc = c + dir[1];

            //go in the direction until going beyond the board
            while (nr >= 0 && nr < 8 && nc >= 0 && nc < 8) {
                Piece p = board.getBox(nr, nc).getPiece();
                if (p != null) {
                    if (p.isWhite() == isWhite &&
                            (p instanceof Queen || p instanceof Rook)) {
                        return true;
                    }
                    break; // piece blocking line
                }
                nr += dir[0];
                nc += dir[1];
            }
        }

        return false;
    }
}
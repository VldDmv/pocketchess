package org.pocketchess.core.ai.evaluation;

import org.pocketchess.core.ai.difficulty.EvaluationParameters;
import org.pocketchess.core.general.Board;
import org.pocketchess.core.general.Game;
import org.pocketchess.core.pieces.*;

/**
 * Evaluator of strategic elements of the position.
 * Responsible for evaluating:
 * - King safety
 * - Passed pawns
 * - Pawn structure
 * - Strategic bonuses (castling, bishop pair, rooks on open files)
 */
public class StrategicEvaluator {
    private final EvaluationParameters params;

    public StrategicEvaluator(EvaluationParameters params) {
        this.params = params;
    }

    /**
     * Evaluates the security of both sides' kings.
     * Considers:
     * - The presence of a pawn shield in front of the king
     * - The number of enemy pieces attacking the king's zone
     */
    public int getKingSafetyScore(Game game) {
        int score = 0;
        Spot whiteKingSpot = game.findKing(true);
        Spot blackKingSpot = game.findKing(false);

        if (whiteKingSpot != null) {
            // Penalty for the white king's weak pawn shield
            score -= calculateKingShieldPenalty(game.getBoard(), whiteKingSpot, true);
            // Penalty for attacking the white king
            score -= countAttackers(game, whiteKingSpot, false) * params.kingAttackWeight;
        }
        if (blackKingSpot != null) {
            // Bonus if the black king has a weak shield
            score += calculateKingShieldPenalty(game.getBoard(), blackKingSpot, false);
            // Bonus for attacking the black king
            score += countAttackers(game, blackKingSpot, true) * params.kingAttackWeight;
        }

        return score;
    }

    /**
     * Calculates the penalty for the king's lack of pawn protection.
     * Checks the three pawns in front of the king (if the king is on the edge of the board).
     * If the king is in the center, it is not considered vulnerable.
     */
    private int calculateKingShieldPenalty(Board board, Spot kingSpot, boolean isWhite) {
        int penalty = 0;
        int kingCol = kingSpot.getY();

        // If the king is in the center (columns c-f), he doesn't need a pawn shield
        if (kingCol > 1 && kingCol < 6) return 0;

        // The row where the defending pawns should be
        int pawnRank = isWhite ? 6 : 1;

        // Check the three columns before the king
        for (int c = kingCol - 1; c <= kingCol + 1; c++) {
            if (c >= 0 && c < 8) {
                Piece pawn = board.getBox(pawnRank, c).getPiece();
                if (!(pawn instanceof Pawn && pawn.isWhite() == isWhite)) {
                    penalty += Math.abs(params.kingShieldPenalty);
                }
            }
        }
        return penalty;
    }

    /**
     * Counts the number of squares around the king that are under attack.
     * Checks a 5x5 square with the king in the center.
     * The more squares under attack, the more dangerous the king's position.
     */
    private int countAttackers(Game game, Spot kingSpot, boolean isAttackerWhite) {
        int attackScore = 0;
        int kx = kingSpot.getX();
        int ky = kingSpot.getY();

        // Check the 5x5 area around the king
        for (int r = Math.max(0, kx - 2); r <= Math.min(7, kx + 2); r++) {
            for (int c = Math.max(0, ky - 2); c <= Math.min(7, ky + 2); c++) {
                Spot targetSpot = game.getBoard().getBox(r, c);
                if (game.isSquareUnderAttack(targetSpot, isAttackerWhite)) {
                    attackScore++;
                }
            }
        }
        return attackScore;
    }

    /**
     * Evaluates the strategic bonuses of a position.
     * Includes:
     * - Bonus for castling (king is safe)
     * - Penalty for not castling after move 10
     * - Bonus for a bishop pair
     * - Bonuses for rooks on open and semi-open files
     */
    public int getStrategicBonuses(Game game) {
        int score = 0;
        Board board = game.getBoard();
        int whiteBishops = 0;
        int blackBishops = 0;

        Spot whiteKingSpot = game.findKing(true);
        Spot blackKingSpot = game.findKing(false);

        // Bonus for a castled king (on the edge of the board)
        if (whiteKingSpot != null && (whiteKingSpot.getY() > 4 || whiteKingSpot.getY() < 3)) {
            score += params.castlingBonus;
        }
        if (blackKingSpot != null && (blackKingSpot.getY() > 4 || blackKingSpot.getY() < 3)) {
            score -= params.castlingBonus;
        }

        // Penalty for not castling after 10 moves
        if (game.getMoveHistory().size() > 20) {
            Piece whiteKing = whiteKingSpot != null ? whiteKingSpot.getPiece() : null;
            if (whiteKing instanceof King && !((King) whiteKing).hasMoved()) {
                score -= params.castlingBonus;
            }

            Piece blackKing = blackKingSpot != null ? blackKingSpot.getPiece() : null;
            if (blackKing instanceof King && !((King) blackKing).hasMoved()) {
                score += params.castlingBonus;
            }
        }

        // Counting Bishops and Evaluating Rooks
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece p = board.getBox(r, c).getPiece();
                if (p == null) continue;

                if (p instanceof Bishop) {
                    if (p.isWhite()) whiteBishops++;
                    else blackBishops++;
                }

                if (p instanceof Rook) {
                    if (p.isWhite()) score += getRookBonus(board, c, true);
                    else score -= getRookBonus(board, c, false);
                }
            }
        }

        // Bonus for a pair of bishops (two b's are stronger together)
        if (whiteBishops >= 2) score += params.bishopPairBonus;
        if (blackBishops >= 2) score -= params.bishopPairBonus;

        return score;
    }

    /**
     * Calculates the rook bonus based on the file.
     * - Open file (no pawns): maximum bonus
     * - Semi-open file (no friendly pawns): average bonus
     * - Closed file: no bonus
     */
    private int getRookBonus(Board board, int col, boolean isWhite) {
        boolean ownPawns = false;
        boolean enemyPawns = false;

        // Check the entire file for pawns
        for (int r = 0; r < 8; r++) {
            Piece p = board.getBox(r, col).getPiece();
            if (p instanceof Pawn) {
                if (p.isWhite() == isWhite) ownPawns = true;
                else enemyPawns = true;
            }
        }

        if (!ownPawns && !enemyPawns) return params.rookOnOpenFileBonus;    //opened
        if (!ownPawns) return params.rookOnSemiOpenFileBonus;                // semiopened
        return 0;                                                             // closed
    }

    /**
     * Evaluates passed pawns.
     * A passed pawn is a pawn with no enemy pawns in front of it
     * on its own or adjacent files. Such pawns are very dangerous,
     * especially if they are close to promotion.
     * The closer to promotion, the higher the bonus (exponential growth).
     */
    public int getPassedPawnBonus(Game game) {
        int score = 0;
        Board board = game.getBoard();

        // Bonuses for a passed pawn depending on the row
// [0, 10, 20, 30, 50, 75, 100, 0]
        final int[] PASSED_PAWN_BONUS = {0, 10, 20, 30, 50, 75, 100, 0};

        // Check the white passages (moving up)
        for (int c = 0; c < 8; c++) {
            for (int r = 5; r >= 1; r--) {
                Piece p = board.getBox(r, c).getPiece();
                if (p instanceof Pawn && p.isWhite()) {
                    if (isPassedPawn(board, r, c, true)) {
                        score += (PASSED_PAWN_BONUS[7 - r] * params.passedPawnWeight) / 20;
                    }
                    break; // There can only be one pass-through on a vertical line
                }
            }

            // Check black passages (moving down)
            for (int r = 2; r <= 6; r++) {
                Piece p = board.getBox(r, c).getPiece();
                if (p instanceof Pawn && !p.isWhite()) {
                    if (isPassedPawn(board, r, c, false)) {
                        score -= (PASSED_PAWN_BONUS[r] * params.passedPawnWeight) / 20;
                    }
                    break;
                }
            }
        }
        return score;
    }

    /**
     * Checks whether a pawn is passed.
     */
    private boolean isPassedPawn(Board board, int r, int c, boolean isWhite) {
        int direction = isWhite ? -1 : 1;

        // checkin three verticals (left, center, right)
        for (int col = Math.max(0, c - 1); col <= Math.min(7, c + 1); col++) {
            // movin in the direction of the pawn's movement to the edge of the board
            for (int row = r + direction; row >= 0 && row < 8; row += direction) {
                Piece p = board.getBox(row, col).getPiece();
                // encounter an enemy pawn - not a passed pawn.
                if (p instanceof Pawn && p.isWhite() != isWhite) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Evaluates pawn structure.
     * Considers:
     * - Isolated pawns (penalty)
     * - Doubled pawns (penalty)
     * Uses bitwise operations for efficiency.
     */
    public int evaluatePawnStructure(Game game) {
        int score = 0;
        Board board = game.getBoard();
        long whitePawns = 0L, blackPawns = 0L;

        // Create pawn bitmaps (each bit = one square)
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece p = board.getBox(r, c).getPiece();
                if (p instanceof Pawn) {
                    if (p.isWhite()) {
                        whitePawns |= (1L << (r * 8 + c));
                    } else {
                        blackPawns |= (1L << (r * 8 + c));
                    }
                }
            }
        }

        // Penalties for weaknesses in pawn structure
        score -= countIsolatedPawns(whitePawns) * Math.abs(params.isolatedPawnPenalty);
        score += countIsolatedPawns(blackPawns) * Math.abs(params.isolatedPawnPenalty);
        score -= countDoubledPawns(whitePawns) * Math.abs(params.doubledPawnPenalty);
        score += countDoubledPawns(blackPawns) * Math.abs(params.doubledPawnPenalty);

        return score;
    }

    /**
     * Counts isolated pawns.
     */
    private int countIsolatedPawns(long pawns) {
        int count = 0;
        for (int i = 0; i < 64; i++) {
            if ((pawns & (1L << i)) != 0) {
                int col = i % 8;
                boolean isIsolated = true;

                // Check if there are allied pawns to the left or right
                for (int j = 0; j < 64; j++) {
                    if ((pawns & (1L << j)) != 0 && Math.abs((j % 8) - col) == 1) {
                        isIsolated = false;
                        break;
                    }
                }
                if (isIsolated) count++;
            }
        }
        return count;
    }

    /**
     * Counts doubled pawns.
     * Doubled pawns are two or more pawns on the same file.
     * They block each other and create weaknesses.
     */
    private int countDoubledPawns(long pawns) {
        int count = 0;
        for (int c = 0; c < 8; c++) {
            int pawnsOnCol = 0;
            // count the pawns on each file
            for (int r = 0; r < 8; r++) {
                if ((pawns & (1L << (r * 8 + c))) != 0) {
                    pawnsOnCol++;
                }
            }
            // If there is more than one pawn, the rest are a penalty
            if (pawnsOnCol > 1) {
                count += (pawnsOnCol - 1);
            }
        }
        return count;
    }
}
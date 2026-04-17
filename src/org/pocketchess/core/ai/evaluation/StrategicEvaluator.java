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
 * - Back-rank weakness
 * - King tropism in endgame
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
            score -= calculateKingShieldPenalty(game.getBoard(), whiteKingSpot, true);
            score -= countAttackers(game, whiteKingSpot, false) * params.kingAttackWeight;
        }
        if (blackKingSpot != null) {
            score += calculateKingShieldPenalty(game.getBoard(), blackKingSpot, false);
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

        if (kingCol > 1 && kingCol < 6) return 0;

        int pawnRank = isWhite ? 6 : 1;

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
     */
    private int countAttackers(Game game, Spot kingSpot, boolean isAttackerWhite) {
        int attackScore = 0;
        int kx = kingSpot.getX();
        int ky = kingSpot.getY();

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
     * - Bonus for castling (king on g1/c1 or g8/c8)
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

        // Bonus only for actual castled positions: kingside = g-file (col 6), queenside = c-file (col 2)
        if (whiteKingSpot != null &&
                (whiteKingSpot.getY() == 6 || whiteKingSpot.getY() == 2) &&
                whiteKingSpot.getX() == 7) {
            score += params.castlingBonus;
        }
        if (blackKingSpot != null &&
                (blackKingSpot.getY() == 6 || blackKingSpot.getY() == 2) &&
                blackKingSpot.getX() == 0) {
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

        // Count bishops and evaluate rooks
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

        for (int r = 0; r < 8; r++) {
            Piece p = board.getBox(r, col).getPiece();
            if (p instanceof Pawn) {
                if (p.isWhite() == isWhite) ownPawns = true;
                else enemyPawns = true;
            }
        }

        if (!ownPawns && !enemyPawns) return params.rookOnOpenFileBonus;
        if (!ownPawns) return params.rookOnSemiOpenFileBonus;
        return 0;
    }

    /**
     * Evaluates passed pawns.
     * A passed pawn is a pawn with no enemy pawns in front of it
     * on its own or adjacent files. The closer to promotion, the higher the bonus.
     */
    public int getPassedPawnBonus(Game game) {
        int score = 0;
        Board board = game.getBoard();

        final int[] PASSED_PAWN_BONUS = {0, 15, 30, 50, 80, 130, 200, 0};

        for (int c = 0; c < 8; c++) {
            for (int r = 5; r >= 1; r--) {
                Piece p = board.getBox(r, c).getPiece();
                if (p instanceof Pawn && p.isWhite()) {
                    if (isPassedPawn(board, r, c, true)) {
                        score += (PASSED_PAWN_BONUS[7 - r] * params.passedPawnWeight) / 20;
                    }
                    break;
                }
            }

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

        for (int col = Math.max(0, c - 1); col <= Math.min(7, c + 1); col++) {
            for (int row = r + direction; row >= 0 && row < 8; row += direction) {
                Piece p = board.getBox(row, col).getPiece();
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
     */
    private int countDoubledPawns(long pawns) {
        int count = 0;
        for (int c = 0; c < 8; c++) {
            int pawnsOnCol = 0;
            for (int r = 0; r < 8; r++) {
                if ((pawns & (1L << (r * 8 + c))) != 0) {
                    pawnsOnCol++;
                }
            }
            if (pawnsOnCol > 1) {
                count += (pawnsOnCol - 1);
            }
        }
        return count;
    }

    /**
     * Bonus for king proximity to passed pawns in endgame.
     * Applies only to passed pawns — king should support promotion, not chase random pawns.
     * Also rewards general king centralization.
     *
     * @param isEndgame provided by PositionEvaluator to avoid duplicate board scans
     */
    public int getKingTropismScore(Game game, boolean isEndgame) {
        if (!isEndgame) return 0;

        int score = 0;
        Board board = game.getBoard();
        Spot whiteKing = game.findKing(true);
        Spot blackKing = game.findKing(false);

        if (whiteKing == null || blackKing == null) return 0;

        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece p = board.getBox(r, c).getPiece();
                if (!(p instanceof Pawn)) continue;
                if (!isPassedPawn(board, r, c, p.isWhite())) continue;

                int rankFactor = p.isWhite() ? (7 - r) : r;

                if (p.isWhite()) {
                    int ownDist = Math.max(Math.abs(whiteKing.getX() - r),
                            Math.abs(whiteKing.getY() - c));
                    int oppDist = Math.max(Math.abs(blackKing.getX() - r),
                            Math.abs(blackKing.getY() - c));
                    score += (7 - ownDist) * 3 * (1 + rankFactor / 3);
                    score += oppDist * 2 * (1 + rankFactor / 3);
                } else {
                    int ownDist = Math.max(Math.abs(blackKing.getX() - r),
                            Math.abs(blackKing.getY() - c));
                    int oppDist = Math.max(Math.abs(whiteKing.getX() - r),
                            Math.abs(whiteKing.getY() - c));
                    score -= (7 - ownDist) * 3 * (1 + rankFactor / 3);
                    score -= oppDist * 2 * (1 + rankFactor / 3);
                }
            }
        }

        // General king centralization in endgame
        int whiteKingCenterDist = Math.max(Math.abs(whiteKing.getX() - 3),
                Math.abs(whiteKing.getY() - 3));
        int blackKingCenterDist = Math.max(Math.abs(blackKing.getX() - 4),
                Math.abs(blackKing.getY() - 4));
        score += (7 - whiteKingCenterDist) * 4;
        score -= (7 - blackKingCenterDist) * 4;

        return score;
    }

    /**
     * Detects back-rank weakness: king trapped on back rank by own pawns
     * while opponent has rooks or queens that can reach that rank.
     * Penalty scales with the number of blocked escape squares and opponent heavy pieces.
     */
    public int getBackRankWeaknessScore(Game game) {
        int score = 0;
        score -= calculateBackRankWeakness(game, true);
        score += calculateBackRankWeakness(game, false);
        return score;
    }

    private int calculateBackRankWeakness(Game game, boolean isWhite) {
        int backRank = isWhite ? 7 : 0;
        Board board = game.getBoard();

        Spot kingSpot = game.findKing(isWhite);
        if (kingSpot == null || kingSpot.getX() != backRank) return 0;

        int kingCol = kingSpot.getY();
        int forwardRow = isWhite ? backRank - 1 : backRank + 1;

        int blockedSquares = 0;
        int escapeCandidates = 0;
        for (int c = Math.max(0, kingCol - 1); c <= Math.min(7, kingCol + 1); c++) {
            Piece p = board.getBox(forwardRow, c).getPiece();
            if (p instanceof Pawn && p.isWhite() == isWhite) {
                blockedSquares++;
            } else {
                escapeCandidates++;
            }
        }

        if (escapeCandidates >= 2) return 0;

        int heavyPieceThreats = 0;
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece p = board.getBox(r, c).getPiece();
                if (p == null || p.isWhite() == isWhite) continue;
                if (!(p instanceof Rook || p instanceof Queen)) continue;

                boolean canReachBackRank = false;
                for (int targetCol = 0; targetCol < 8; targetCol++) {
                    Spot target = board.getBox(backRank, targetCol);
                    Piece atTarget = target.getPiece();
                    if (atTarget != null && atTarget.isWhite() != isWhite) continue;
                    if (game.isMoveLegal(board.getBox(r, c), target)) {
                        canReachBackRank = true;
                        break;
                    }
                }
                if (canReachBackRank) heavyPieceThreats++;
            }
        }

        if (heavyPieceThreats == 0) return 0;

        int penalty = blockedSquares * 20 * heavyPieceThreats;
        return Math.min(penalty, 150);
    }
}
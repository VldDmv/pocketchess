package org.pocketchess.core.ai.difficulty;

import java.io.Serial;
import java.io.Serializable;

/**
 * Chess AI evaluation function parameters.
 * This class stores all the weights and coefficients that determine how the AI evaluates
 * chess positions. It serves as the "genome" for the genetic optimization algorithm.
 * Each parameter affects how the AI views the position:
 * - Large values The AI considers it important
 * - Small values The AI largely ignores it
 * - Negative values penalties for undesirable factors
 */
public class EvaluationParameters implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    // ======== FIGURE COSTS ==========
    // Basic material values (1 pawn = 100)

    public int pawnValue = 100;


    public int knightValue = 320;

    public int bishopValue = 330;

    public int rookValue = 500;

    public int queenValue = 900;


    /**
     * Mobility weight (number of available moves).
     * More moves = better position = more options.
     */
    public int mobilityWeight = 2;

    /**
     * Bonus for a pair of bishops.
     * Two bishops together are stronger than the sum of their individual values.
     */
    public int bishopPairBonus = 30;

    /**
     * Bonus for a rook on an open file (no pawns).
     * The rook becomes very active.
     */
    public int rookOnOpenFileBonus = 25;

    /**
     * Bonus for a rook on a half-open file (without its own pawns).
     */
    public int rookOnSemiOpenFileBonus = 15;

    /**
     * Bonus for castling.
     * Protects the king and activates the rook.
     */
    public int castlingBonus = 50;

    /**
     * Weight of passed pawns.
     * A pawn that can reach promotion without interference.
     */
    public int passedPawnWeight = 20;

    /**
     * Penalty for an isolated pawn (no pawns on adjacent files).
     * Such a pawn is difficult to defend.
     */
    public int isolatedPawnPenalty = -15;

    /**
     * Penalty for doubled pawns (two pawns on the same file).
     * They interfere with each other and create weaknesses.
     */
    public int doubledPawnPenalty = -10;

    /**
     * Attack weight on the king.
     * The more pieces attacking the opponent's king zone, the better.
     */
    public int kingAttackWeight = 10;

    /**
     * Penalty for the king's lack of pawn protection.
     * A king without a pawn shield is vulnerable to attack.v
     */
    public int kingShieldPenalty = -10;

    /**
     * Bonus for linked pawns (adjacent horizontally).
     */
    public int connectedPawnBonus = 10;

    /**
     * Bonus for pawn chains (pawns defending each other diagonally).
     */
    public int pawnChainBonus = 5;

    /**
     * Penalty for a backward pawn.
     * A pawn that has fallen behind its peers and cannot advance.
     */
    public int backwardPawnPenalty = -12;

    /**
     * Control of central fields provides a spatial advantage.
     */
    public int centerControlWeight = 8;

    /**
     * Pieces that protect each other work more effectively.
     */
    public int pieceCoordinationBonus = 3;

    /**
     * Parameter quality score (for the genetic algorithm).
     * The higher the fitness score, the better these parameters perform.
     */
    public int fitness = 0;

    /**
     * Creates parameters with default values for a regular game.
     */
    public EvaluationParameters() {
    }

    @Override
    public String toString() {
        return String.format(
                "Fitness: %d | N=%d, B=%d, R=%d, Q=%d | Cast=%d, Mob=%d, KingAtk=%d, " +
                        "BishPair=%d, RookOpen=%d, Passed=%d, Isolated=%d, Doubled=%d",
                fitness, knightValue, bishopValue, rookValue, queenValue,
                castlingBonus, mobilityWeight, kingAttackWeight,
                bishopPairBonus, rookOnOpenFileBonus, passedPawnWeight,
                isolatedPawnPenalty, doubledPawnPenalty
        );
    }
}
package org.pocketchess.core.ai.search;

import org.pocketchess.core.ai.evaluation.PositionEvaluator;
import org.pocketchess.core.game.moveanalyze.Move;

import java.util.List;

/**
 * Sorts moves for efficient alpha-beta search.
 * 1. MVV-LVA (Most Valuable Victim - Least Valuable Attacker)
 * 2. Pawn Promotions
 * 3. Castling
 * 4. Center Control
 */
public class MoveOrderer {
    private final PositionEvaluator evaluator;

    public MoveOrderer(PositionEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    /**
     * Sorts moves in descending order of "promisingness"
     * The most promising moves appear first.
     */
    public void orderMoves(List<Move> moves) {
        moves.sort((m1, m2) -> {
            int score1 = getMoveOrderingScore(m1);
            int score2 = getMoveOrderingScore(m2);
            return Integer.compare(score2, score1);
        });
    }

    /**
     * Calculates a heuristic estimate of the "promise" of a move.
     * The higher the estimate, the sooner the move will be considered.
     */
    private int getMoveOrderingScore(Move move) {
        int score = 0;

        // 1. CAPTURING - the most important moves to consider
        // MVV-LVA: preferring to take valuable things cheaply
        if (move.pieceKilled != null) {
            int victimValue = evaluator.getPieceValue(move.pieceKilled);
            int attackerValue = evaluator.getPieceValue(move.pieceMoved);
            score = 10000 + 10 * victimValue - attackerValue;
        }

        // 2. PROMOTIONS - very powerful moves

        if (move.promotedTo != null) {
            score += 9000;
        }

        // 3. CASTLE - usually a good move

        if (move.wasCastlingMove) {
            score += 100;
        }

        // 4. CENTER CONTROL - A Light Heuristic

        int endX = move.end.getX();
        int endY = move.end.getY();
        // Central 4 squares: d4, d5, e4, e5
        if (endX >= 3 && endX <= 4 && endY >= 3 && endY <= 4) {
            score += 50;
        }

        return score;
    }
}
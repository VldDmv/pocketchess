package org.pocketchess.core.ai.evaluation;

import org.pocketchess.core.ai.FastMoveGenerator;
import org.pocketchess.core.game.moveanalyze.Move;
import org.pocketchess.core.general.Game;

import java.util.List;

/**
 * Iterative search deepening - gradually increasing the depth.
 * Instead of searching at depth N immediately, search sequentially:
 * depth 1 → depth 2 → depth 3 → ... → depth N
 */
public class IterativeDeepeningSearch {
    private final NegamaxEngine engine;
    private final FastMoveGenerator moveGenerator;

    public IterativeDeepeningSearch(NegamaxEngine engine, FastMoveGenerator moveGenerator) {
        this.engine = engine;
        this.moveGenerator = moveGenerator;
    }

    /**
     * Performs iterative deepening to the target depth.
     */
    public Move search(Game game, int targetDepth) {
        List<Move> legalMoves = moveGenerator.generateMoves(game);

        // Boundary cases
        if (legalMoves.isEmpty()) return null;
        if (legalMoves.size() == 1) return legalMoves.get(0);

        Move bestMoveFound = null;

        // Iterative deepening: gradually increasing the depth
        for (int currentDepth = 1; currentDepth <= targetDepth; currentDepth++) {
            Move bestMoveAtThisDepth = engine.findBestMoveAtDepth(game, currentDepth);

            if (bestMoveAtThisDepth != null) {
                bestMoveFound = bestMoveAtThisDepth;
            }

            // At each iteration, information about the best moves is stored
            // in the transposition table, improving the efficiency of the next iteration.
        }

        return bestMoveFound;
    }
}
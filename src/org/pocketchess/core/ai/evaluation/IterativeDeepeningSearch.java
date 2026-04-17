package org.pocketchess.core.ai.evaluation;

import org.pocketchess.core.ai.search.FastMoveGenerator;
import org.pocketchess.core.game.moveanalyze.Move;
import org.pocketchess.core.general.Game;

import java.util.List;

/**
 * Iterative deepening with an endgame depth boost.
 */
public class IterativeDeepeningSearch {
    private final NegamaxEngine engine;
    private final FastMoveGenerator moveGenerator;
    private final PositionEvaluator evaluator;

    /** Extra plies added when the position is endgame. */
    private static final int ENDGAME_EXTRA_DEPTH = 1;

    public IterativeDeepeningSearch(NegamaxEngine engine,
                                    FastMoveGenerator moveGenerator) {
        this(engine, moveGenerator, null);
    }

    public IterativeDeepeningSearch(NegamaxEngine engine,
                                    FastMoveGenerator moveGenerator,
                                    PositionEvaluator evaluator) {
        this.engine        = engine;
        this.moveGenerator = moveGenerator;
        this.evaluator     = evaluator;
    }

    public Move search(Game game, int targetDepth) {
        List<Move> legalMoves = moveGenerator.generateMoves(game);
        if (legalMoves.isEmpty()) return null;
        if (legalMoves.size() == 1) return legalMoves.get(0);

        int effectiveDepth = targetDepth;
        if (evaluator != null && evaluator.isEndGame(game)) {
            effectiveDepth = targetDepth + ENDGAME_EXTRA_DEPTH;
        }

        Move bestMoveFound = null;
        for (int currentDepth = 1; currentDepth <= effectiveDepth; currentDepth++) {
            Move best = engine.findBestMoveAtDepth(game, currentDepth);
            if (best != null) bestMoveFound = best;
        }
        return bestMoveFound;
    }
}
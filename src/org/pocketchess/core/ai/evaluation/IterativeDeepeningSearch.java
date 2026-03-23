package org.pocketchess.core.ai.evaluation;

import org.pocketchess.core.ai.search.FastMoveGenerator;
import org.pocketchess.core.game.moveanalyze.Move;
import org.pocketchess.core.general.Game;

import java.util.List;

/**
 * Iterative deepening with a conservative endgame depth boost.
 *
 * ENDGAME conditions (ALL must be true):
 *   1. No queens on the board
 *   2. Total non-king material < ENDGAME_MATERIAL_THRESHOLD
 *
 * Previous threshold was 4000 cp — that triggered with a queen still on
 * the board (queen = 900, so 4000 could mean Q + R + N + pawns which is
 * still a complex middlegame/early endgame with high branching factor).
 *
 * New threshold: 2200 cp — roughly king + rook + 2-3 pawns each side.
 * This is a genuine endgame where branching factor drops enough to
 * safely add an extra ply.
 *
 * Boost reduced from +2 to +1 as extra safety margin.
 * Depth 5 → depth 6 in true endgame:
 *   ~10 moves branching → 10^6 = 1M nodes (fast)
 *   vs 10^5 = 100k at depth 5 (same speed range)
 */
public class IterativeDeepeningSearch {
    private final NegamaxEngine engine;
    private final FastMoveGenerator moveGenerator;
    private final PositionEvaluator evaluator;

    /** Extra plies in a true endgame (few pieces, no queens). */
    private static final int ENDGAME_EXTRA_DEPTH = 1;

    /**
     * Total non-king material threshold for endgame detection.
     * 2200 cp ≈ two rooks + a few pawns per side.
     * With a queen still present the value easily exceeds this.
     */
    private static final int ENDGAME_MATERIAL_THRESHOLD = 2200;

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
        if (evaluator != null && isTrueEndgame(game)) {
            effectiveDepth = targetDepth + ENDGAME_EXTRA_DEPTH;
        }

        Move bestMoveFound = null;
        for (int currentDepth = 1; currentDepth <= effectiveDepth; currentDepth++) {
            Move best = engine.findBestMoveAtDepth(game, currentDepth);
            if (best != null) bestMoveFound = best;
        }
        return bestMoveFound;
    }

    /**
     * True endgame: no queens AND total material below threshold.
     * Uses direct board scan — does not call evaluator.isEndGame() which
     * had the too-low threshold of 4000 cp.
     */
    private boolean isTrueEndgame(Game game) {
        int total = 0;
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                org.pocketchess.core.pieces.Piece p = game.getBoard().getBox(r, c).getPiece();
                if (p == null || p instanceof org.pocketchess.core.pieces.King) continue;
                if (p instanceof org.pocketchess.core.pieces.Queen) return false; // queen present → not endgame
                total += evaluator.getPieceValue(p);
            }
        }
        return total < ENDGAME_MATERIAL_THRESHOLD;
    }
}
package org.pocketchess.core.ai.evaluation;

import org.pocketchess.core.ai.search.FastMoveGenerator;
import org.pocketchess.core.ai.search.MoveOrderer;
import org.pocketchess.core.ai.search.TranspositionTable;
import org.pocketchess.core.ai.difficulty.AIDifficulty;
import org.pocketchess.core.game.moveanalyze.Move;
import org.pocketchess.core.general.Game;

import java.util.List;

/**
 * A search engine based on the Negamax algorithm with alpha-beta cutoffs.
 * - Alpha-beta cutoffs (discards obviously bad options)
 * - Principal Variation Search (PVS) - accelerated search
 * - Null Move Pruning - discards positions without threats
 * - Transposition Table - caches position evaluations
 * - Quiescence Search - analyzes tactical sequences
 */
public class NegamaxEngine {
    private final TranspositionTable transpositionTable;
    private final FastMoveGenerator moveGenerator;
    private final QuiescenceSearch quiescenceSearch;
    private final PositionEvaluator evaluator;
    private final MoveOrderer moveOrderer;
    private final AIDifficulty difficulty;

    public NegamaxEngine(TranspositionTable tt, FastMoveGenerator mg,
                         QuiescenceSearch qs, PositionEvaluator eval,
                         MoveOrderer orderer, AIDifficulty diff) {
        this.transpositionTable = tt;
        this.moveGenerator = mg;
        this.quiescenceSearch = qs;
        this.evaluator = eval;
        this.moveOrderer = orderer;
        this.difficulty = diff;
    }

    /**
     * Finds the best move at a given depth using PVS.
     * Principal Variation Search (PVS):
     * 1. The first move is searched in the full window (alpha, beta)
     * 2. The remaining moves are searched in the zero window (alpha, alpha+1)
     * 3. If the zero window yields a result > alpha, repeat the search
     */
    public Move findBestMoveAtDepth(Game game, int depth) {
        List<Move> legalMoves = moveGenerator.generateMoves(game);
        if (legalMoves.isEmpty()) return null;


        moveOrderer.orderMoves(legalMoves);

        Move bestMove = legalMoves.get(0);
        int bestScore = Integer.MIN_VALUE;
        int alpha = Integer.MIN_VALUE + 1;
        int beta = Integer.MAX_VALUE;

        for (int i = 0; i < legalMoves.size(); i++) {
            Move move = legalMoves.get(i);
            game.makeTemporaryMove(move);
            int score;

            if (i == 0) {
                // First move: full search window
                score = -negamax(game, depth - 1, -beta, -alpha);
            } else {
                // Other moves: first the zero window
                score = -negamax(game, depth - 1, -alpha - 1, -alpha);

               // hit the window - repeat the search
                if (score > alpha && score < beta) {
                    score = -negamax(game, depth - 1, -beta, -alpha);
                }
            }

            game.undoTemporaryMove(move);

            if (score > bestScore) {
                bestScore = score;
                bestMove = move;
            }

            alpha = Math.max(alpha, bestScore);

            // Beta cutoff - other moves are not needed
            if (alpha == beta) {
                break;
            }
        }

        transpositionTable.put(game, depth, bestScore,
                TranspositionTable.Entry.EntryType.EXACT, bestMove);
        return bestMove;
    }

    /**
     * Recursive Negamax
     * 1. Check for mate/depth
     * 2. Search in the transposition table
     * 3. Null move pruning (if applicable)
     * 4. Recursive search over all moves
     * 5. Store the result
     */
    public int negamax(Game game, int depth, int alpha, int beta) {
        // Mate distance pruning - we don't search for mats further than possible
        int mateValue = 100000 + depth;
        alpha = Math.max(alpha, -mateValue);
        beta = Math.min(beta, mateValue);
        if (alpha >= beta) return alpha;

        if (depth <= 0) {
            return quiescenceSearch.search(game, alpha, beta, difficulty.getQuiescenceDepth());
        }

        int originalAlpha = alpha;


        TranspositionTable.Entry ttEntry = transpositionTable.get(game);
        if (ttEntry != null && ttEntry.depth >= depth) {
            // Accurate estimate - return immediately
            if (ttEntry.type == TranspositionTable.Entry.EntryType.EXACT) {
                return ttEntry.score;
            }
            // Lower bound
            if (ttEntry.type == TranspositionTable.Entry.EntryType.ALPHA && ttEntry.score <= alpha) {
                return alpha;
            }
            // Upper bound
            if (ttEntry.type == TranspositionTable.Entry.EntryType.BETA && ttEntry.score >= beta) {
                return beta;
            }
        }


        if (difficulty != AIDifficulty.EASY && depth >= 3 && !game.isKingInCheck(game.isWhiteTurn())) {
            int R = (depth >= 6) ? 3 : 2;
            game.makeNullMove();
            int score = -negamax(game, depth - 1 - R, -beta, -beta + 1);
            game.undoNullMove();

            // Even if beta is better with a pass - cut it off
            if (score >= beta) {
                return beta;
            }
        }

        List<Move> legalMoves = moveGenerator.generateMoves(game);


        if (legalMoves.isEmpty()) {
            if (game.isKingInCheck(game.isWhiteTurn())) {

                return -100000 - depth;
            }

            return evaluator.getContemptScore(game);
        }


        moveOrderer.orderMoves(legalMoves);
        Move bestMove = null;


        for (Move move : legalMoves) {
            game.makeTemporaryMove(move);

            int score;

            if (game.isThreefoldRepetition() || game.isFiftyMoveRule()) {
                score = -evaluator.getContemptScore(game);
            } else {

                score = -negamax(game, depth - 1, -beta, -alpha);
            }

            game.undoTemporaryMove(move);

            // Beta cutoff - found too good move
            if (score >= beta) {
                transpositionTable.put(game, depth, beta,
                        TranspositionTable.Entry.EntryType.BETA, move);
                return beta;
            }

            // Found a new best move
            if (score > alpha) {
                alpha = score;
                bestMove = move;
            }
        }


        TranspositionTable.Entry.EntryType entryType = (alpha <= originalAlpha) ?
                TranspositionTable.Entry.EntryType.ALPHA :
                TranspositionTable.Entry.EntryType.EXACT;
        transpositionTable.put(game, depth, alpha, entryType, bestMove);

        return alpha;
    }
}
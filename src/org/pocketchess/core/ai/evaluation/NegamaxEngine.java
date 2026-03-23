package org.pocketchess.core.ai.evaluation;

import org.pocketchess.core.ai.search.FastMoveGenerator;
import org.pocketchess.core.ai.search.MoveOrderer;
import org.pocketchess.core.ai.search.TranspositionTable;
import org.pocketchess.core.ai.difficulty.AIDifficulty;
import org.pocketchess.core.game.moveanalyze.Move;
import org.pocketchess.core.general.Game;

import java.util.List;

/**
 * Negamax: Alpha-Beta, PVS, Null Move Pruning, LMR,
 * Killer/History, Repetition avoidance, Check extensions.
 */
public class NegamaxEngine {
    private final TranspositionTable transpositionTable;
    private final FastMoveGenerator moveGenerator;
    private final QuiescenceSearch quiescenceSearch;
    private final PositionEvaluator evaluator;
    private final MoveOrderer moveOrderer;
    private final AIDifficulty difficulty;

    // ── LMR ──────────────────────────────────────────────────────────────────
    private static final int LMR_FULL_DEPTH_MOVES = 4;
    private static final int LMR_MIN_DEPTH        = 3;
    private static final int LMR_REDUCTION        = 1;

    // ── Repetition ───────────────────────────────────────────────────────────
    private static final int REPETITION_WARNING_PENALTY = 150;

    /**
     * If the position is this bad or worse (from the moving side's perspective),
     * skip the repetition penalty — a draw is actually welcome.
     */
    private static final int REPETITION_PENALTY_THRESHOLD = -200;

    // ── Check extension ───────────────────────────────────────────────────────
    private static final int CHECK_EXTENSION = 1;
    private static final int MAX_EXTENSIONS  = 3;

    public NegamaxEngine(TranspositionTable tt, FastMoveGenerator mg,
                         QuiescenceSearch qs, PositionEvaluator eval,
                         MoveOrderer orderer, AIDifficulty diff) {
        this.transpositionTable = tt;
        this.moveGenerator      = mg;
        this.quiescenceSearch   = qs;
        this.evaluator          = eval;
        this.moveOrderer        = orderer;
        this.difficulty         = diff;
    }

    // ── Root ─────────────────────────────────────────────────────────────────

    public Move findBestMoveAtDepth(Game game, int depth) {
        List<Move> legalMoves = moveGenerator.generateMoves(game);
        if (legalMoves.isEmpty()) return null;

        moveOrderer.orderMoves(legalMoves, depth);

        Move bestMove  = legalMoves.get(0);
        int  bestScore = Integer.MIN_VALUE;
        int  alpha     = Integer.MIN_VALUE + 1;
        int  beta      = Integer.MAX_VALUE;

        for (int i = 0; i < legalMoves.size(); i++) {
            Move move = legalMoves.get(i);
            game.makeTemporaryMove(move);
            int score;

            if (i == 0) {
                score = -negamax(game, depth - 1, -beta, -alpha, depth - 1, 0);
            } else {
                score = -negamax(game, depth - 1, -alpha - 1, -alpha, depth - 1, 0);
                if (score > alpha && score < beta)
                    score = -negamax(game, depth - 1, -beta, -alpha, depth - 1, 0);
            }

            game.undoTemporaryMove(move);

            if (score > bestScore) { bestScore = score; bestMove = move; }
            if (score > alpha)     alpha = score;
            if (alpha >= beta)     break;
        }

        transpositionTable.put(game, depth, bestScore,
                TranspositionTable.Entry.EntryType.EXACT, bestMove);
        return bestMove;
    }

    // ── Recursive ────────────────────────────────────────────────────────────

    public int negamax(Game game, int depth, int alpha, int beta,
                       int plyFromRoot, int extensionsSoFar) {

        // Mate-distance pruning
        int mateValue = 100_000 + depth;
        alpha = Math.max(alpha, -mateValue);
        beta  = Math.min(beta,   mateValue);
        if (alpha >= beta) return alpha;

        if (depth <= 0)
            return quiescenceSearch.search(game, alpha, beta, difficulty.getQuiescenceDepth());

        int originalAlpha = alpha;

        // Transposition table
        TranspositionTable.Entry ttEntry = transpositionTable.get(game);
        if (ttEntry != null && ttEntry.depth >= depth) {
            switch (ttEntry.type) {
                case EXACT -> { return ttEntry.score; }
                case ALPHA -> { if (ttEntry.score <= alpha) return alpha; }
                case BETA  -> { if (ttEntry.score >= beta)  return beta;  }
            }
        }

        // Null move pruning — skip when in check
        if (difficulty != AIDifficulty.EASY
                && depth >= 3
                && !game.isKingInCheck(game.isWhiteTurn())) {
            int R = (depth >= 6) ? 3 : 2;
            game.makeNullMove();
            int nmScore = -negamax(game, depth - 1 - R, -beta, -beta + 1,
                    plyFromRoot + 1, extensionsSoFar);
            game.undoNullMove();
            if (nmScore >= beta) return beta;
        }

        List<Move> legalMoves = moveGenerator.generateMoves(game);

        if (legalMoves.isEmpty()) {
            return game.isKingInCheck(game.isWhiteTurn())
                    ? -100_000 - depth
                    : evaluator.getContemptScore(game);
        }

        moveOrderer.orderMoves(legalMoves, plyFromRoot);

        Move bestMove  = null;
        int  moveIndex = 0;

        for (Move move : legalMoves) {
            game.makeTemporaryMove(move);

            int score;
            boolean isQuiet = move.pieceKilled == null
                    && move.promotedTo  == null
                    && !move.wasCastlingMove;

            // ── Repetition detection ──────────────────────────────────────────
            if (game.isFiftyMoveRule() || game.isThreefoldRepetition()) {
                score = -evaluator.getContemptScore(game);

            } else {
                int posCount = game.getPositionCount();

                if (posCount >= 2) {
                    int deepScore = -negamax(game, depth - 1, -beta, -alpha,
                            plyFromRoot + 1, extensionsSoFar);
                    int penalty = (deepScore > REPETITION_PENALTY_THRESHOLD)
                            ? REPETITION_WARNING_PENALTY
                            : 0;
                    score = deepScore - penalty;

                } else {
                    // ── Check extension ───────────────────────────────────────
                    boolean givesCheck = game.isKingInCheck(game.isWhiteTurn());
                    int extension = 0;
                    if (givesCheck && extensionsSoFar < MAX_EXTENSIONS) {
                        extension = CHECK_EXTENSION;
                    }

                    int newDepth = depth - 1 + extension;
                    int newExt   = extensionsSoFar + extension;

                    // ── LMR — never reduce if move gives check ────────────────
                    if (extension == 0
                            && moveIndex >= LMR_FULL_DEPTH_MOVES
                            && depth >= LMR_MIN_DEPTH
                            && isQuiet
                            && !game.isKingInCheck(!game.isWhiteTurn())) {

                        score = -negamax(game, newDepth - LMR_REDUCTION,
                                -alpha - 1, -alpha, plyFromRoot + 1, newExt);
                        if (score > alpha)
                            score = -negamax(game, newDepth, -beta, -alpha,
                                    plyFromRoot + 1, newExt);
                    } else {
                        score = -negamax(game, newDepth, -beta, -alpha,
                                plyFromRoot + 1, newExt);
                    }
                }
            }

            game.undoTemporaryMove(move);

            if (score >= beta) {
                if (isQuiet) {
                    moveOrderer.recordKiller(move, plyFromRoot);
                    moveOrderer.recordHistory(move, depth);
                }
                transpositionTable.put(game, depth, beta,
                        TranspositionTable.Entry.EntryType.BETA, move);
                return beta;
            }

            if (score > alpha) { alpha = score; bestMove = move; }
            moveIndex++;
        }

        var entryType = (alpha <= originalAlpha)
                ? TranspositionTable.Entry.EntryType.ALPHA
                : TranspositionTable.Entry.EntryType.EXACT;
        transpositionTable.put(game, depth, alpha, entryType, bestMove);
        return alpha;
    }

    /** Backwards-compatible overload for MistakeGenerator. */
    public int negamax(Game game, int depth, int alpha, int beta) {
        return negamax(game, depth, alpha, beta,
                Math.max(0, difficulty.getSearchDepth() - depth), 0);
    }
}
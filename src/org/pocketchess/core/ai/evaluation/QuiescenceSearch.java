package org.pocketchess.core.ai.evaluation;

import org.pocketchess.core.ai.FastMoveGenerator;
import org.pocketchess.core.game.moveanalyze.Move;
import org.pocketchess.core.general.Game;
import org.pocketchess.core.pieces.Piece;

import java.util.ArrayList;
import java.util.List;

/**
 * Quiescence Search - search in "noisy" positions.
 */
public class QuiescenceSearch {
    private final FastMoveGenerator moveGenerator;
    private final PositionEvaluator evaluator;

    public QuiescenceSearch(FastMoveGenerator moveGenerator, PositionEvaluator evaluator) {
        this.moveGenerator = moveGenerator;
        this.evaluator = evaluator;
    }

    /**
     * Performs a quiescence search - searches only for tactical moves.
     * 1. Evaluate the current position (stand-off)
     * 2. If the evaluation is >= beta, you can prune
     * 3. Generate only "noisy" moves (captures, checks, promotions)
     * 4. Recursively search until depth > 0 or the position becomes quiet
     */
    public int search(Game game, int alpha, int beta, int depth) {
        // Stand-pat evaluation - evaluation without additional moves
        int standPat = evaluator.evaluate(game);
        if (!game.isWhiteTurn()) {
            standPat = -standPat;
        }

        // Reached maximum quiescence depth
        if (depth == 0) return standPat;

        // Beta cutoff - the position is already too good
        if (standPat >= beta) return beta;

        // Delta pruning - if even the best move can't raise alpha, skip it
        if (alpha < standPat) alpha = standPat;

        List<Move> noisyMoves = generateNoisyMoves(game);
        orderMoves(noisyMoves);
// analyze each tactical move
        for (Move move : noisyMoves) {
            game.makeTemporaryMove(move);
            int score = -search(game, -beta, -alpha, depth - 1);
            game.undoTemporaryMove(move);

            if (score >= beta) return beta;
            if (score > alpha) alpha = score;
        }

        return alpha;
    }

    /**
     * Generates only noisy tactical moves.
     * captures/promotions/checks
     * Silent positional moves are ignored.
     */
    private List<Move> generateNoisyMoves(Game game) {
        List<Move> allMoves = moveGenerator.generateMoves(game);
        List<Move> noisyMoves = new ArrayList<>();

        for (Move move : allMoves) {
            //capture/promotion
            if (move.pieceKilled != null || move.promotedTo != null) {
                noisyMoves.add(move);
            } else {
                // check?
                if (isMoveCheck(game, move)) {
                    noisyMoves.add(move);
                }
            }
        }

        return noisyMoves;
    }


    private boolean isMoveCheck(Game game, Move move) {
        game.makeTemporaryMove(move);
        boolean isCheck = game.isKingInCheck(game.isWhiteTurn());
        game.undoTemporaryMove(move);
        return isCheck;
    }

    /**
     * Sorts moves by MVV-LVA
     */
    private void orderMoves(List<Move> moves) {
        moves.sort((m1, m2) -> {
            int score1 = 0;
            int score2 = 0;

            // MVV-LVA: 10 * victim - attacker
            if (m1.pieceKilled != null)
                score1 = 10 * getPieceValue(m1.pieceKilled) - getPieceValue(m1.pieceMoved);
            if (m2.pieceKilled != null)
                score2 = 10 * getPieceValue(m2.pieceKilled) - getPieceValue(m2.pieceMoved);

            return Integer.compare(score2, score1);
        });
    }

    private int getPieceValue(Piece piece) {
        return evaluator.getPieceValue(piece);
    }
}
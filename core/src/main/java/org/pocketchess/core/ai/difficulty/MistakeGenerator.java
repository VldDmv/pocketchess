package org.pocketchess.core.ai.difficulty;

import org.pocketchess.core.ai.search.FastMoveGenerator;
import org.pocketchess.core.ai.evaluation.NegamaxEngine;
import org.pocketchess.core.game.moveanalyze.Move;
import org.pocketchess.core.general.Game;

import java.util.Random;

/**
 * A deliberate error generator to simulate human play.
 * Instead of always making the best move, the AI can intentionally choose a
 * suboptimal move to make the game feel more natural and appropriate
 * for the selected difficulty level.
 */
public class MistakeGenerator {
    private final NegamaxEngine engine;
    private final FastMoveGenerator moveGenerator;
    private final AIDifficulty difficulty;
    private final Random random;

    public MistakeGenerator(NegamaxEngine engine, FastMoveGenerator moveGenerator,
                            AIDifficulty difficulty, Random random) {
        this.engine = engine;
        this.moveGenerator = moveGenerator;
        this.difficulty = difficulty;
        this.random = random;
    }

    /**
     * Deliberately chooses the wrong move to simulate human error.
     */
    public Move makeDeliberateMistake(Game game, Move bestMove) {
        var allMoves = moveGenerator.generateMoves(game);
        if (allMoves.size() <= 1) return bestMove;

        // On an easy level, choose from the top 5, on an average level, from the top 3
        int topN = difficulty == AIDifficulty.EASY ? 5 : 3;

        // evaluate all moves
        var scoredMoves = new java.util.ArrayList<ScoredMove>();
        for (Move move : allMoves) {
            game.makeTemporaryMove(move);
            int score = -engine.negamax(game, 1, Integer.MIN_VALUE + 1, Integer.MAX_VALUE);
            game.undoTemporaryMove(move);
            scoredMoves.add(new ScoredMove(move, score));
        }

        // Sort from best to worst
        scoredMoves.sort((a, b) -> Integer.compare(b.score, a.score));

        // Select a random move from the best N
        int pickIndex = random.nextInt(Math.min(topN, scoredMoves.size()));
        return scoredMoves.get(pickIndex).move;
    }

    /**
     * Inner class for storing a move and its evaluation
     */
    private record ScoredMove(Move move, int score) {
    }
}
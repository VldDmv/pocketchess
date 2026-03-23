package org.pocketchess.core.ai.search;

import org.pocketchess.core.ai.difficulty.AIDifficulty;
import org.pocketchess.core.ai.difficulty.EvaluationParameters;
import org.pocketchess.core.ai.difficulty.MistakeGenerator;
import org.pocketchess.core.ai.evaluation.IterativeDeepeningSearch;
import org.pocketchess.core.ai.evaluation.NegamaxEngine;
import org.pocketchess.core.ai.evaluation.PositionEvaluator;
import org.pocketchess.core.ai.evaluation.QuiescenceSearch;
import org.pocketchess.core.ai.opening.OpeningBookManager;
import org.pocketchess.core.game.moveanalyze.Move;
import org.pocketchess.core.general.Game;

import java.util.Random;

/**
 * Main AI coordinator.
 *
 * Change: passes evaluator to IterativeDeepeningSearch so the endgame
 * depth boost works correctly.
 */
public class AIPlayer {
    private final AIDifficulty difficulty;
    private final TranspositionTable transpositionTable;
    private final FastMoveGenerator moveGenerator;
    private final Random random;

    private final PositionEvaluator positionEvaluator;
    private final QuiescenceSearch quiescenceSearch;

    private final NegamaxEngine negamaxEngine;
    private final IterativeDeepeningSearch iterativeSearch;
    private final MoveOrderer moveOrderer;
    private final OpeningBookManager openingBook;
    private final MistakeGenerator mistakeGenerator;

    public AIPlayer(EvaluationParameters params, AIDifficulty difficulty) {
        this.difficulty         = difficulty;
        this.random             = new Random();
        this.transpositionTable = new TranspositionTable();
        this.moveGenerator      = new FastMoveGenerator();

        this.positionEvaluator  = new PositionEvaluator(params, moveGenerator, difficulty);
        this.quiescenceSearch   = new QuiescenceSearch(moveGenerator, positionEvaluator);

        this.moveOrderer        = new MoveOrderer(positionEvaluator);
        this.negamaxEngine      = new NegamaxEngine(
                transpositionTable, moveGenerator, quiescenceSearch,
                positionEvaluator, moveOrderer, difficulty);

        // Pass evaluator so IterativeDeepeningSearch can detect endgame
        this.iterativeSearch    = new IterativeDeepeningSearch(
                negamaxEngine, moveGenerator, positionEvaluator);

        this.openingBook        = new OpeningBookManager(difficulty, random);
        this.mistakeGenerator   = new MistakeGenerator(negamaxEngine, moveGenerator,
                difficulty, random);
    }

    public Move findBestMove(Game game) {
        if (!game.isLive()) return null;

        Game gameCopy = new Game(game);

        Move bookMove = openingBook.getBookMove(gameCopy);
        if (bookMove != null) return convertMoveToOriginalGame(bookMove, game);

        transpositionTable.clear();
        positionEvaluator.clearCache();
        moveOrderer.clear();

        Move bestMove = iterativeSearch.search(gameCopy, difficulty.getSearchDepth());

        if (bestMove != null && random.nextDouble() < difficulty.getMistakeProbability())
            bestMove = mistakeGenerator.makeDeliberateMistake(gameCopy, bestMove);

        if (bestMove == null)
            throw new IllegalStateException("No legal moves found in a position");

        return convertMoveToOriginalGame(bestMove, game);
    }

    private Move convertMoveToOriginalGame(Move moveFromCopy, Game originalGame) {
        return new Move(
                originalGame.getBoard().getBox(moveFromCopy.start.getX(), moveFromCopy.start.getY()),
                originalGame.getBoard().getBox(moveFromCopy.end.getX(), moveFromCopy.end.getY()),
                originalGame.getBoard().getBox(moveFromCopy.start.getX(), moveFromCopy.start.getY()).getPiece(),
                originalGame.getBoard().getBox(moveFromCopy.end.getX(), moveFromCopy.end.getY()).getPiece(),
                moveFromCopy.wasCastlingMove,
                moveFromCopy.wasFirstMoveForPiece,
                originalGame.getBoard().getEnPassantTargetSquare(),
                0, 0, 0
        );
    }
}
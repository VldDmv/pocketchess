package org.pocketchess.core.ai;

import org.pocketchess.core.ai.difficulty.AIDifficulty;
import org.pocketchess.core.ai.difficulty.EvaluationParameters;
import org.pocketchess.core.ai.difficulty.MistakeGenerator;
import org.pocketchess.core.ai.evaluation.IterativeDeepeningSearch;
import org.pocketchess.core.ai.evaluation.NegamaxEngine;
import org.pocketchess.core.ai.evaluation.PositionEvaluator;
import org.pocketchess.core.ai.evaluation.QuiescenceSearch;
import org.pocketchess.core.game.moveanalyze.Move;
import org.pocketchess.core.general.Game;

import java.util.Random;

/**
 * The main class of the chess AI is the coordinator of all components.
 * Architecture:
 * 1. OpeningBook - opening theory
 * 2. IterativeDeepening - iterative search deepening
 * 3. NegamaxEngine - search for the best move
 * 4. PositionEvaluator - position evaluation
 * 5. MistakeGenerator - simulating human error
 */
public class AIPlayer {
    private final AIDifficulty difficulty;
    private final TranspositionTable transpositionTable;
    private final FastMoveGenerator moveGenerator;
    private final Random random;

    // Evaluation components
    private final PositionEvaluator positionEvaluator;
    private final QuiescenceSearch quiescenceSearch;

    // Search components
    private final NegamaxEngine negamaxEngine;
    private final IterativeDeepeningSearch iterativeSearch;
    private final MoveOrderer moveOrderer;
    private final OpeningBookManager openingBook;
    private final MistakeGenerator mistakeGenerator;

    /**
     * Creates an AI with the given rating parameters and difficulty level.
     */
    public AIPlayer(EvaluationParameters params, AIDifficulty difficulty) {
        this.difficulty = difficulty;
        this.random = new Random();
        this.transpositionTable = new TranspositionTable();
        this.moveGenerator = new FastMoveGenerator();

        // Initialization of evaluation components
        this.positionEvaluator = new PositionEvaluator(params, moveGenerator, difficulty);
        this.quiescenceSearch = new QuiescenceSearch(moveGenerator, positionEvaluator);

        // Initialize search components
        this.moveOrderer = new MoveOrderer(positionEvaluator);
        this.negamaxEngine = new NegamaxEngine(
                transpositionTable,
                moveGenerator,
                quiescenceSearch,
                positionEvaluator,
                moveOrderer,
                difficulty
        );
        this.iterativeSearch = new IterativeDeepeningSearch(negamaxEngine, moveGenerator);
        this.openingBook = new OpeningBookManager(difficulty, random);
        this.mistakeGenerator = new MistakeGenerator(negamaxEngine, moveGenerator, difficulty, random);
    }

    /**
     * Finds and returns the best move for the current position.
     * Decision algorithm:
     * 1. Check the opening book (if not EASY)
     * 2. If there is no opening, perform iterative deepening
     * 3. Make a deliberate mistake with a certain probability
     * 4. Convert the move back to the original game
     */
    public Move findBestMove(Game game) {
        if (!game.isLive()) return null;

        // Working with a copy for safety
        Game gameCopy = new Game(game);

        // Checking the debut book
        Move bookMove = openingBook.getBookMove(gameCopy);
        if (bookMove != null) {
            return convertMoveToOriginalGame(bookMove, game);
        }

        // Clear caches for a fresh search
        transpositionTable.clear();
        positionEvaluator.clearCache();

        //  Perform iterative deepening to a given depth
        Move bestMove = iterativeSearch.search(gameCopy, difficulty.getSearchDepth());

        // 4. Apply error rates
        // EASY: 30% error rate, MEDIUM: 15%, HARD: 0%
        if (bestMove != null && random.nextDouble() < difficulty.getMistakeProbability()) {
            bestMove = mistakeGenerator.makeDeliberateMistake(gameCopy, bestMove);
        }

        assert bestMove != null : "No legal moves found!";
        return convertMoveToOriginalGame(bestMove, game);
    }

    /**
     * Converts a move from a copy of the game back to the original game.
     */
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
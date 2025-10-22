package org.pocketchess.core.ai.evaluation;

import org.pocketchess.core.ai.FastMoveGenerator;
import org.pocketchess.core.ai.difficulty.AIDifficulty;
import org.pocketchess.core.ai.difficulty.EvaluationParameters;
import org.pocketchess.core.game.GameStatus;
import org.pocketchess.core.general.Game;
import org.pocketchess.core.pieces.*;

/**
 * Main evaluator of chess positions.
 * Uses different sets of functions depending on the difficulty level:
 * EASY: only material + positional tables
 * MEDIUM: full evaluation without advanced concepts
 * HARD: all possible factors
 */
public class PositionEvaluator {
    private final EvaluationParameters params;
    private final FastMoveGenerator moveGenerator;
    private final StrategicEvaluator strategicEval;
    private final AdvancedEvaluator advancedEval;
    private final AIDifficulty difficulty;
    private Integer cachedMobilityScore = null;
    private final PieceSquareTables pieceSquareTables;

    public PositionEvaluator(EvaluationParameters params, FastMoveGenerator moveGenerator, AIDifficulty difficulty) {
        this.params = params;
        this.moveGenerator = moveGenerator;
        this.difficulty = difficulty;
        this.strategicEval = new StrategicEvaluator(params);
        this.advancedEval = new AdvancedEvaluator(params);
        this.pieceSquareTables = new PieceSquareTables();
    }

    /**
     * Main position evaluation function.
     * Returns the numerical evaluation of the position
     * +300 = White's advantage is 3 pawns
     * -9500 = Black's advantage is 95 pawns
     * ±100000 = checkmate
     */
    public int evaluate(Game game) {
        GameStatus status = game.getStatus();


        if (status == GameStatus.WHITE_WIN || status == GameStatus.WHITE_WINS_BY_RESIGNATION) {
            return 100000;
        }
        if (status == GameStatus.BLACK_WIN || status == GameStatus.BLACK_WINS_BY_RESIGNATION) {
            return -100000;
        }
        if (game.isGameOver()) return 0;

        // EASY: simplified valuation (material + item only)
        if (difficulty.isSimplifiedEvaluation()) {
            return getSimplifiedEvaluation(game);
        }

        // MEDIUM & HARD: полная оценка
        int score = 0;

        // Basic components (for all levels above EASY)
        score += getMaterialScore(game);
        score += getPositionalScore(game);
        score += getMobilityScore(game);


        score += strategicEval.getKingSafetyScore(game);
        score += strategicEval.getStrategicBonuses(game);
        score += strategicEval.getPassedPawnBonus(game);
        score += strategicEval.evaluatePawnStructure(game);

        // Advanced components (HARD only)
        if (difficulty == AIDifficulty.HARD) {
            score += advancedEval.getAdvancedPawnStructureScore(game);
            score += advancedEval.getCenterControlScore(game);
            score += advancedEval.getPieceCoordinationScore(game);
        }

        boolean isEndGame = isEndGame(game);
        if (isEndGame) {
            score += strategicEval.getPassedPawnBonus(game) / 2;
        } else if (difficulty == AIDifficulty.HARD) {
            score += advancedEval.getCenterControlScore(game) / 3;
        }

        return score;
    }

    /**
     * Simplified assessment for EASY level
     */
    private int getSimplifiedEvaluation(Game game) {
        int score = 0;
        boolean isEndGame = isEndGame(game);

        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece piece = game.getBoard().getBox(r, c).getPiece();
                if (piece != null) {
                    int pieceValue = getPieceValue(piece);
                    int positionalValue = pieceSquareTables.getScore(piece, r, c, isEndGame);

                    if (piece.isWhite()) {
                        score += pieceValue + positionalValue;
                    } else {
                        score -= pieceValue + positionalValue;
                    }
                }
            }
        }

        return score;
    }

    /**
     * Calculates material balance
     */
    private int getMaterialScore(Game game) {
        int score = 0;
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece piece = game.getBoard().getBox(r, c).getPiece();
                if (piece != null) {
                    if (piece.isWhite()) {
                        score += getPieceValue(piece);
                    } else {
                        score -= getPieceValue(piece);
                    }
                }
            }
        }
        return score;
    }

    /**
     * Returns the contempt score - the draw score.
     * The contempt factor forces the AI to play for a win when ahead,
     * and to settle for a draw when the position is bad
     */
    public int getContemptScore(Game game) {
        int materialScore = getMaterialScore(game);
        int perspective = game.isWhiteTurn() ? 1 : -1;
        int contempt = (materialScore * perspective) / 5;
        return Math.max(-params.pawnValue, Math.min(params.pawnValue, contempt));
    }

    /**
     * Evaluates the positional placement of pieces.
     * Uses piece-square tables - tables that show
     * how well a piece fits on each square.
     */
    private int getPositionalScore(Game game) {
        int score = 0;
        boolean isEndGame = isEndGame(game);

        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece piece = game.getBoard().getBox(r, c).getPiece();
                if (piece != null) {
                    if (piece.isWhite()) {
                        score += pieceSquareTables.getScore(piece, r, c, isEndGame);
                    } else {
                        score -= pieceSquareTables.getScore(piece, r, c, isEndGame);
                    }
                }
            }
        }
        return score;
    }

    /**
     * Evaluates mobility (number of available moves).
     */
    private int getMobilityScore(Game game) {
        if (cachedMobilityScore != null) return cachedMobilityScore;


        int currentPlayerMoves = moveGenerator.generateMoves(game).size();

        game.makeNullMove();
        int opponentMoves = moveGenerator.generateMoves(game).size();
        game.undoNullMove();

        int mobilityScore = currentPlayerMoves - opponentMoves;

        if (!game.isWhiteTurn()) {
            mobilityScore = -mobilityScore;
        }

        cachedMobilityScore = params.mobilityWeight * mobilityScore;
        return cachedMobilityScore;
    }

    /**
     * Determines the phase of the game (mid-game or endgame).
     * The endgame occurs if:
     * - There are no queens on the board
     * - OR the total piece value is < 4000
     * In the endgame, priorities change: the king becomes active,
     * Passed pawns are critical.
     */
    private boolean isEndGame(Game game) {
        int totalMaterial = 0;
        int queenCount = 0;

        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece p = game.getBoard().getBox(r, c).getPiece();
                if (p != null && !(p instanceof King)) {
                    totalMaterial += getPieceValue(p);
                    if (p instanceof Queen) {
                        queenCount++;
                    }
                }
            }
        }

        return queenCount == 0 || totalMaterial < 4000;
    }

    /**
     Returns the cost of the shape from the parameters
     */
    public int getPieceValue(Piece piece) {
        if (piece instanceof Pawn) return params.pawnValue;
        if (piece instanceof Knight) return params.knightValue;
        if (piece instanceof Bishop) return params.bishopValue;
        if (piece instanceof Rook) return params.rookValue;
        if (piece instanceof Queen) return params.queenValue;
        if (piece instanceof King) return 20000;
        return 0;
    }

    /**
     * Clears the mobility cache.
     */
    public void clearCache() {
        cachedMobilityScore = null;
    }
}
package org.pocketchess.core.ai.evaluation;

import org.pocketchess.core.ai.search.FastMoveGenerator;
import org.pocketchess.core.ai.difficulty.AIDifficulty;
import org.pocketchess.core.ai.difficulty.EvaluationParameters;
import org.pocketchess.core.game.model.GameStatus;
import org.pocketchess.core.general.Game;
import org.pocketchess.core.gamemode.LavaManager;
import org.pocketchess.core.pieces.*;

/**
 * Main evaluator of chess positions.
 *
 * Lava-mode additions:
 *  - Pieces on WARNING squares receive a heavy penalty  (~80 % of their value)
 *    because they will be destroyed when lava activates.
 *  - Enemy pieces on WARNING squares give a corresponding bonus.
 *  - Pieces somehow still on active LAVA squares are penalised at full value
 *    (they are effectively dead – in a real game applyLavaEffect already
 *    removed them, but AI search copies may see them transiently).
 *
 * Scale constants are tuned so the AI eagerly moves pieces off warning squares
 * without over-weighting the lava factor versus normal chess evaluation.
 */
public class PositionEvaluator {
    private final EvaluationParameters params;
    private final FastMoveGenerator moveGenerator;
    private final StrategicEvaluator strategicEval;
    private final AdvancedEvaluator advancedEval;
    private final AIDifficulty difficulty;
    private Integer cachedMobilityScore = null;
    private final PieceSquareTables pieceSquareTables;

    // ── Lava evaluation weights ───────────────────────────────────────────────

    /**
     * Fraction of piece value lost for standing on a WARNING square.
     *
     * 100 % – the piece is treated as already dead.
     *
     * Why 100 % and not less:
     *  The AI game copy does NOT simulate lava transitions during search.
     *  This means the AI cannot "see" that on the exact half-move that
     *  triggers the lava interval (totalHalfMoves % LAVA_INTERVAL == 0)
     *  the warning square fires and the piece is destroyed.  Using a
     *  fraction < 100 % lets the AI profitably capture on warning squares
     *  right before the interval fires (gain piece, lose only 60-80 %).
     *  At 100 % the trade is break-even at best, so the AI avoids it.
     */
    private static final int WARNING_PENALTY_NUM   = 1;
    private static final int WARNING_PENALTY_DEN   = 1;

    /**
     * Fraction of piece value gained for an ENEMY piece on a warning square.
     * Also 100 % – symmetric with own-piece penalty so that capturing an
     * enemy piece that is already "condemned" yields zero net gain.
     */
    private static final int WARNING_BONUS_NUM     = 1;
    private static final int WARNING_BONUS_DEN     = 1;

    public PositionEvaluator(EvaluationParameters params,
                             FastMoveGenerator moveGenerator,
                             AIDifficulty difficulty) {
        this.params         = params;
        this.moveGenerator  = moveGenerator;
        this.difficulty     = difficulty;
        this.strategicEval  = new StrategicEvaluator(params);
        this.advancedEval   = new AdvancedEvaluator(params);
        this.pieceSquareTables = new PieceSquareTables();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Main evaluation
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns the numerical evaluation of the position from White's perspective.
     * +300 = White advantage of 3 pawns, −9500 = Black near-winning, ±100000 = mate.
     */
    public int evaluate(Game game) {
        GameStatus status = game.getStatus();

        if (status == GameStatus.WHITE_WIN || status == GameStatus.WHITE_WINS_BY_RESIGNATION)
            return 100000;
        if (status == GameStatus.BLACK_WIN || status == GameStatus.BLACK_WINS_BY_RESIGNATION)
            return -100000;
        if (game.isGameOver()) return 0;

        // EASY: simplified valuation (material + piece-square tables only)
        if (difficulty.isSimplifiedEvaluation()) {
            int s = getSimplifiedEvaluation(game);
            if (game.isLavaMode()) s += getLavaAwarenessScore(game);
            return s;
        }

        // MEDIUM & HARD: full evaluation
        int score = 0;
        score += getMaterialScore(game);
        score += getPositionalScore(game);
        score += getMobilityScore(game);
        score += strategicEval.getKingSafetyScore(game);
        score += strategicEval.getStrategicBonuses(game);
        score += strategicEval.getPassedPawnBonus(game);
        score += strategicEval.evaluatePawnStructure(game);

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

        // ── Lava awareness (all non-EASY levels) ────────────────────────────
        if (game.isLavaMode()) {
            score += getLavaAwarenessScore(game);
        }

        return score;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Lava evaluation
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Evaluates lava-specific positional factors.
     * Warning squares (blue) – will become lava on the next interval:
     * Own piece on warning  → penalty  ≈ −80 % of piece value
     * Enemy piece on warning → bonus    ≈ +60 % of piece value
     * Active lava squares (red) – in a real game the piece is already
     * removed; in AI search copies it may still be present transiently:
     Any piece on active lava → full piece-value penalty/bonus
     * Kings are ignored (they trigger game-end logic elsewhere).
     */
    private int getLavaAwarenessScore(Game game) {
        LavaManager lm = game.getLavaManager();
        if (!lm.isEnabled()) return 0;

        int score = 0;

        // ── Warning squares ──────────────────────────────────────────────────
        for (int encoded : lm.getWarningSquares()) {
            int[] pos   = LavaManager.decode(encoded);
            Piece piece = game.getBoard().getBox(pos[0], pos[1]).getPiece();
            if (piece == null || piece instanceof King) continue;

            int val = getPieceValue(piece);

            if (piece.isWhite()) {
                // White piece in danger → bad for White
                score -= val * WARNING_PENALTY_NUM / WARNING_PENALTY_DEN;
            } else {
                // Black piece in danger → good for White
                score += val * WARNING_BONUS_NUM / WARNING_BONUS_DEN;
            }
        }

        // ── Active lava squares ──────────────────────────────────────────────
        // Normally empty after applyLavaEffect; guard against AI-copy edge cases.
        for (int encoded : lm.getLavaSquares()) {
            int[] pos   = LavaManager.decode(encoded);
            Piece piece = game.getBoard().getBox(pos[0], pos[1]).getPiece();
            if (piece == null || piece instanceof King) continue;

            int val = getPieceValue(piece);
            // Full value – the piece is as good as dead
            score += piece.isWhite() ? -val : val;
        }

        return score;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Standard evaluation helpers (unchanged)
    // ─────────────────────────────────────────────────────────────────────────

    private int getSimplifiedEvaluation(Game game) {
        int score = 0;
        boolean isEndGame = isEndGame(game);
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece piece = game.getBoard().getBox(r, c).getPiece();
                if (piece != null) {
                    int pieceValue     = getPieceValue(piece);
                    int positionalValue = pieceSquareTables.getScore(piece, r, c, isEndGame);
                    if (piece.isWhite()) score += pieceValue + positionalValue;
                    else                 score -= pieceValue + positionalValue;
                }
            }
        }
        return score;
    }

    private int getMaterialScore(Game game) {
        int score = 0;
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece piece = game.getBoard().getBox(r, c).getPiece();
                if (piece != null) {
                    if (piece.isWhite()) score += getPieceValue(piece);
                    else                 score -= getPieceValue(piece);
                }
            }
        }
        return score;
    }

    public int getContemptScore(Game game) {
        int materialScore = getMaterialScore(game);
        int perspective   = game.isWhiteTurn() ? 1 : -1;
        int contempt      = (materialScore * perspective) / 5;
        return Math.max(-params.pawnValue, Math.min(params.pawnValue, contempt));
    }

    private int getPositionalScore(Game game) {
        int score = 0;
        boolean isEndGame = isEndGame(game);
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece piece = game.getBoard().getBox(r, c).getPiece();
                if (piece != null) {
                    if (piece.isWhite()) score += pieceSquareTables.getScore(piece, r, c, isEndGame);
                    else                 score -= pieceSquareTables.getScore(piece, r, c, isEndGame);
                }
            }
        }
        return score;
    }

    private int getMobilityScore(Game game) {
        if (cachedMobilityScore != null) return cachedMobilityScore;

        int currentPlayerMoves = moveGenerator.generateMoves(game).size();
        game.makeNullMove();
        int opponentMoves = moveGenerator.generateMoves(game).size();
        game.undoNullMove();

        int mobilityScore = currentPlayerMoves - opponentMoves;
        if (!game.isWhiteTurn()) mobilityScore = -mobilityScore;

        cachedMobilityScore = params.mobilityWeight * mobilityScore;
        return cachedMobilityScore;
    }

    private boolean isEndGame(Game game) {
        int totalMaterial = 0;
        int queenCount    = 0;
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece p = game.getBoard().getBox(r, c).getPiece();
                if (p != null && !(p instanceof King)) {
                    totalMaterial += getPieceValue(p);
                    if (p instanceof Queen) queenCount++;
                }
            }
        }
        return queenCount == 0 || totalMaterial < 4000;
    }

    public int getPieceValue(Piece piece) {
        if (piece instanceof Pawn)   return params.pawnValue;
        if (piece instanceof Knight) return params.knightValue;
        if (piece instanceof Bishop) return params.bishopValue;
        if (piece instanceof Rook)   return params.rookValue;
        if (piece instanceof Queen)  return params.queenValue;
        if (piece instanceof King)   return 20000;
        return 0;
    }

    public void clearCache() {
        cachedMobilityScore = null;
    }
}
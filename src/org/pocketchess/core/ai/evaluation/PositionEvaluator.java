package org.pocketchess.core.ai.evaluation;

import org.pocketchess.core.ai.search.FastMoveGenerator;
import org.pocketchess.core.ai.difficulty.AIDifficulty;
import org.pocketchess.core.ai.difficulty.EvaluationParameters;
import org.pocketchess.core.game.model.GameStatus;
import org.pocketchess.core.general.Game;
import org.pocketchess.core.gamemode.LavaManager;
import org.pocketchess.core.pieces.*;

/**
 * Main position evaluator.
 *
 * isEndGame() threshold fix:
 *   Was: queenCount == 0 OR totalMaterial < 4000
 *   Now: queenCount == 0 AND totalMaterial < 3000
 *
 *   The old OR condition meant "endgame" triggered even when queens were
 *   gone but rooks/bishops/knights remained (material ~4500+ cp) — exactly
 *   the complex positions shown in the screenshots.  The new AND + lower
 *   threshold fires only in genuine endgames.
 *
 *   3000 cp ≈ two rooks + bishop + several pawns — still a rook endgame,
 *   but without queens and heavy pieces the king-safety tables should
 *   switch to the endgame variant.
 */
public class PositionEvaluator {
    private final EvaluationParameters params;
    private final FastMoveGenerator moveGenerator;
    private final StrategicEvaluator strategicEval;
    private final AdvancedEvaluator advancedEval;
    private final AIDifficulty difficulty;
    private Integer cachedMobilityScore = null;
    private final PieceSquareTables pieceSquareTables;

    // ── Lava ─────────────────────────────────────────────────────────────────
    private static final int WARNING_PENALTY_NUM = 1;
    private static final int WARNING_PENALTY_DEN = 1;
    private static final int WARNING_BONUS_NUM   = 1;
    private static final int WARNING_BONUS_DEN   = 1;

    // ── Contempt ──────────────────────────────────────────────────────────────
    private static final int CONTEMPT_DIVISOR = 2;
    private static final int CONTEMPT_MAX_CP  = 500;

    // ── Hanging piece ─────────────────────────────────────────────────────────
    private static final double HANGING_UNDEFENDED_FACTOR = 0.80;
    private static final double HANGING_DEFENDED_FACTOR   = 0.20;

    // ── Endgame threshold ─────────────────────────────────────────────────────
    /**
     * Total non-king material (both sides) below which the king switches
     * to endgame piece-square tables.
     * 3000 cp: no queens, roughly 2 rooks + minor piece + pawns per side.
     */
    private static final int ENDGAME_MATERIAL_THRESHOLD = 3000;

    public PositionEvaluator(EvaluationParameters params,
                             FastMoveGenerator moveGenerator,
                             AIDifficulty difficulty) {
        this.params            = params;
        this.moveGenerator     = moveGenerator;
        this.difficulty        = difficulty;
        this.strategicEval     = new StrategicEvaluator(params);
        this.advancedEval      = new AdvancedEvaluator(params);
        this.pieceSquareTables = new PieceSquareTables();
    }

    // ── Main ─────────────────────────────────────────────────────────────────

    public int evaluate(Game game) {
        GameStatus status = game.getStatus();

        if (status == GameStatus.WHITE_WIN || status == GameStatus.WHITE_WINS_BY_RESIGNATION)
            return 100_000;
        if (status == GameStatus.BLACK_WIN || status == GameStatus.BLACK_WINS_BY_RESIGNATION)
            return -100_000;
        if (game.isGameOver()) return 0;

        if (difficulty.isSimplifiedEvaluation()) {
            int s = getSimplifiedEvaluation(game);
            if (game.isLavaMode()) s += getLavaAwarenessScore(game);
            return s;
        }

        int score = 0;
        score += getMaterialScore(game);
        score += getPositionalScore(game);
        score += getMobilityScore(game);
        score += strategicEval.getKingSafetyScore(game);
        score += strategicEval.getStrategicBonuses(game);
        score += strategicEval.getPassedPawnBonus(game);
        score += strategicEval.evaluatePawnStructure(game);
        score += getHangingPieceScore(game);

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

        if (game.isLavaMode()) score += getLavaAwarenessScore(game);

        return score;
    }

    // ── Endgame detection ─────────────────────────────────────────────────────

    /**
     * Returns true only when queens are gone AND total material is low.
     * Used for king piece-square table selection and passed-pawn bonus.
     *
     * Condition: NO queens on board AND total non-king material < 3000 cp.
     * Previous (buggy): queenCount == 0 OR totalMaterial < 4000
     *   → triggered too early in positions with rooks/minors still active.
     */
    public boolean isEndGame(Game game) {
        int total = 0;
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece p = game.getBoard().getBox(r, c).getPiece();
                if (p == null || p instanceof King) continue;
                if (p instanceof Queen) return false; // queen present → middlegame
                total += getPieceValue(p);
            }
        }
        return total < ENDGAME_MATERIAL_THRESHOLD;
    }

    // ── Hanging piece detection ───────────────────────────────────────────────

    private int getHangingPieceScore(Game game) {
        int score = 0;
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece piece = game.getBoard().getBox(r, c).getPiece();
                if (piece == null || piece instanceof Pawn || piece instanceof King) continue;

                int pieceVal = getPieceValue(piece);
                boolean isWhite = piece.isWhite();

                int cheapestAttacker = getCheapestAttackerValue(game, r, c, !isWhite);
                if (cheapestAttacker == Integer.MAX_VALUE) continue;
                if (cheapestAttacker >= pieceVal) continue;

                boolean defended = hasFriendlyDefender(game, r, c, isWhite);
                int penalty = defended
                        ? (int)(pieceVal * HANGING_DEFENDED_FACTOR)
                        : (int)(pieceVal * HANGING_UNDEFENDED_FACTOR);

                score += isWhite ? -penalty : penalty;
            }
        }
        return score;
    }

    private int getCheapestAttackerValue(Game game, int r, int c, boolean attackerIsWhite) {
        org.pocketchess.core.general.Board board = game.getBoard();

        // Pawns
        int pawnDir = attackerIsWhite ? 1 : -1;
        int pr = r + pawnDir;
        if (pr >= 0 && pr < 8) {
            if (c > 0) {
                Piece p = board.getBox(pr, c - 1).getPiece();
                if (p instanceof Pawn && p.isWhite() == attackerIsWhite) return params.pawnValue;
            }
            if (c < 7) {
                Piece p = board.getBox(pr, c + 1).getPiece();
                if (p instanceof Pawn && p.isWhite() == attackerIsWhite) return params.pawnValue;
            }
        }

        // Knights
        int[][] knightDelta = {{-2,-1},{-2,1},{-1,-2},{-1,2},{1,-2},{1,2},{2,-1},{2,1}};
        for (int[] d : knightDelta) {
            int nr = r + d[0], nc = c + d[1];
            if (nr < 0 || nr > 7 || nc < 0 || nc > 7) continue;
            Piece p = board.getBox(nr, nc).getPiece();
            if (p instanceof Knight && p.isWhite() == attackerIsWhite) return params.knightValue;
        }

        // Bishops / diagonal Queens
        int[][] diagDirs = {{-1,-1},{-1,1},{1,-1},{1,1}};
        for (int[] dir : diagDirs) {
            int nr = r + dir[0], nc = c + dir[1];
            while (nr >= 0 && nr < 8 && nc >= 0 && nc < 8) {
                Piece p = board.getBox(nr, nc).getPiece();
                if (p != null) {
                    if (p.isWhite() == attackerIsWhite) {
                        if (p instanceof Bishop) return params.bishopValue;
                        if (p instanceof Queen)  return params.queenValue;
                    }
                    break;
                }
                nr += dir[0]; nc += dir[1];
            }
        }

        // Rooks / straight Queens
        int[][] straightDirs = {{-1,0},{1,0},{0,-1},{0,1}};
        for (int[] dir : straightDirs) {
            int nr = r + dir[0], nc = c + dir[1];
            while (nr >= 0 && nr < 8 && nc >= 0 && nc < 8) {
                Piece p = board.getBox(nr, nc).getPiece();
                if (p != null) {
                    if (p.isWhite() == attackerIsWhite) {
                        if (p instanceof Rook)  return params.rookValue;
                        if (p instanceof Queen) return params.queenValue;
                    }
                    break;
                }
                nr += dir[0]; nc += dir[1];
            }
        }

        // King
        int[][] kingDelta = {{-1,-1},{-1,0},{-1,1},{0,-1},{0,1},{1,-1},{1,0},{1,1}};
        for (int[] d : kingDelta) {
            int nr = r + d[0], nc = c + d[1];
            if (nr < 0 || nr > 7 || nc < 0 || nc > 7) continue;
            Piece p = board.getBox(nr, nc).getPiece();
            if (p instanceof King && p.isWhite() == attackerIsWhite) return 20_000;
        }

        return Integer.MAX_VALUE;
    }

    private boolean hasFriendlyDefender(Game game, int r, int c, boolean defenderIsWhite) {
        org.pocketchess.core.general.Board board = game.getBoard();
        Piece original = board.getBox(r, c).getPiece();
        board.getBox(r, c).setPiece(null);
        boolean found = (getCheapestAttackerValue(game, r, c, defenderIsWhite) != Integer.MAX_VALUE);
        board.getBox(r, c).setPiece(original);
        return found;
    }

    // ── Contempt ─────────────────────────────────────────────────────────────

    public int getContemptScore(Game game) {
        int materialScore = getMaterialScore(game);
        int perspective   = game.isWhiteTurn() ? 1 : -1;
        int contempt      = (materialScore * perspective) / CONTEMPT_DIVISOR;
        return Math.max(-CONTEMPT_MAX_CP, Math.min(CONTEMPT_MAX_CP, contempt));
    }

    // ── Lava ─────────────────────────────────────────────────────────────────

    private int getLavaAwarenessScore(Game game) {
        LavaManager lm = game.getLavaManager();
        if (!lm.isEnabled()) return 0;
        int score = 0;
        for (int encoded : lm.getWarningSquares()) {
            int[] pos   = LavaManager.decode(encoded);
            Piece piece = game.getBoard().getBox(pos[0], pos[1]).getPiece();
            if (piece == null || piece instanceof King) continue;
            int val = getPieceValue(piece);
            if (piece.isWhite()) score -= val * WARNING_PENALTY_NUM / WARNING_PENALTY_DEN;
            else                 score += val * WARNING_BONUS_NUM   / WARNING_BONUS_DEN;
        }
        for (int encoded : lm.getLavaSquares()) {
            int[] pos   = LavaManager.decode(encoded);
            Piece piece = game.getBoard().getBox(pos[0], pos[1]).getPiece();
            if (piece == null || piece instanceof King) continue;
            int val = getPieceValue(piece);
            score += piece.isWhite() ? -val : val;
        }
        return score;
    }

    // ── Standard helpers ──────────────────────────────────────────────────────

    private int getSimplifiedEvaluation(Game game) {
        int score = 0;
        boolean eg = isEndGame(game);
        for (int r = 0; r < 8; r++)
            for (int c = 0; c < 8; c++) {
                Piece piece = game.getBoard().getBox(r, c).getPiece();
                if (piece != null) {
                    int v = getPieceValue(piece) + pieceSquareTables.getScore(piece, r, c, eg);
                    score += piece.isWhite() ? v : -v;
                }
            }
        return score;
    }

    public int getMaterialScore(Game game) {
        int score = 0;
        for (int r = 0; r < 8; r++)
            for (int c = 0; c < 8; c++) {
                Piece piece = game.getBoard().getBox(r, c).getPiece();
                if (piece != null)
                    score += piece.isWhite() ? getPieceValue(piece) : -getPieceValue(piece);
            }
        return score;
    }

    private int getPositionalScore(Game game) {
        int score = 0;
        boolean eg = isEndGame(game);
        for (int r = 0; r < 8; r++)
            for (int c = 0; c < 8; c++) {
                Piece piece = game.getBoard().getBox(r, c).getPiece();
                if (piece != null) {
                    int v = pieceSquareTables.getScore(piece, r, c, eg);
                    score += piece.isWhite() ? v : -v;
                }
            }
        return score;
    }

    private int getMobilityScore(Game game) {
        if (cachedMobilityScore != null) return cachedMobilityScore;
        int cur = moveGenerator.generateMoves(game).size();
        game.makeNullMove();
        int opp = moveGenerator.generateMoves(game).size();
        game.undoNullMove();
        int mob = game.isWhiteTurn() ? cur - opp : opp - cur;
        cachedMobilityScore = params.mobilityWeight * mob;
        return cachedMobilityScore;
    }

    public int getPieceValue(Piece piece) {
        if (piece instanceof Pawn)   return params.pawnValue;
        if (piece instanceof Knight) return params.knightValue;
        if (piece instanceof Bishop) return params.bishopValue;
        if (piece instanceof Rook)   return params.rookValue;
        if (piece instanceof Queen)  return params.queenValue;
        if (piece instanceof King)   return 20_000;
        return 0;
    }

    public void clearCache() { cachedMobilityScore = null; }
}
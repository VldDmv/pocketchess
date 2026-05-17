package org.pocketchess.online.engine;

import org.pocketchess.core.ai.difficulty.AIDifficulty;
import org.pocketchess.core.ai.difficulty.EvaluationParameters;
import org.pocketchess.core.ai.search.AIPlayer;
import org.pocketchess.core.game.model.GameMode;
import org.pocketchess.core.game.model.GameStatus;
import org.pocketchess.core.game.model.TimeControl;
import org.pocketchess.core.game.moveanalyze.Move;
import org.pocketchess.core.game.utils.FenUtils;
import org.pocketchess.core.gamemode.GameModeType;
import org.pocketchess.core.general.Game;
import org.pocketchess.core.pieces.Bishop;
import org.pocketchess.core.pieces.Knight;
import org.pocketchess.core.pieces.Piece;
import org.pocketchess.core.pieces.Queen;
import org.pocketchess.core.pieces.Rook;

/**
 * Thin, headless wrapper around {@link Game} used by the online server.
 *
 * <p>The underlying {@link Game} is always configured in {@link GameMode#PVP},
 * even when playing against the engine — the adapter triggers AI moves
 * explicitly via {@link #requestAiMove()} instead of letting the desktop
 * code path schedule a Swing {@code Timer}. This keeps the server free of
 * AWT/EDT activity.
 */
public class ChessEngineAdapter {

    private final Game game;
    private final AIDifficulty aiDifficulty;

    private ChessEngineAdapter(Game game, AIDifficulty aiDifficulty) {
        this.game = game;
        this.aiDifficulty = aiDifficulty;
    }

    public static ChessEngineAdapter newClassicGame(TimeControl tc, AIDifficulty aiDifficulty) {
        return newGame(tc, aiDifficulty, GameModeType.CLASSIC);
    }

    public static ChessEngineAdapter newGame(TimeControl tc, AIDifficulty aiDifficulty,
                                             GameModeType variant) {
        Game g = new Game();
        g.resetGame(tc, GameMode.PVP, Piece.Color.WHITE, aiDifficulty, variant);
        return new ChessEngineAdapter(g, aiDifficulty);
    }

    public String fen() {
        return FenUtils.generateFEN(game.getBoard(), game.isWhiteTurn());
    }

    public boolean isWhiteTurn() {
        return game.isWhiteTurn();
    }

    public GameStatus status() {
        return game.getStatus();
    }

    public boolean isGameOver() {
        return game.isGameOver();
    }

    /**
     * Applies a player move expressed in UCI long-algebraic notation.
     *
     * <p>Promotion is detected from the suffix ({@code q r b n}); if the move
     * lands a pawn on the back rank without a suffix, the adapter defaults to
     * queen promotion — same convention the desktop AI uses.
     */
    public MoveResult applyMove(String uci) {
        UciMove m;
        try {
            m = UciMove.parse(uci);
        } catch (IllegalArgumentException e) {
            return MoveResult.reject(e.getMessage());
        }

        if (game.isGameOver()) {
            return MoveResult.reject("Game is over: " + game.getStatus());
        }

        boolean moverIsWhite = game.isWhiteTurn();
        boolean accepted = game.playerMove(m.fromRow(), m.fromCol(), m.toRow(), m.toCol());
        if (!accepted) {
            return MoveResult.reject("Illegal move: " + uci);
        }

        if (game.getStatus() == GameStatus.AWAITING_PROMOTION) {
            Piece promo = promotionPiece(m.promotion(), moverIsWhite);
            game.promotePawn(promo);
        }

        return MoveResult.ok(uci, fen(), game.getStatus(), game.isWhiteTurn());
    }

    /**
     * Runs the engine synchronously for the side to move and applies its choice.
     * The returned UCI string includes the promotion suffix when applicable.
     */
    public MoveResult requestAiMove() {
        if (game.isGameOver()) {
            return MoveResult.reject("Game is over: " + game.getStatus());
        }

        AIPlayer ai = new AIPlayer(new EvaluationParameters(), aiDifficulty);
        Move best = ai.findBestMove(game);
        if (best == null) {
            return MoveResult.reject("Engine returned no move");
        }

        boolean moverIsWhite = game.isWhiteTurn();
        boolean accepted = game.playerMove(
                best.start.getX(), best.start.getY(),
                best.end.getX(), best.end.getY());
        if (!accepted) {
            return MoveResult.reject("Engine produced illegal move: " + UciMove.fromMove(best));
        }

        Character promoSuffix = null;
        if (game.getStatus() == GameStatus.AWAITING_PROMOTION) {
            char suffix = best.promotedTo != null
                    ? pieceLetter(best.promotedTo) : 'q';
            game.promotePawn(promotionPiece(suffix, moverIsWhite));
            promoSuffix = suffix;
        }

        return MoveResult.ok(UciMove.fromMove(best, promoSuffix),
                fen(), game.getStatus(), game.isWhiteTurn());
    }

    private static Piece promotionPiece(Character suffix, boolean isWhite) {
        char c = suffix == null ? 'q' : Character.toLowerCase(suffix);
        return switch (c) {
            case 'q' -> new Queen(isWhite);
            case 'r' -> new Rook(isWhite);
            case 'b' -> new Bishop(isWhite);
            case 'n' -> new Knight(isWhite);
            default -> throw new IllegalArgumentException("Unknown promotion: " + suffix);
        };
    }

    private static char pieceLetter(Piece piece) {
        if (piece instanceof Queen) return 'q';
        if (piece instanceof Rook) return 'r';
        if (piece instanceof Bishop) return 'b';
        if (piece instanceof Knight) return 'n';
        return 'q';
    }
}

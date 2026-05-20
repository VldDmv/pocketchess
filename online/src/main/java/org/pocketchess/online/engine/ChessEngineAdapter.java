package org.pocketchess.online.engine;

import org.pocketchess.core.ai.difficulty.AIDifficulty;
import org.pocketchess.core.ai.difficulty.EvaluationParameters;
import org.pocketchess.core.ai.search.AIPlayer;
import org.pocketchess.core.ai.search.FastMoveGenerator;
import org.pocketchess.core.game.gamenotation.ChessNotationFormatter;
import org.pocketchess.core.game.gamenotation.PgnBuilder;
import org.pocketchess.core.game.model.GameMode;
import org.pocketchess.core.game.model.GameStatus;
import org.pocketchess.core.game.model.TimeControl;
import org.pocketchess.core.game.moveanalyze.Move;
import org.pocketchess.core.game.utils.FenUtils;
import org.pocketchess.core.gamemode.GameModeType;
import org.pocketchess.core.gamemode.LavaManager;
import org.pocketchess.core.general.Game;
import org.pocketchess.core.pieces.Bishop;
import org.pocketchess.core.pieces.King;
import org.pocketchess.core.pieces.Knight;
import org.pocketchess.core.pieces.Pawn;
import org.pocketchess.core.pieces.Piece;
import org.pocketchess.core.pieces.Queen;
import org.pocketchess.core.pieces.Rook;
import org.pocketchess.core.pieces.Spot;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
    private final ChessNotationFormatter notationFormatter;
    private final FastMoveGenerator moveGenerator = new FastMoveGenerator();

    private ChessEngineAdapter(Game game, AIDifficulty aiDifficulty) {
        this.game = game;
        this.aiDifficulty = aiDifficulty;
        this.notationFormatter = new ChessNotationFormatter(game);
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

    /** Black pieces white has captured, as FEN letters ("p", "n", "r"...). */
    public List<String> capturedByWhite() {
        return toFenLetters(game.getWhiteCapturedPieces());
    }

    /** White pieces black has captured, as FEN letters ("P", "N", "R"...). */
    public List<String> capturedByBlack() {
        return toFenLetters(game.getBlackCapturedPieces());
    }

    /** Reverts the most recent half-move. Returns false if there is nothing to undo. */
    public boolean undoLastHalfMove() {
        if (game.getMoveHistory().isEmpty()) return false;
        game.undoMove();
        return true;
    }

    public void resign() { game.resign(); }

    /**
     * Sets the resignation status from the specified side's point of view
     * (offline {@code Game.resign()} only knows how to resign whoever is on
     * move, so we flip the turn with a null move first when the resigner
     * isn't currently on the clock).
     */
    public void resignBy(boolean resignerIsWhite) {
        if (game.isWhiteTurn() != resignerIsWhite) {
            game.makeNullMove();
        }
        game.resign();
    }

    /**
     * Drives the engine into {@code DRAW_AGREED}. The offline {@code offerDraw()}
     * is a toggle that needs two invocations (offer + accept), so the session
     * layer calls this once when both players have signalled agreement.
     */
    public void acceptDraw() {
        if (!game.isDrawOffered()) game.offerDraw();
        game.offerDraw();
    }

    /** Marks the side-to-move as having lost on time. */
    public void flagFall() {
        // The engine's onTimeExpired sets the appropriate WIN_ON_TIME status.
        game.onTimeExpired(game.isWhiteTurn());
    }

    /** Full game history in Standard Algebraic Notation. */
    public List<String> sanHistory() {
        List<Move> history = game.getMoveHistory();
        List<String> out = new ArrayList<>(history.size());
        for (Move m : history) out.add(notationFormatter.getNotationForMove(m));
        return out;
    }

    /** Multi-line PGN with seven-tag roster plus the move list. */
    public String pgn(String whiteName, String blackName) {
        return PgnBuilder.build(game, notationFormatter, whiteName, blackName);
    }

    /** Squares currently on fire. Empty list when lava mode is off. */
    public List<String> lavaSquares() {
        return squaresFromLava(LavaManager::getLavaSquares);
    }

    /** Squares that will burn next turn. */
    public List<String> warningSquares() {
        return squaresFromLava(LavaManager::getWarningSquares);
    }

    private List<String> squaresFromLava(java.util.function.Function<LavaManager, Set<Integer>> getter) {
        if (!game.isLavaMode()) return List.of();
        Set<Integer> encoded = getter.apply(game.getLavaManager());
        List<String> out = new ArrayList<>(encoded.size());
        for (int e : encoded) out.add(toSquareName(e / 8, e % 8));
        return out;
    }

    /** Every legal half-move available to the side to move, in UCI form. */
    public List<String> legalMoves() {
        if (isGameOver()) return List.of();
        List<Move> moves = moveGenerator.generateMoves(game);
        List<String> out = new ArrayList<>(moves.size());
        boolean seenPromo = false;
        for (Move m : moves) {
            if (m.start == null || m.end == null) continue;
            boolean isPawnMove = m.pieceMoved instanceof Pawn;
            boolean toBackRank = m.end.getX() == 0 || m.end.getX() == 7;
            if (isPawnMove && toBackRank) {
                // Expand into four UCI strings so the client knows promotion is required.
                String base = uciOf(m);
                out.add(base + "q");
                out.add(base + "r");
                out.add(base + "b");
                out.add(base + "n");
                seenPromo = true;
            } else {
                out.add(uciOf(m));
            }
        }
        return out;
    }

    /** Square name of the king that is currently in check, or null if none. */
    public String kingInCheckSquare() {
        if (game.getStatus() != GameStatus.CHECK) return null;
        Spot kingSpot = game.findKing(game.isWhiteTurn());
        if (kingSpot == null) return null;
        return toSquareName(kingSpot.getX(), kingSpot.getY());
    }

    public boolean wasLastMoveCapture() {
        Move last = game.getLastMove();
        return last != null && last.pieceKilled != null;
    }

    public boolean wasLastMoveCastling() {
        Move last = game.getLastMove();
        return last != null && last.wasCastlingMove;
    }

    private static String uciOf(Move m) {
        return UciMove.fromMove(m);
    }

    /**
     * Parses {@code pgn} into a temporary game and replays each ply through
     * this adapter so the normal {@code applyMove} path populates the FEN
     * history, captured-piece bookkeeping and last-move metadata.
     */
    public List<MoveResult> loadFromPgn(String pgn) {
        Game temp = new Game();
        temp.resetGame(new org.pocketchess.core.game.model.TimeControl(5 * 60, 0),
                GameMode.PVP, Piece.Color.WHITE, aiDifficulty, GameModeType.CLASSIC);
        org.pocketchess.core.game.utils.PgnUtils.loadPgn(temp, pgn);

        List<String> uciMoves = new ArrayList<>(temp.getMoveHistory().size());
        for (Move m : temp.getMoveHistory()) {
            char promo = m.promotedTo != null ? pieceLetter(m.promotedTo) : 0;
            String uci = UciMove.fromMove(m, promo == 0 ? null : promo);
            uciMoves.add(uci);
        }

        List<MoveResult> out = new ArrayList<>(uciMoves.size());
        for (String uci : uciMoves) {
            MoveResult r = applyMove(uci);
            if (!r.accepted()) {
                throw new IllegalStateException(
                        "PGN replay rejected at " + uci + ": " + r.error());
            }
            out.add(r);
        }
        return out;
    }

    private static String toSquareName(int row, int col) {
        return "" + (char) ('a' + col) + (8 - row);
    }

    private static List<String> toFenLetters(List<Piece> pieces) {
        List<String> out = new ArrayList<>(pieces.size());
        for (Piece p : pieces) out.add(fenLetter(p));
        return out;
    }

    private static String fenLetter(Piece piece) {
        String letter;
        if (piece instanceof Pawn) letter = "p";
        else if (piece instanceof Knight) letter = "n";
        else if (piece instanceof Bishop) letter = "b";
        else if (piece instanceof Rook) letter = "r";
        else if (piece instanceof Queen) letter = "q";
        else if (piece instanceof King) letter = "k";
        else letter = "?";
        return piece.isWhite() ? letter.toUpperCase() : letter;
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

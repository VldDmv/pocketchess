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

    // Initial (pre-move) position captured during loadFromPgn, after the
    // variant / lava seed is applied. Lets the session seed its ply-0 history.
    private String initialFen;
    private List<String> initialLava = List.of();
    private List<String> initialWarning = List.of();

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
        boolean chess960 = game.getGameModeType() == GameModeType.CHESS960;
        List<Move> moves = moveGenerator.generateLegalMoves(game);   // exact rules: warnings passable
        List<String> out = new ArrayList<>(moves.size());
        for (Move m : moves) {
            if (m.start == null || m.end == null) continue;
            // Chess960 castling is expressed as "king takes rook" so the move
            // is unambiguous when the king starts on/next to its castled square.
            if (chess960 && m.wasCastlingMove && m.chess960RookFromCol >= 0) {
                out.add("" + (char) ('a' + m.start.getY()) + (8 - m.start.getX())
                          + (char) ('a' + m.chess960RookFromCol) + (8 - m.start.getX()));
                continue;
            }
            boolean isPawnMove = m.pieceMoved instanceof Pawn;
            boolean toBackRank = m.end.getX() == 0 || m.end.getX() == 7;
            if (isPawnMove && toBackRank) {
                // Expand into four UCI strings so the client knows promotion is required.
                String base = uciOf(m);
                out.add(base + "q");
                out.add(base + "r");
                out.add(base + "b");
                out.add(base + "n");
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
     *
     * <p>If the PGN declares {@code [Variant "Chess960"]} with a {@code [FEN ...]}
     * header, our own game is re-seeded to that exact back-rank so the moves
     * apply against the right initial position.
     */
    public List<MoveResult> loadFromPgn(String pgn) {
        Game temp = new Game();
        temp.resetGame(new org.pocketchess.core.game.model.TimeControl(5 * 60, 0),
                GameMode.PVP, Piece.Color.WHITE, aiDifficulty, GameModeType.CLASSIC);
        org.pocketchess.core.game.utils.PgnUtils.loadPgn(temp, pgn);

        if (temp.getGameModeType() == GameModeType.CHESS960) {
            org.pocketchess.core.general.Board snap = temp.getBoard().getInitialSnapshot();
            if (snap != null) {
                game.resetGame(
                        new org.pocketchess.core.game.model.TimeControl(5 * 60, 0),
                        GameMode.PVP, Piece.Color.WHITE,
                        aiDifficulty, GameModeType.CHESS960);
                for (int r = 0; r < 8; r++) {
                    for (int c = 0; c < 8; c++) {
                        game.getBoard().getBox(r, c).setPiece(snap.getBox(r, c).getPiece());
                    }
                }
                game.getBoard().saveAsInitial();
            }
        } else if (temp.getGameModeType() == GameModeType.LAVA) {
            // Carry the imported seed over to our own engine so replay produces
            // the exact same lava waves as the original.
            long seed = temp.getLavaManager().getSeed();
            game.getLavaManager().reseed(seed);
            game.resetGame(
                    new org.pocketchess.core.game.model.TimeControl(5 * 60, 0),
                    GameMode.PVP, Piece.Color.WHITE,
                    aiDifficulty, GameModeType.LAVA);
        }

        // Snapshot the initial (pre-move) position now that the variant — and
        // its lava seed / Chess960 back-rank — is set up, so the caller can seed
        // the ply-0 entry of its replay history correctly.
        this.initialFen = fen();
        this.initialLava = lavaSquares();
        this.initialWarning = warningSquares();

        boolean chess960 = temp.getGameModeType() == GameModeType.CHESS960;
        List<String> uciMoves = new ArrayList<>(temp.getMoveHistory().size());
        for (Move m : temp.getMoveHistory()) {
            // Chess960 castles must replay as king-takes-rook so they apply for
            // any king file (king→g/c-file only works when the king moves >1 sq).
            if (chess960 && m.wasCastlingMove && m.chess960RookFromCol >= 0) {
                uciMoves.add("" + (char) ('a' + m.start.getY()) + (8 - m.start.getX())
                              + (char) ('a' + m.chess960RookFromCol) + (8 - m.start.getX()));
                continue;
            }
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

    /** Initial-position FEN captured by the most recent {@link #loadFromPgn}. */
    public String initialFen() { return initialFen == null ? fen() : initialFen; }
    /** Initial-position lava squares captured by the most recent {@link #loadFromPgn}. */
    public List<String> initialLava() { return initialLava; }
    /** Initial-position warning squares captured by the most recent {@link #loadFromPgn}. */
    public List<String> initialWarning() { return initialWarning; }

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
     * Human-readable label for a move, used in error messages instead of the
     * raw UCI string. Approximates SAN: "Kc3", "Nxf3", "exd5", "e8=Q", and
     * "O-O" / "O-O-O" for a king-takes-rook castle attempt.
     */
    private String describeMove(UciMove m) {
        Piece p = game.getBoard().getBox(m.fromRow(), m.fromCol()).getPiece();
        String dest = toSquareName(m.toRow(), m.toCol());
        if (p == null) return dest;
        Piece target = game.getBoard().getBox(m.toRow(), m.toCol()).getPiece();

        if (p instanceof King && target instanceof Rook && target.isWhite() == p.isWhite()) {
            return m.toCol() > m.fromCol() ? "O-O" : "O-O-O";   // castle attempt
        }
        boolean capture = target != null && target.isWhite() != p.isWhite();
        String promo = m.promotion() != null
                ? "=" + Character.toUpperCase(m.promotion()) : "";
        if (p instanceof Pawn) {
            String fromFile = "" + (char) ('a' + m.fromCol());
            return (capture ? fromFile + "x" : "") + dest + promo;
        }
        return pieceSymbol(p) + (capture ? "x" : "") + dest;
    }

    private static String pieceSymbol(Piece p) {
        if (p instanceof King)   return "K";
        if (p instanceof Queen)  return "Q";
        if (p instanceof Rook)   return "R";
        if (p instanceof Bishop) return "B";
        if (p instanceof Knight) return "N";
        return "";
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
            return MoveResult.reject("Illegal move: " + describeMove(m));
        }

        if (game.getStatus() == GameStatus.AWAITING_PROMOTION) {
            Piece promo = promotionPiece(m.promotion(), moverIsWhite);
            game.promotePawn(promo);
        }

        return MoveResult.ok(uci, fen(), game.getStatus(), game.isWhiteTurn(),
                lavaSquares(), warningSquares());
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
        boolean chess960Castle = game.getGameModeType() == GameModeType.CHESS960
                && best.wasCastlingMove && best.chess960RookFromCol >= 0;

        boolean accepted;
        if (chess960Castle) {
            // Apply as king-takes-rook so the engine's Chess960 castling path
            // runs regardless of the king's starting file (the king→g/c-file
            // shortcut only works when the king travels more than one square).
            accepted = game.playerMove(
                    best.start.getX(), best.start.getY(),
                    best.start.getX(), best.chess960RookFromCol);
        } else {
            accepted = game.playerMove(
                    best.start.getX(), best.start.getY(),
                    best.end.getX(), best.end.getY());
        }
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

        if (chess960Castle) {
            // Report the same king→rook UCI the human path uses, so the client
            // animates the castle consistently.
            String castleUci = "" + (char) ('a' + best.start.getY()) + (8 - best.start.getX())
                                  + (char) ('a' + best.chess960RookFromCol) + (8 - best.start.getX());
            return MoveResult.ok(castleUci, fen(), game.getStatus(), game.isWhiteTurn(),
                    lavaSquares(), warningSquares());
        }

        return MoveResult.ok(UciMove.fromMove(best, promoSuffix),
                fen(), game.getStatus(), game.isWhiteTurn(),
                lavaSquares(), warningSquares());
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

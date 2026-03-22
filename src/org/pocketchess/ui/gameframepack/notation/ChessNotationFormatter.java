package org.pocketchess.ui.gameframepack.notation;

import org.pocketchess.core.game.model.GameMode;
import org.pocketchess.core.game.model.GameStatus;
import org.pocketchess.core.game.moveanalyze.Move;
import org.pocketchess.core.game.model.TimeControl;
import org.pocketchess.core.pieces.*;
import org.pocketchess.core.general.Board;
import org.pocketchess.core.general.Game;

import java.util.*;

/**
 * Converts Move objects into Standard Algebraic Notation (SAN).
 *   A notation cache (Map<Move, String>) is maintained.  When getNotationForMove()
 *   is called for a move that is not yet cached, rebuildCache() replays the entire
 *   game history ONCE, computing and storing notation for every move in a single
 *   sequential pass.  Subsequent look-ups are O(1) map reads.
 *
 *   The cache is invalidated whenever the history size changes (new move made,
 *   undo performed, PGN loaded).  Identity comparison on Move objects keeps the
 *   check free.
 */
public class ChessNotationFormatter {
    private final Game game;

    /** Cached notation strings, keyed by Move identity. */
    private final Map<Move, String> notationCache = new IdentityHashMap<>();

    /** Size of game.getMoveHistory() when the cache was last built. */
    private int cachedHistorySize = -1;

    public ChessNotationFormatter(Game game) {
        this.game = game;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns SAN for the given move, rebuilding the cache if the history has
     * changed since the last call.
     */
    public String getNotationForMove(Move move) {
        if (move == null || move.pieceMoved == null
                || move.start == null || move.end == null) {
            return "";
        }

        List<Move> history = game.getMoveHistory();

        // Invalidate and rebuild whenever history length changes
        if (history.size() != cachedHistorySize) {
            rebuildCache(history);
        }

        return notationCache.getOrDefault(move, "");
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Cache rebuild  — O(n) total for n moves
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Replays the game once from the start, computing notation for every move
     * at the correct board state.
     *
     * Board state at move i is needed to detect ambiguity (two knights that can
     * both reach the same square).  We achieve this by replaying with a temporary
     * Game and computing each move's notation BEFORE applying it to the temp game.
     */
    private void rebuildCache(List<Move> history) {
        notationCache.clear();

        // Temporary game that mirrors the real game's starting state
        Game tempGame = new Game();
        tempGame.resetGame(new TimeControl(1, 0), GameMode.PVP, Piece.Color.WHITE);

        for (Move move : history) {
            // Board is now in the state it was in BEFORE this move — compute notation
            String notation = computeNotation(move, tempGame);
            notationCache.put(move, notation);

            // Advance the temp game
            tempGame.playerMove(
                    move.start.getX(), move.start.getY(),
                    move.end.getX(),   move.end.getY(),
                    move.promotedTo
            );
        }

        cachedHistorySize = history.size();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Notation computation (called once per move during rebuild)
    // ─────────────────────────────────────────────────────────────────────────

    private String computeNotation(Move move, Game boardState) {
        // Castling
        if (move.wasCastlingMove) {
            return (move.end.getY() > 5 ? "O-O" : "O-O-O") + checkOrMateSymbol(move);
        }

        String pieceSymbol  = getPieceSymbol(move.pieceMoved);
        String captureSymbol = move.pieceKilled != null ? "x" : "";
        String endCoords    = getCoords(move.end);
        String promotion    = move.promotedTo != null ? "=" + getPieceSymbol(move.promotedTo) : "";
        String checkSymbol  = checkOrMateSymbol(move);

        // Pawn
        if (move.pieceMoved instanceof Pawn) {
            String startFile = move.pieceKilled != null
                    ? String.valueOf(getCoords(move.start).charAt(0))
                    : "";
            return startFile + captureSymbol + endCoords + promotion + checkSymbol;
        }

        // Piece — resolve ambiguity using the supplied board state
        String ambiguity = resolveAmbiguity(move, boardState);
        return pieceSymbol + ambiguity + captureSymbol + endCoords + promotion + checkSymbol;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Ambiguity resolution  (now called with the pre-built board state)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Determines whether a file, rank, or full coordinate must be added to
     * disambiguate the moving piece from another piece of the same type that
     * could also legally reach the destination.
     */
    private String resolveAmbiguity(Move move, Game boardState) {
        Board board        = boardState.getBoard();
        Spot  startSpot    = board.getBox(move.start.getX(), move.start.getY());
        Spot  endSpot      = board.getBox(move.end.getX(),   move.end.getY());

        // Collect other pieces of the same type and colour that can reach endSpot
        List<Spot> ambiguous = new ArrayList<>();
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece p = board.getBox(r, c).getPiece();
                if (p == null) continue;
                if (p.getClass() != move.pieceMoved.getClass()) continue;
                if (p.isWhite() != move.pieceMoved.isWhite()) continue;
                if (r == startSpot.getX() && c == startSpot.getY()) continue; // skip self

                if (boardState.isMoveLegal(board.getBox(r, c), endSpot)) {
                    ambiguous.add(board.getBox(r, c));
                }
            }
        }

        if (ambiguous.isEmpty()) return "";

        boolean fileUnique = true;
        boolean rankUnique = true;

        for (Spot s : ambiguous) {
            if (s.getY() == startSpot.getY()) fileUnique = false;
            if (s.getX() == startSpot.getX()) rankUnique = false;
        }

        if (fileUnique)  return String.valueOf(getCoords(startSpot).charAt(0));
        if (rankUnique)  return String.valueOf(getCoords(startSpot).charAt(1));
        return getCoords(startSpot);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private String checkOrMateSymbol(Move move) {
        if (move.statusAfterMove == GameStatus.WHITE_WIN
                || move.statusAfterMove == GameStatus.BLACK_WIN) return "#";
        if (move.statusAfterMove == GameStatus.CHECK) return "+";
        return "";
    }

    private String getPieceSymbol(Piece piece) {
        if (piece instanceof King)   return "K";
        if (piece instanceof Queen)  return "Q";
        if (piece instanceof Rook)   return "R";
        if (piece instanceof Bishop) return "B";
        if (piece instanceof Knight) return "N";
        return "";
    }

    private String getCoords(Spot spot) {
        return "" + (char) ('a' + spot.getY()) + (8 - spot.getX());
    }
}
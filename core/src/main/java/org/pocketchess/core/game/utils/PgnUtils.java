package org.pocketchess.core.game.utils;

import org.pocketchess.core.ai.difficulty.AIDifficulty;
import org.pocketchess.core.ai.search.FastMoveGenerator;
import org.pocketchess.core.game.model.GameStatus;
import org.pocketchess.core.game.model.TimeControl;
import org.pocketchess.core.game.moveanalyze.Move;
import org.pocketchess.core.gamemode.GameModeType;
import org.pocketchess.core.general.Board;
import org.pocketchess.core.general.Game;
import org.pocketchess.core.pieces.*;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PgnUtils {

    private static final Pattern HEADER = Pattern.compile("\\[(\\w+)\\s+\"([^\"]*)\"]");

    public static void loadPgn(Game game, String pgn) {
        // Extract the headers we care about BEFORE stripping them — Chess960
        // PGNs carry the actual back-rank arrangement in [FEN "..."], and
        // Lava PGNs carry the [LavaSeed "..."] used by the wave RNG so the
        // exact lava sequence is reproducible on replay.
        String variant   = extractHeader(pgn, "Variant");
        String startFen  = extractHeader(pgn, "FEN");
        String lavaSeed  = extractHeader(pgn, "LavaSeed");
        boolean chess960 = variant != null && variant.toLowerCase().contains("960");
        boolean lava     = variant != null && variant.toLowerCase().contains("lava");

        // Reseed the lava RNG BEFORE resetGame — resetGame's enable() draws
        // the first wave from the seeded random.
        if (lava && lavaSeed != null) {
            try {
                game.getLavaManager().reseed(Long.parseLong(lavaSeed.trim()));
            } catch (NumberFormatException ignored) { /* fall back to default seed */ }
        }

        GameModeType gmt = chess960 ? GameModeType.CHESS960
                          : lava     ? GameModeType.LAVA
                          :            GameModeType.CLASSIC;
        game.resetGame(new TimeControl(500000, 0),
                game.getGameMode(), game.getPlayerColor(),
                AIDifficulty.MEDIUM, gmt);

        if (chess960 && startFen != null && !startFen.isBlank()) {
            applyFenPosition(game.getBoard(), startFen);
            game.getBoard().saveAsInitial();
        }

        pgn = pgn.replaceAll("\\[.*?]", "").replaceAll("\\{[^}]*}", "").replaceAll("1-0|0-1|1/2-1/2|\\*", "").trim();
        pgn = pgn.replaceAll("\\d+\\.", "").trim();
        String[] moves = pgn.split("\\s+");
        FastMoveGenerator moveGenerator = new FastMoveGenerator();

        for (String moveSan : moves) {
            if (moveSan.isEmpty()) continue;
            List<Move> legalMoves = moveGenerator.generateLegalMoves(game);
            Move moveToDo = findMoveBySan(legalMoves, moveSan);

            if (moveToDo != null) {
                if (moveToDo.wasCastlingMove && moveToDo.chess960RookFromCol >= 0) {
                    // Apply as king-takes-rook so castling works for any king
                    // file — the king→g/c-file form only registers as a castle
                    // when the king travels more than one square.
                    game.playerMove(moveToDo.start.getX(), moveToDo.start.getY(),
                            moveToDo.start.getX(), moveToDo.chess960RookFromCol, null);
                } else {
                    game.playerMove(moveToDo.start.getX(), moveToDo.start.getY(),
                            moveToDo.end.getX(), moveToDo.end.getY(),
                            moveToDo.promotedTo);
                }
            } else {
                throw new IllegalArgumentException("Invalid SAN move: " + moveSan);
            }
        }
    }

    /** [piece]?[disambiguation]?[x]?dest[=promo]? — the standard SAN body. */
    private static final Pattern SAN =
            Pattern.compile("^([KQRBN])?([a-h]?[1-8]?)x?([a-h][1-8])=?([QRBN])?$");

    private static Move findMoveBySan(List<Move> legalMoves, String san) {
        san = san.replaceAll("[+#]", "").replace("0", "O");   // strip check marks; 0-0 → O-O

        // Castling — match by the canonical O-O / O-O-O form.
        if (san.equals("O-O") || san.equals("O-O-O")) {
            for (Move m : legalMoves) {
                if (m.wasCastlingMove && generateSimpleNotation(m).equals(san)) return m;
            }
            return null;
        }

        // Fast path: an unambiguous move whose generated notation matches exactly.
        for (Move m : legalMoves) {
            if (generateSimpleNotation(m).equals(san)) return m;
        }

        // General path: parse the SAN and match piece + destination, then apply
        // any file/rank disambiguation. This tolerates extra disambiguation that
        // some exporters add (e.g. a king move written "Kbb8").
        Matcher mt = SAN.matcher(san);
        if (!mt.matches()) return null;
        String pieceLetter = mt.group(1) == null ? "" : mt.group(1);
        String disambig    = mt.group(2) == null ? "" : mt.group(2);
        String dest        = mt.group(3);
        String promo       = mt.group(4);

        for (Move m : legalMoves) {
            if (!getPieceSymbol(m.pieceMoved).equals(pieceLetter)) continue;
            if (!getCoords(m.end).equals(dest)) continue;
            if (promo != null && (m.promotedTo == null
                    || !getPieceSymbol(m.promotedTo).equals(promo))) continue;

            String from = getCoords(m.start);     // e.g. "b7"
            boolean matches = true;
            for (char ch : disambig.toCharArray()) {
                if (Character.isDigit(ch)) { if (from.charAt(1) != ch) matches = false; }
                else                       { if (from.charAt(0) != ch) matches = false; }
            }
            if (matches) return m;
        }
        return null; // Move not found
    }

    private static String generateSimpleNotation(Move move) {
        if (move.wasCastlingMove) {
            // Kingside is always the col-6 (g-file) destination; queenside is
            // col 2. Comparing king start vs end breaks in Chess960 when the
            // king starts left of centre (e.g. b-file), where the queenside
            // target c-file is to its right.
            return move.end.getY() == 6 ? "O-O" : "O-O-O";
        }
        String pieceSymbol = getPieceSymbol(move.pieceMoved);
        String captureSymbol = move.pieceKilled != null ? "x" : "";
        String coords = getCoords(move.end);
        String promotion = move.promotedTo != null ? "=" + getPieceSymbol(move.promotedTo) : "";
        if (move.pieceMoved instanceof Pawn) {
            if (move.pieceKilled != null) {
                return getCoords(move.start).charAt(0) + captureSymbol + coords + promotion;
            } else {
                return coords + promotion;
            }
        }
        return pieceSymbol + captureSymbol + coords + promotion;
    }

    private static String getCoords(Spot spot) {
        return "" + (char) ('a' + spot.getY()) + (8 - spot.getX());
    }

    private static String getPieceSymbol(Piece piece) {
        if (piece instanceof King) return "K";
        if (piece instanceof Queen) return "Q";
        if (piece instanceof Rook) return "R";
        if (piece instanceof Bishop) return "B";
        if (piece instanceof Knight) return "N";
        return "";
    }

    public static String getResultString(GameStatus status) {
        return switch (status) {
            case WHITE_WIN, WHITE_WIN_ON_TIME, WHITE_WINS_BY_RESIGNATION -> "1-0";
            case BLACK_WIN, BLACK_WIN_ON_TIME, BLACK_WINS_BY_RESIGNATION -> "0-1";
            case STALEMATE, DRAW_50_MOVES, DRAW_AGREED, DRAW_INSUFFICIENT_MATERIAL, DRAW_THREEFOLD_REPETITION ->
                    "1/2-1/2";
            default -> "*";
        };
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Header / FEN parsing — only the bits we need for Chess960 imports.
    // ─────────────────────────────────────────────────────────────────────

    private static String extractHeader(String pgn, String name) {
        if (pgn == null) return null;
        Matcher m = HEADER.matcher(pgn);
        while (m.find()) {
            if (m.group(1).equalsIgnoreCase(name)) return m.group(2);
        }
        return null;
    }

    /** Populates {@code board} from the position field of a FEN. */
    private static void applyFenPosition(Board board, String fen) {
        String[] fields = fen.trim().split("\\s+");
        if (fields.length == 0) return;
        String[] ranks = fields[0].split("/");
        if (ranks.length != 8) {
            throw new IllegalArgumentException("FEN does not have 8 ranks: " + fen);
        }
        // Clear board first.
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) board.getBox(r, c).setPiece(null);
        }
        for (int r = 0; r < 8; r++) {
            int col = 0;
            for (int i = 0; i < ranks[r].length(); i++) {
                char ch = ranks[r].charAt(i);
                if (ch >= '1' && ch <= '8') {
                    col += ch - '0';
                } else {
                    if (col > 7) throw new IllegalArgumentException("FEN rank too long: " + ranks[r]);
                    board.getBox(r, col).setPiece(pieceFromFenChar(ch));
                    col++;
                }
            }
        }
        board.setEnPassantTargetSquare(null);
    }

    private static Piece pieceFromFenChar(char ch) {
        boolean white = Character.isUpperCase(ch);
        return switch (Character.toLowerCase(ch)) {
            case 'p' -> new Pawn(white);
            case 'n' -> new Knight(white);
            case 'b' -> new Bishop(white);
            case 'r' -> new Rook(white);
            case 'q' -> new Queen(white);
            case 'k' -> new King(white);
            default -> throw new IllegalArgumentException("Unknown FEN piece char: " + ch);
        };
    }
}
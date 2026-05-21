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
        // PGNs carry the actual back-rank arrangement in [FEN "..."].
        String variant   = extractHeader(pgn, "Variant");
        String startFen  = extractHeader(pgn, "FEN");
        boolean chess960 = variant != null && variant.toLowerCase().contains("960");

        GameModeType gmt = chess960 ? GameModeType.CHESS960 : GameModeType.CLASSIC;
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
            List<Move> legalMoves = moveGenerator.generateMoves(game);
            Move moveToDo = findMoveBySan(legalMoves, moveSan);

            if (moveToDo != null) {
                game.playerMove(moveToDo.start.getX(), moveToDo.start.getY(),
                        moveToDo.end.getX(), moveToDo.end.getY(),
                        moveToDo.promotedTo);
            } else {
                throw new IllegalArgumentException("Invalid SAN move: " + moveSan);
            }
        }
    }

    private static Move findMoveBySan(List<Move> legalMoves, String san) {
        san = san.replaceAll("[+#]", ""); // Remove check and checkmate symbols

        List<Move> candidates = new ArrayList<>();

        for (Move move : legalMoves) {
            // Generate simple notation for each legal move
            String simpleSan = generateSimpleNotation(move);

            if (simpleSan.equals(san)) {
                return move;
            }

            // Check if the move matches ambiguous notation (e.g., "Nbc6")
            boolean b = simpleSan.endsWith(san.substring(san.length() - 2));
            if (move.pieceMoved instanceof Knight && san.startsWith("N") && b) {
                candidates.add(move);
            }
            if (move.pieceMoved instanceof Rook && san.startsWith("R") && b) {
                candidates.add(move);
            }
        }

        if (candidates.size() == 1) {
            return candidates.get(0);
        }

        if (candidates.size() > 1 && san.length() >= 4) {
            char disambiguationChar = san.charAt(1);
            for (Move candidate : candidates) {
                if (getCoords(candidate.start).charAt(0) == disambiguationChar) {
                    return candidate;
                }
                if (getCoords(candidate.start).charAt(1) == disambiguationChar) {
                    return candidate;
                }
            }
        }

        return null; // Move not found
    }

    private static String generateSimpleNotation(Move move) {
        if (move.wasCastlingMove) {
            return move.end.getY() > move.start.getY() ? "O-O" : "O-O-O";
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
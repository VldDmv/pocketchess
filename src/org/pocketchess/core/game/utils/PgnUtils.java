package org.pocketchess.core.game.utils;

import org.pocketchess.core.ai.FastMoveGenerator;
import org.pocketchess.core.game.GameStatus;
import org.pocketchess.core.game.TimeControl;
import org.pocketchess.core.game.moveanalyze.Move;
import org.pocketchess.core.general.Game;
import org.pocketchess.core.pieces.*;

import java.util.ArrayList;
import java.util.List;

public class PgnUtils {

    public static void loadPgn(Game game, String pgn) {
        game.resetGame(new TimeControl(10, 0), game.getGameMode(), game.getPlayerColor());
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
                System.err.println("Could not parse move: " + moveSan);
                break;
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
}
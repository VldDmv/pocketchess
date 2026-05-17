package org.pocketchess.online.engine;

import org.pocketchess.core.game.moveanalyze.Move;

/**
 * Long-algebraic / UCI move on a classical 8×8 board.
 *
 * <p>UCI strings use file letters {@code a..h} and rank digits {@code 1..8}
 * (white's back rank is 1). The promotion suffix is one of {@code q r b n}.
 *
 * <p>The engine internally indexes squares by (row, col) where row 0 is
 * black's back rank and row 7 is white's, so this record translates between
 * the two conventions.
 */
public record UciMove(int fromRow, int fromCol, int toRow, int toCol, Character promotion) {

    public static UciMove parse(String uci) {
        if (uci == null || (uci.length() != 4 && uci.length() != 5)) {
            throw new IllegalArgumentException("Invalid UCI move: " + uci);
        }
        int fromCol = fileToCol(uci.charAt(0));
        int fromRow = rankToRow(uci.charAt(1));
        int toCol   = fileToCol(uci.charAt(2));
        int toRow   = rankToRow(uci.charAt(3));
        Character promo = null;
        if (uci.length() == 5) {
            char c = Character.toLowerCase(uci.charAt(4));
            if ("qrbn".indexOf(c) < 0) {
                throw new IllegalArgumentException("Invalid promotion piece in UCI: " + uci);
            }
            promo = c;
        }
        return new UciMove(fromRow, fromCol, toRow, toCol, promo);
    }

    /** Builds a UCI string for a {@link Move} produced by the engine. */
    public static String fromMove(Move move) {
        return fromMove(move, null);
    }

    public static String fromMove(Move move, Character promotion) {
        StringBuilder sb = new StringBuilder(promotion == null ? 4 : 5);
        sb.append(colToFile(move.start.getY())).append(rowToRank(move.start.getX()));
        sb.append(colToFile(move.end.getY())).append(rowToRank(move.end.getX()));
        if (promotion != null) sb.append(Character.toLowerCase(promotion));
        return sb.toString();
    }

    private static int fileToCol(char file) {
        char lower = Character.toLowerCase(file);
        if (lower < 'a' || lower > 'h') {
            throw new IllegalArgumentException("Invalid file: " + file);
        }
        return lower - 'a';
    }

    private static int rankToRow(char rank) {
        if (rank < '1' || rank > '8') {
            throw new IllegalArgumentException("Invalid rank: " + rank);
        }
        return 8 - (rank - '0');
    }

    private static char colToFile(int col) {
        return (char) ('a' + col);
    }

    private static char rowToRank(int row) {
        return (char) ('0' + (8 - row));
    }
}

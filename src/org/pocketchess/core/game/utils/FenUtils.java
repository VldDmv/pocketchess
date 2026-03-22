package org.pocketchess.core.game.utils;

import org.pocketchess.core.general.Board;
import org.pocketchess.core.pieces.*;

public final class FenUtils {
    private FenUtils() {
    }

    /**
     * Appends castling rights for one side.
     * Works for both Classic (king on e-file) and Chess960 (king anywhere).
     * Uses uppercase letters for White (K/Q), lowercase for Black (k/q).
     */
    private static void appendCastlingRights(Board board, StringBuilder sb, boolean isWhite) {
        int rank = isWhite ? 7 : 0;

        // Find the unmoved king on this rank
        int kingCol = -1;
        for (int c = 0; c < 8; c++) {
            Piece p = board.getBox(rank, c).getPiece();
            if (p instanceof King && p.isWhite() == isWhite && !((King) p).hasMoved()) {
                kingCol = c;
                break;
            }
        }
        if (kingCol == -1) return; // King has moved — no castling rights

        // Kingside: scan right of king for unmoved rook
        for (int c = kingCol + 1; c < 8; c++) {
            Piece p = board.getBox(rank, c).getPiece();
            if (p instanceof Rook && p.isWhite() == isWhite && !((Rook) p).hasMoved()) {
                sb.append(isWhite ? 'K' : 'k');
                break;
            }
            if (p != null) break;
        }

        // Queenside: scan left of king for unmoved rook
        for (int c = kingCol - 1; c >= 0; c--) {
            Piece p = board.getBox(rank, c).getPiece();
            if (p instanceof Rook && p.isWhite() == isWhite && !((Rook) p).hasMoved()) {
                sb.append(isWhite ? 'Q' : 'q');
                break;
            }
            if (p != null) break;
        }
    }

    /**
     * Generates a FEN string from the current position.
     * Used for:
     * - Tracking position repetitions
     * - Searching the opening book
     * - Saving/loading positions
     */
    public static String generateFEN(Board board, boolean isWhiteTurn) {
        StringBuilder fen = new StringBuilder();

        // piece pos
        for (int row = 0; row < 8; row++) {
            int emptySquares = 0;
            for (int col = 0; col < 8; col++) {
                Piece piece = board.getBox(row, col).getPiece();
                if (piece == null) {
                    emptySquares++;
                } else {
                    if (emptySquares > 0) {
                        fen.append(emptySquares);
                        emptySquares = 0;
                    }
                    fen.append(getFenCharFromPiece(piece));
                }
            }
            if (emptySquares > 0) {
                fen.append(emptySquares);
            }
            if (row < 7) {
                fen.append('/');
            }
        }

        fen.append(isWhiteTurn ? " w " : " b ");

        // Castling rights — scan dynamically so Chess960 works correctly.
        // Classic:  king must be on e-file (col 4); rooks on a/h-files.
        // Chess960: king can be anywhere; rooks wherever they started.
        StringBuilder castlingRights = new StringBuilder();

        appendCastlingRights(board, castlingRights, true);   // White
        appendCastlingRights(board, castlingRights, false);  // Black

        if (castlingRights.isEmpty()) {
            fen.append("- ");
        } else {
            fen.append(castlingRights).append(" ");
        }

        // En passant
        Spot enPassantTarget = board.getEnPassantTargetSquare();
        if (enPassantTarget != null) {
            fen.append((char) ('a' + enPassantTarget.getY())).append(8 - enPassantTarget.getX());
        } else {
            fen.append("-");
        }

        return fen.toString();
    }

    /**
     * Converts the piece to the FEN symbol.
     */
    private static String getFenCharFromPiece(Piece piece) {
        if (piece == null) return "";
        String name = "";
        if (piece instanceof Pawn) name = "p";
        else if (piece instanceof Knight) name = "n";
        else if (piece instanceof Bishop) name = "b";
        else if (piece instanceof Rook) name = "r";
        else if (piece instanceof Queen) name = "q";
        else if (piece instanceof King) name = "k";
        return piece.isWhite() ? name.toUpperCase() : name;
    }
}
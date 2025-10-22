package org.pocketchess.core.game.utils;

import org.pocketchess.core.general.Board;
import org.pocketchess.core.pieces.*;

public final class FenUtils {
    private FenUtils() {
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

        StringBuilder castlingRights = new StringBuilder();

        // White
        Piece whiteKing = board.getBox(7, 4).getPiece();
        if (whiteKing instanceof King && !((King) whiteKing).hasMoved()) {
            Piece kingRook = board.getBox(7, 7).getPiece();
            if (kingRook instanceof Rook && !((Rook) kingRook).hasMoved()) castlingRights.append("K");
            Piece queenRook = board.getBox(7, 0).getPiece();
            if (queenRook instanceof Rook && !((Rook) queenRook).hasMoved()) castlingRights.append("Q");
        }

        // Black
        Piece blackKing = board.getBox(0, 4).getPiece();
        if (blackKing instanceof King && !((King) blackKing).hasMoved()) {
            Piece kingRook = board.getBox(0, 7).getPiece();
            if (kingRook instanceof Rook && !((Rook) kingRook).hasMoved()) castlingRights.append("k");
            Piece queenRook = board.getBox(0, 0).getPiece();
            if (queenRook instanceof Rook && !((Rook) queenRook).hasMoved()) castlingRights.append("q");
        }

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
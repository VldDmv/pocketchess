package org.pocketchess.core.game.moveanalyze;

import org.pocketchess.core.general.Board;
import org.pocketchess.core.pieces.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks for insufficient material for a checkmate.
 * If this situation occurs, the game is automatically declared a draw
 */
public class MaterialChecker {

    /**
     * Main check for insufficient material.
     */
    public boolean isInsufficientMaterial(Board board) {
        List<Piece> whitePieces = new ArrayList<>();
        List<Piece> blackPieces = new ArrayList<>();


        collectPieces(board, whitePieces, blackPieces);


        if (hasSufficientMaterial(whitePieces) || hasSufficientMaterial(blackPieces)) {
            return false;
        }


        return checkInsufficientCombinations(board, whitePieces, blackPieces);
    }

    /**
     * Collects all pieces from the board (except kings).
     */
    private void collectPieces(Board board, List<Piece> whitePieces, List<Piece> blackPieces) {
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece p = board.getBox(r, c).getPiece();
                if (p != null && !(p instanceof King)) {
                    if (p.isWhite()) {
                        whitePieces.add(p);
                    } else {
                        blackPieces.add(p);
                    }
                }
            }
        }
    }

    /**
     Checks if there is enough material for the checkmate
     */
    private boolean hasSufficientMaterial(List<Piece> pieces) {
        for (Piece p : pieces) {
            if (p instanceof Queen || p instanceof Rook || p instanceof Pawn) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks for special cases of material insuf.
     */
    private boolean checkInsufficientCombinations(Board board, List<Piece> whitePieces, List<Piece> blackPieces) {
        // K vs K || K vs K+N || K vs K+B
        if (whitePieces.size() <= 1 && blackPieces.isEmpty()) return true;
        if (blackPieces.size() <= 1 && whitePieces.isEmpty()) return true;

        // K+B vs K+B
        if (whitePieces.size() == 1 && blackPieces.size() == 1) {
            if (areBishopsOnSameColor(board, whitePieces.get(0), blackPieces.get(0))) {
                return true;
            }
        }

        // K+N vs K+B
        if (whitePieces.size() == 1 && blackPieces.size() == 1) {
            Piece wPiece = whitePieces.get(0);
            Piece bPiece = blackPieces.get(0);
            if ((wPiece instanceof Knight && bPiece instanceof Bishop) ||
                    (wPiece instanceof Bishop && bPiece instanceof Knight)) {
                return true;
            }
        }

        // 2N vs KING
        if (isTwoKnightsVsKing(whitePieces, blackPieces)) return true;
        if (isTwoKnightsVsKing(blackPieces, whitePieces)) return true;

        // Several same-field bibshops vs king
        if (areAllBishopsOnSameColor(board, whitePieces, blackPieces)) return true;
        return areAllBishopsOnSameColor(board, blackPieces, whitePieces);
    }

    /**
     * Checks if two bishops are on squares of the same color
     */
    private boolean areBishopsOnSameColor(Board board, Piece piece1, Piece piece2) {
        if (!(piece1 instanceof Bishop && piece2 instanceof Bishop)) {
            return false;
        }

        Spot spot1 = findPiece(board, piece1);
        Spot spot2 = findPiece(board, piece2);

        if (spot1 != null && spot2 != null) {
            // square color = parity of the sum of coordinates
            return (spot1.getX() + spot1.getY()) % 2 == (spot2.getX() + spot2.getY()) % 2;
        }
        return false;
    }

    /**
     * Tests the situation of "two knights against a lone king"
     */
    private boolean isTwoKnightsVsKing(List<Piece> attackers, List<Piece> defenders) {
        if (attackers.size() == 2 && defenders.isEmpty()) {
            return attackers.get(0) instanceof Knight && attackers.get(1) instanceof Knight;
        }
        return false;
    }

    /**
     Checks if all bishops are on squares of the same color.
     */
    private boolean areAllBishopsOnSameColor(Board board, List<Piece> pieces, List<Piece> opponents) {
        if (pieces.size() <= 1 || !opponents.isEmpty()) {
            return false;
        }

        int firstBishopColor = -1;
        for (Piece p : pieces) {
            if (!(p instanceof Bishop)) {
                return false;
            }

            Spot spot = findPiece(board, p);
            if (spot == null) {
                return false;
            }

            int squareColor = (spot.getX() + spot.getY()) % 2;
            if (firstBishopColor == -1) {
                firstBishopColor = squareColor;
            } else if (firstBishopColor != squareColor) {
                return false;
            }
        }
        return true;
    }

    /**
     * Finds the square on which the given piece stands
     */
    private Spot findPiece(Board board, Piece pieceToFind) {
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                if (board.getBox(i, j).getPiece() == pieceToFind) {
                    return board.getBox(i, j);
                }
            }
        }
        return null;
    }
}
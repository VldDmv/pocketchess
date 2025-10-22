package org.pocketchess.core.pieces;

import org.pocketchess.core.general.Board;

/**
 * Represents the Queen piece.
 */
public class Queen extends Piece {

    public Queen(boolean isWhite) {
        super(isWhite);
    }

    @Override
    public boolean canMove(Board board, Spot start, Spot end) {

        // Check move like a Rook (straight lines)
        Rook tempRook = new Rook(this.isWhite());
        if (tempRook.canMove(board, start, end)) {
            return true;
        }

        // Check move like a Bishop (diagonally)
        Bishop tempBishop = new Bishop(this.isWhite());
        return tempBishop.canMove(board, start, end);

        // If neither option works, the move is impossible
    }
}
package org.pocketchess.core.pieces;

import org.pocketchess.core.general.Board;

/**
 * Represents the Pawn piece.
 */
public class Pawn extends Piece {

    public Pawn(boolean isWhite) {
        super(isWhite);
    }

    @Override
    public boolean canMove(Board board, Spot start, Spot end) {
        // Cannot move to a square occupied by a piece of the same color
        if (end.getPiece() != null && end.getPiece().isWhite() == this.isWhite()) {
            return false;
        }

        int startX = start.getX();
        int endX = end.getX();
        int startY = start.getY();
        int endY = end.getY();

        int forwardDirection = this.isWhite() ? -1 : 1;

        // --- STANDARD ONE-SQUARE FORWARD MOVE ---
        if (startX + forwardDirection == endX && startY == endY && end.getPiece() == null) {
            return true;
        }

        // --- TWO-SQUARE FIRST MOVE ---
        boolean isStartingRank = (this.isWhite() && startX == 6) || (!this.isWhite() && startX == 1);

        if (isStartingRank && startX + 2 * forwardDirection == endX && startY == endY && end.getPiece() == null) {
            // Check that the path is clear
            Spot middleSpot = board.getBox(startX + forwardDirection, startY);
            if (middleSpot.getPiece() == null) {
                return true;
            }
        }

        // capture
        if (startX + forwardDirection == endX && Math.abs(startY - endY) == 1) {

            if (end.getPiece() != null && end.getPiece().isWhite() != this.isWhite()) {
                return true;
            }

            //en pass
            return end.getPiece() == null && board.getEnPassantTargetSquare() == end;
        }

        return false;
    }
}
package org.pocketchess.core.pieces;

import org.pocketchess.core.general.Board;

/**
 * Represents the Knight piece.
 */
public class Knight extends Piece {

    public Knight(boolean isWhite) {
        super(isWhite);
    }

    @Override
    public boolean canMove(Board board, Spot start, Spot end) {
        // Cannot move to a square occupied by a piece of the same color
        if (end.getPiece() != null && end.getPiece().isWhite() == this.isWhite()) {
            return false;
        }

        int startX = start.getX();
        int startY = start.getY();
        int endX = end.getX();
        int endY = end.getY();

        // Calculate the absolute difference along X and Y axes
        int dx = Math.abs(startX - endX);
        int dy = Math.abs(startY - endY);

        // Knight moves 2 squares in one direction and 1 square in perpendicular direction
        // This means the product of the differences along the axes is always 2
        // (dx=2, dy=1) or (dx=1, dy=2)
        return dx * dy == 2;
    }
}
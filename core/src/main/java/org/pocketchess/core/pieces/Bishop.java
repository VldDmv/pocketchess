package org.pocketchess.core.pieces;

import org.pocketchess.core.general.Board;

public class Bishop extends Piece {

    public Bishop(boolean isWhite) {
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

        // Bishop moves diagonally, so the change in X must equal the change in Y
        if (Math.abs(startX - endX) != Math.abs(startY - endY)) {
            return false;
        }

        // Determine the direction of diagonal movement
        int xDir = (endX - startX) > 0 ? 1 : -1;
        int yDir = (endY - startY) > 0 ? 1 : -1;

        // Check squares along the path for obstacles
        int currentX = startX + xDir;
        int currentY = startY + yDir;

        while (currentX != endX && currentY != endY) {
            if (board.getBox(currentX, currentY).getPiece() != null) {
                return false; // Path is blocked
            }
            currentX += xDir;
            currentY += yDir;
        }

        return true;
    }
}
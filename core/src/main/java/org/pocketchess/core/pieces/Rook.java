package org.pocketchess.core.pieces;

import org.pocketchess.core.general.Board;

public class Rook extends Piece {
    private boolean hasMoved = false;

    public Rook(boolean isWhite) {
        super(isWhite);
    }

    public Rook(boolean isWhite, boolean hasMoved) {
        super(isWhite);
        this.hasMoved = hasMoved;
    }

    public boolean hasMoved() {
        return hasMoved;
    }

    public void setHasMoved(boolean hasMoved) {
        this.hasMoved = hasMoved;
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

        // Rook must move either horizontally or vertically
        if (startX != endX && startY != endY) {
            return false;
        }

        // Check for obstacles along the path
        if (startX != endX) {
            // Vertical movement
            int minX = Math.min(startX, endX);
            int maxX = Math.max(startX, endX);
            for (int i = minX + 1; i < maxX; i++) {
                if (board.getBox(i, startY).getPiece() != null) {
                    return false;
                }
            }
        } else {
            // Horizontal movement
            int minY = Math.min(startY, endY);
            int maxY = Math.max(startY, endY);
            for (int i = minY + 1; i < maxY; i++) {
                if (board.getBox(startX, i).getPiece() != null) {
                    return false;
                }
            }
        }
        return true;
    }
}
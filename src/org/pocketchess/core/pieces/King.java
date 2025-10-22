package org.pocketchess.core.pieces;

import org.pocketchess.core.general.Board;

public class King extends Piece {
    private static final int KINGSIDE_ROOK_Y = 7;
    private static final int QUEENSIDE_ROOK_Y = 0;
    private boolean hasMoved = false;

    public King(boolean isWhite) {
        super(isWhite);
    }

    public King(boolean isWhite, boolean hasMoved) {
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
        // Cannot move to a square with a piece of the same color
        if (end.getPiece() != null && end.getPiece().isWhite() == this.isWhite()) {
            return false;
        }

        int x = Math.abs(start.getX() - end.getX());
        int y = Math.abs(start.getY() - end.getY());

        // Normal king move (one square)
        if (x <= 1 && y <= 1) {
            return true;
        }

        // Castling logic (king moves 2 squares horizontally)
        return isCastlingMove(board, start, end);
    }

    private boolean isCastlingMove(Board board, Spot start, Spot end) {
        // Castling is only possible if the king hasn't moved
        if (this.hasMoved) {
            return false;
        }

        int dx = end.getX() - start.getX();
        int dy = end.getY() - start.getY();

        // Castling is a horizontal move of 2 squares
        if (dx == 0 && Math.abs(dy) == 2) {
            // Determine which side to castle
            Spot rookSpot;
            if (dy > 0) { // Right (kingside castling)
                rookSpot = board.getBox(start.getX(), KINGSIDE_ROOK_Y);
            } else { // Left (queenside castling)
                rookSpot = board.getBox(start.getX(), QUEENSIDE_ROOK_Y);
            }

            // Check that there is a rook in place and it hasn't moved
            if (rookSpot.getPiece() == null || !(rookSpot.getPiece() instanceof Rook)) {
                return false;
            }
            if (((Rook) rookSpot.getPiece()).hasMoved()) {
                return false;
            }

            // Check that there are no pieces between the king and rook
            int direction = (int) Math.signum(dy);
            for (int i = 1; i < Math.abs(dy); i++) {
                if (board.getBox(start.getX(), start.getY() + i * direction).getPiece() != null) {
                    return false;
                }
            }

            return true;
        }
        return false;
    }
}
package org.pocketchess.core.pieces;

public class Spot {
    private final int x;
    private final int y;
    private Piece piece;

    public Spot(int x, int y, Piece piece) {
        this.x = x;
        this.y = y;
        this.piece = piece;
    }

    public Spot(Spot other) {
        this.x = other.x;
        this.y = other.y;
        if (other.piece != null) {

            if (other.piece instanceof King)
                this.piece = new King(other.piece.isWhite(), ((King) other.piece).hasMoved());
            else if (other.piece instanceof Rook)
                this.piece = new Rook(other.piece.isWhite(), ((Rook) other.piece).hasMoved());
            else if (other.piece instanceof Queen) this.piece = new Queen(other.piece.isWhite());
            else if (other.piece instanceof Bishop) this.piece = new Bishop(other.piece.isWhite());
            else if (other.piece instanceof Knight) this.piece = new Knight(other.piece.isWhite());
            else if (other.piece instanceof Pawn) this.piece = new Pawn(other.piece.isWhite());
        } else {
            this.piece = null;
        }
    }

    public Piece getPiece() {
        return this.piece;
    }

    public void setPiece(Piece p) {
        this.piece = p;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }
}
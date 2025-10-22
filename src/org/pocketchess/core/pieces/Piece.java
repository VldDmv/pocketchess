package org.pocketchess.core.pieces;

import org.pocketchess.core.general.Board;

public abstract class Piece {
    private final boolean isWhite;

    public Piece(boolean isWhite) {
        this.isWhite = isWhite;
    }

    public boolean isWhite() {
        return this.isWhite;
    }

    public abstract boolean canMove(Board board, Spot start, Spot end);

    public enum Color {WHITE, BLACK}
}
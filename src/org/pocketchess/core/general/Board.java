package org.pocketchess.core.general;

import org.pocketchess.core.pieces.*;

/**
 * Represents the chess board
 */
public class Board {
    Spot[][] boxes = new Spot[8][8];


    private Spot enPassantTargetSquare = null;

    public Board() {
        resetBoard();
    }

    /**
     * Gets the square at specified coordinates.
     */
    public Spot getBox(int x, int y) {
        if (x < 0 || x > 7 || y < 0 || y > 7) {
            throw new IllegalArgumentException("Index out of bound");
        }
        return boxes[x][y];
    }

    /**
     * Resets the board to the standard chess starting position.
     */
    public void resetBoard() {
        // White pieces (bottom - 1st rank)
        boxes[7][0] = new Spot(7, 0, new Rook(true));
        boxes[7][1] = new Spot(7, 1, new Knight(true));
        boxes[7][2] = new Spot(7, 2, new Bishop(true));
        boxes[7][3] = new Spot(7, 3, new Queen(true));
        boxes[7][4] = new Spot(7, 4, new King(true));
        boxes[7][5] = new Spot(7, 5, new Bishop(true));
        boxes[7][6] = new Spot(7, 6, new Knight(true));
        boxes[7][7] = new Spot(7, 7, new Rook(true));
        for (int i = 0; i < 8; i++) {
            boxes[6][i] = new Spot(6, i, new Pawn(true));
        }

        // Black pieces (top - 8th rank)
        boxes[0][0] = new Spot(0, 0, new Rook(false));
        boxes[0][1] = new Spot(0, 1, new Knight(false));
        boxes[0][2] = new Spot(0, 2, new Bishop(false));
        boxes[0][3] = new Spot(0, 3, new Queen(false));
        boxes[0][4] = new Spot(0, 4, new King(false));
        boxes[0][5] = new Spot(0, 5, new Bishop(false));
        boxes[0][6] = new Spot(0, 6, new Knight(false));
        boxes[0][7] = new Spot(0, 7, new Rook(false));
        for (int i = 0; i < 8; i++) {
            boxes[1][i] = new Spot(1, i, new Pawn(false));
        }

        // Empty squares
        for (int i = 2; i < 6; i++) {
            for (int j = 0; j < 8; j++) {
                boxes[i][j] = new Spot(i, j, null);
            }
        }
    }

    public Spot getEnPassantTargetSquare() {
        return enPassantTargetSquare;
    }

    public void setEnPassantTargetSquare(Spot enPassantTargetSquare) {
        this.enPassantTargetSquare = enPassantTargetSquare;
    }

    /**
     * Copy constructor - creates a deep copy of the board.
     */
    public Board(Board other) {
        this.enPassantTargetSquare = other.enPassantTargetSquare;
        this.boxes = new Spot[8][8];
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                this.boxes[r][c] = new Spot(other.boxes[r][c]);
            }
        }
    }
}
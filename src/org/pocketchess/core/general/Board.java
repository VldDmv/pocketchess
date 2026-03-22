package org.pocketchess.core.general;

import org.pocketchess.core.pieces.*;

/**
 * Represents the chess board.
 */
public class Board {
    Spot[][] boxes = new Spot[8][8];

    private Spot enPassantTargetSquare = null;

    /**
     * Snapshot of the game's opening position (standard or Chess960).
     * Set by {@link #saveAsInitial()} immediately after board setup.
     */
    private Spot[][] initialBoxes = null;

    public Board() {
        resetBoard();
    }

    public Spot getBox(int x, int y) {
        if (x < 0 || x > 7 || y < 0 || y > 7) {
            throw new IllegalArgumentException("Index out of bound");
        }
        return boxes[x][y];
    }

    /** Standard starting position (always 8×8 classical chess). */
    public void resetBoard() {
        boxes[7][0] = new Spot(7, 0, new Rook(true));
        boxes[7][1] = new Spot(7, 1, new Knight(true));
        boxes[7][2] = new Spot(7, 2, new Bishop(true));
        boxes[7][3] = new Spot(7, 3, new Queen(true));
        boxes[7][4] = new Spot(7, 4, new King(true));
        boxes[7][5] = new Spot(7, 5, new Bishop(true));
        boxes[7][6] = new Spot(7, 6, new Knight(true));
        boxes[7][7] = new Spot(7, 7, new Rook(true));
        for (int i = 0; i < 8; i++) boxes[6][i] = new Spot(6, i, new Pawn(true));

        boxes[0][0] = new Spot(0, 0, new Rook(false));
        boxes[0][1] = new Spot(0, 1, new Knight(false));
        boxes[0][2] = new Spot(0, 2, new Bishop(false));
        boxes[0][3] = new Spot(0, 3, new Queen(false));
        boxes[0][4] = new Spot(0, 4, new King(false));
        boxes[0][5] = new Spot(0, 5, new Bishop(false));
        boxes[0][6] = new Spot(0, 6, new Knight(false));
        boxes[0][7] = new Spot(0, 7, new Rook(false));
        for (int i = 0; i < 8; i++) boxes[1][i] = new Spot(1, i, new Pawn(false));

        for (int i = 2; i < 6; i++)
            for (int j = 0; j < 8; j++)
                boxes[i][j] = new Spot(i, j, null);

        enPassantTargetSquare = null;
    }

    // ── Initial-position snapshot ─────────────────────────────────────────────

    /**
     * Saves the current board layout as the "initial position" for this game.
     * Call this once after {@link #resetBoard()} (and after any Chess960
     * shuffle) so that history navigation can restore the correct start state.
     */
    public void saveAsInitial() {
        initialBoxes = new Spot[8][8];
        for (int r = 0; r < 8; r++)
            for (int c = 0; c < 8; c++)
                initialBoxes[r][c] = new Spot(boxes[r][c]);
    }

    /**
     * Resets the board to the saved initial position.
     * Falls back to the standard layout if {@link #saveAsInitial()} was never
     * called (e.g. in AI game copies created before this field existed).
     */
    public void resetToInitial() {
        enPassantTargetSquare = null;
        if (initialBoxes == null) {
            resetBoard();   // fallback: no snapshot available
            return;
        }
        for (int r = 0; r < 8; r++)
            for (int c = 0; c < 8; c++)
                boxes[r][c] = new Spot(initialBoxes[r][c]);
    }

    // ── Standard accessors ────────────────────────────────────────────────────

    public Spot getEnPassantTargetSquare() { return enPassantTargetSquare; }

    public void setEnPassantTargetSquare(Spot s) { this.enPassantTargetSquare = s; }

    /** Deep-copy constructor (used by AI game copies). */
    public Board(Board other) {
        this.enPassantTargetSquare = other.enPassantTargetSquare;
        this.boxes = new Spot[8][8];
        for (int r = 0; r < 8; r++)
            for (int c = 0; c < 8; c++)
                this.boxes[r][c] = new Spot(other.boxes[r][c]);
        // Copy the initial snapshot too so that AI copies can navigate history
        if (other.initialBoxes != null) {
            this.initialBoxes = new Spot[8][8];
            for (int r = 0; r < 8; r++)
                for (int c = 0; c < 8; c++)
                    this.initialBoxes[r][c] = new Spot(other.initialBoxes[r][c]);
        }
    }
}
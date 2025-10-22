package org.pocketchess.ui.boardpack;

import org.pocketchess.core.pieces.Spot;

import java.awt.*;

/**
 * Converts coordinates between display and logical systems.
 */
public class BoardCoordinateHelper {
    private final int tileSize;
    private boolean isFlipped;

    public BoardCoordinateHelper(int tileSize) {
        this.tileSize = tileSize;
        this.isFlipped = false;
    }

    public void setFlipped(boolean flipped) {
        this.isFlipped = flipped;
    }

    /**
     * Converts display coordinates to logical board coordinates.
     */
    public int[] displayToLogical(int displayCol, int displayRow) {
        int logicalRow = isFlipped ? 7 - displayRow : displayRow;
        int logicalCol = isFlipped ? 7 - displayCol : displayCol;
        return new int[]{logicalRow, logicalCol};
    }

    /**
     * Converts a logical Spot to display pixel coordinates.
     */
    public Point spotToDisplay(Spot spot) {
        int logicalRow = spot.getX();
        int logicalCol = spot.getY();
        int displayRow = isFlipped ? 7 - logicalRow : logicalRow;
        int displayCol = isFlipped ? 7 - logicalCol : logicalCol;
        return new Point(displayCol * tileSize, displayRow * tileSize);
    }

    /**
     * Validates that coordinates are within board bounds.
     */
    public boolean isValidPosition(int row, int col) {
        return row < 0 || row > 7 || col < 0 || col > 7;
    }
}
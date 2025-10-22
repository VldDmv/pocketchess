package org.pocketchess.ui.boardpack;

import org.pocketchess.core.pieces.Piece;
import org.pocketchess.core.pieces.Spot;

import java.awt.*;

/**
 * Stores UI interaction state for the board.
 */
public class BoardState {
    /**
     * Square selected by clicking (for click-to-move)
     */
    public Spot selectedSpot = null;

    /**
     * Piece currently being dragged
     */
    public Piece draggedPiece = null;

    /**
     * Image of dragged piece (follows mouse)
     */
    public Image draggedPieceImage = null;

    /**
     * Current drag position
     */
    public int dragX, dragY;

    /**
     * Square where drag started
     */
    public Spot sourceSpot = null;

    /**
     * Whether user is actively dragging
     */
    public boolean isDragging = false;

    /**
     * Highlight source square during drag
     */
    public Spot dragHighlightSpot = null;
}

package org.pocketchess.ui.boardpack;

import org.pocketchess.core.game.GameStatus;
import org.pocketchess.core.game.moveanalyze.Move;
import org.pocketchess.core.general.Game;
import org.pocketchess.core.pieces.Piece;
import org.pocketchess.core.pieces.Spot;
import org.pocketchess.ui.gameframepack.piecesandclock.ImageLoader;

import java.awt.*;

/**
 * Renders the chess board and all visual elements
 * RENDERING LAYERS (in order):
 * 1. Board squares (light/dark pattern)
 * 2. Highlights (last move, selection, check, drag)
 * 3. Legal move indicators (dots)
 * 4. Pieces (on their squares)
 * 5. Game ended overlay (semi-transparent)
 * 6. Dragged piece (follows mouse)
 */
public class BoardRenderer {
    private final Game game;
    private final BoardCoordinateHelper coordHelper;
    private final BoardState boardState;
    private final int tileSize;

    // Color constants
    private static final Color LIGHT_SQUARE = new Color(238, 238, 210);
    private static final Color DARK_SQUARE = new Color(118, 150, 86);
    private static final Color LAST_MOVE_HIGHLIGHT = new Color(186, 202, 68, 120);
    private static final Color SELECTED_HIGHLIGHT = new Color(80, 140, 200, 150);
    private static final Color DRAG_HIGHLIGHT = new Color(0, 0, 0, 90);
    private static final Color LEGAL_MOVE_DOT = new Color(0, 0, 0, 40);
    private static final Color CHECK_HIGHLIGHT = new Color(255, 0, 0, 90);
    private static final Color GAME_ENDED_OVERLAY = new Color(0, 0, 0, 70);

    public BoardRenderer(Game game, BoardCoordinateHelper coordHelper,
                         BoardState boardState, int tileSize) {
        this.game = game;
        this.coordHelper = coordHelper;
        this.boardState = boardState;
        this.tileSize = tileSize;
    }

    /**
     * Main render method - draws everything.
     */
    public void render(Graphics2D g2d, Component component) {
        // Enable anti-aliasing for smooth rendering
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);

        // Draw in layers
        drawBoard(g2d);
        drawHighlights(g2d);
        drawPieces(g2d, component);
        drawGameEndedOverlay(g2d, component);
        drawDraggedPiece(g2d, component);
    }

    /**
     * Draws the checkerboard pattern.
     */
    private void drawBoard(Graphics2D g2d) {
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                g2d.setColor((row + col) % 2 == 0 ? LIGHT_SQUARE : DARK_SQUARE);
                g2d.fillRect(col * tileSize, row * tileSize, tileSize, tileSize);
            }
        }
    }

    /**
     * Draws all pieces on the board.
     */
    private void drawPieces(Graphics2D g2d, Component component) {
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                Spot spot = game.getBoard().getBox(row, col);
                Piece piece = spot.getPiece();
                if (piece != null) {
                    Image image = ImageLoader.getImageForPiece(piece);
                    if (image != null) {
                        Point displayPoint = coordHelper.spotToDisplay(spot);
                        g2d.drawImage(image, displayPoint.x, displayPoint.y,
                                tileSize, tileSize, component);
                    }
                }
            }
        }
    }

    /**
     * Draws all highlights (last move, selection, check, etc.).
     */
    private void drawHighlights(Graphics2D g2d) {
        drawLastMoveHighlight(g2d);
        drawDragHighlight(g2d);
        drawSelectedSpotHighlight(g2d);
        drawCheckHighlight(g2d);
    }

    /**
     * Highlights the last move (source and destination squares).
     */
    private void drawLastMoveHighlight(Graphics2D g2d) {
        Move lastMove = game.getLastMove();
        if (lastMove != null) {
            Point startPoint = coordHelper.spotToDisplay(lastMove.start);
            Point endPoint = coordHelper.spotToDisplay(lastMove.end);
            g2d.setColor(LAST_MOVE_HIGHLIGHT);
            g2d.fillRect(startPoint.x, startPoint.y, tileSize, tileSize);
            g2d.fillRect(endPoint.x, endPoint.y, tileSize, tileSize);
        }
    }

    /**
     * Highlights the square where drag started
     */
    private void drawDragHighlight(Graphics2D g2d) {
        if (boardState.isDragging && boardState.dragHighlightSpot != null) {
            Point highlightPoint = coordHelper.spotToDisplay(boardState.dragHighlightSpot);
            g2d.setColor(DRAG_HIGHLIGHT);
            g2d.fillRect(highlightPoint.x, highlightPoint.y, tileSize, tileSize);
        }
    }

    /**
     * Highlights the selected square and shows legal moves
     */
    private void drawSelectedSpotHighlight(Graphics2D g2d) {
        if (boardState.selectedSpot != null) {
            Point selectedPoint = coordHelper.spotToDisplay(boardState.selectedSpot);
            g2d.setColor(SELECTED_HIGHLIGHT);
            g2d.fillRect(selectedPoint.x, selectedPoint.y, tileSize, tileSize);

            // Show legal move indicators only in live mode
            if (game.isLive()) {
                drawLegalMoveIndicators(g2d);
            }
        }
    }

    /**
     * Draws dots on squares where selected piece can move.
     */
    private void drawLegalMoveIndicators(Graphics2D g2d) {
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                Spot targetSpot = game.getBoard().getBox(row, col);
                if (game.isMoveLegal(boardState.selectedSpot, targetSpot)) {
                    Point targetPoint = coordHelper.spotToDisplay(targetSpot);
                    g2d.setColor(LEGAL_MOVE_DOT);
                    g2d.fillOval(
                            targetPoint.x + tileSize / 3,
                            targetPoint.y + tileSize / 3,
                            tileSize / 3,
                            tileSize / 3
                    );
                }
            }
        }
    }

    /**
     * Highlights the king in check with red circle.
     */
    private void drawCheckHighlight(Graphics2D g2d) {
        if (game.getStatus() == GameStatus.CHECK && game.isLive()) {
            Spot kingSpot = game.findKing(game.isWhiteTurn());
            if (kingSpot != null) {
                Point kingPoint = coordHelper.spotToDisplay(kingSpot);
                g2d.setColor(CHECK_HIGHLIGHT);
                g2d.fillOval(kingPoint.x, kingPoint.y, tileSize, tileSize);
            }
        }
    }

    /**
     * Draws semi-transparent overlay when viewing history.
     */
    private void drawGameEndedOverlay(Graphics2D g2d, Component component) {
        if (!game.isLive()) {
            g2d.setColor(GAME_ENDED_OVERLAY);
            g2d.fillRect(0, 0, component.getWidth(), component.getHeight());
        }
    }


    private void drawDraggedPiece(Graphics2D g2d, Component component) {
        if (boardState.isDragging && boardState.draggedPieceImage != null) {
            g2d.drawImage(
                    boardState.draggedPieceImage,
                    boardState.dragX,
                    boardState.dragY,
                    tileSize,
                    tileSize,
                    component
            );
        }
    }
}
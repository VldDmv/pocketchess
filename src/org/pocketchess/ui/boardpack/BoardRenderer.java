package org.pocketchess.ui.boardpack;

import org.pocketchess.core.game.model.GameStatus;
import org.pocketchess.core.game.moveanalyze.Move;
import org.pocketchess.core.general.Game;
import org.pocketchess.core.gamemode.LavaManager;
import org.pocketchess.core.pieces.Piece;
import org.pocketchess.core.pieces.Spot;
import org.pocketchess.ui.gameframepack.piecesandclock.ImageLoader;

import java.awt.*;

/**
 * Renders the chess board and all visual elements.
 *
 * RENDERING LAYERS (in order):
 * 1.  Board squares (light / dark checkerboard)
 * 2.  Last-move highlight
 * 3.  Warning squares  ← BLUE  (lava mode: will become lava next interval)
 * 4.  Lava squares     ← RED   (lava mode: impassable, kills pieces)
 * 5.  Drag highlight
 * 6.  Selected square + legal-move dots
 * 7.  Check highlight (king in check)
 * 8.  Pieces
 * 9.  History-review overlay (semi-transparent dark veil)
 * 10. Dragged piece (follows cursor)
 */
public class BoardRenderer {
    private final Game game;
    private final BoardCoordinateHelper coordHelper;
    private final BoardState boardState;
    private final int tileSize;

    // ── Standard colours ────────────────────────────────────────────────────
    private static final Color LIGHT_SQUARE      = new Color(238, 238, 210);
    private static final Color DARK_SQUARE       = new Color(118, 150,  86);
    private static final Color LAST_MOVE_HIGHLIGHT = new Color(186, 202,  68, 120);
    private static final Color SELECTED_HIGHLIGHT  = new Color( 80, 140, 200, 150);
    private static final Color DRAG_HIGHLIGHT      = new Color(  0,   0,   0,  90);
    private static final Color LEGAL_MOVE_DOT      = new Color(  0,   0,   0,  40);
    private static final Color CHECK_HIGHLIGHT     = new Color(255,   0,   0,  90);
    private static final Color GAME_ENDED_OVERLAY  = new Color(  0,   0,   0,  70);

    // ── Lava-mode colours ────────────────────────────────────────────────────
    /** Active lava square – bright red, semi-transparent */
    private static final Color LAVA_COLOR    = new Color(220,  50,  20, 160);
    /** Lava border to make it pop */
    private static final Color LAVA_BORDER   = new Color(255, 120,   0, 200);
    /** Warning square – blue, semi-transparent */
    private static final Color WARNING_COLOR = new Color( 30, 120, 255, 100);
    /** Warning border */
    private static final Color WARNING_BORDER = new Color( 80, 160, 255, 180);

    public BoardRenderer(Game game, BoardCoordinateHelper coordHelper,
                         BoardState boardState, int tileSize) {
        this.game        = game;
        this.coordHelper = coordHelper;
        this.boardState  = boardState;
        this.tileSize    = tileSize;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Entry point
    // ─────────────────────────────────────────────────────────────────────────

    public void render(Graphics2D g2d, Component component) {
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,   RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION,  RenderingHints.VALUE_INTERPOLATION_BICUBIC);

        drawBoard(g2d);
        drawLastMoveHighlight(g2d);

        // Lava visuals (only in live mode so history navigation is uncluttered)
        if (game.isLive() && game.isLavaMode()) {
            drawWarningSquares(g2d);
            drawLavaSquares(g2d);
        }

        drawDragHighlight(g2d);
        drawSelectedSpotHighlight(g2d);
        drawCheckHighlight(g2d);
        drawPieces(g2d, component);
        drawGameEndedOverlay(g2d, component);
        drawDraggedPiece(g2d, component);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Board & pieces
    // ─────────────────────────────────────────────────────────────────────────

    private void drawBoard(Graphics2D g2d) {
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                g2d.setColor((row + col) % 2 == 0 ? LIGHT_SQUARE : DARK_SQUARE);
                g2d.fillRect(col * tileSize, row * tileSize, tileSize, tileSize);
            }
        }
    }

    private void drawPieces(Graphics2D g2d, Component component) {
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                Spot  spot  = game.getBoard().getBox(row, col);
                Piece piece = spot.getPiece();
                if (piece != null) {
                    Image image = ImageLoader.getImageForPiece(piece);
                    if (image != null) {
                        Point pt = coordHelper.spotToDisplay(spot);
                        g2d.drawImage(image, pt.x, pt.y, tileSize, tileSize, component);
                    }
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Lava rendering
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Blue highlight for squares that WILL become lava next interval.
     * A pulsing border is drawn to make them clearly visible.
     */
    private void drawWarningSquares(Graphics2D g2d) {
        LavaManager lm = game.getLavaManager();

        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                if (!lm.isWarning(row, col)) continue;

                Point pt = displayPoint(row, col);

                // Fill
                g2d.setColor(WARNING_COLOR);
                g2d.fillRect(pt.x, pt.y, tileSize, tileSize);

                // Border
                g2d.setColor(WARNING_BORDER);
                g2d.setStroke(new BasicStroke(3f));
                g2d.drawRect(pt.x + 2, pt.y + 2, tileSize - 4, tileSize - 4);
                g2d.setStroke(new BasicStroke(1f));

                // Small snowflake / warning icon drawn as a cross in the centre
                drawWarningIcon(g2d, pt);
            }
        }
    }

    /**
     * Red highlight for currently active lava squares.
     * Pieces drawn on top will appear to "stand in lava".
     */
    private void drawLavaSquares(Graphics2D g2d) {
        LavaManager lm = game.getLavaManager();

        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                if (!lm.isLava(row, col)) continue;

                Point pt = displayPoint(row, col);

                // Fill
                g2d.setColor(LAVA_COLOR);
                g2d.fillRect(pt.x, pt.y, tileSize, tileSize);

                // Fiery border
                g2d.setColor(LAVA_BORDER);
                g2d.setStroke(new BasicStroke(4f));
                g2d.drawRect(pt.x + 1, pt.y + 1, tileSize - 2, tileSize - 2);
                g2d.setStroke(new BasicStroke(1f));

                // Flame icon in the centre
                drawLavaIcon(g2d, pt);
            }
        }
    }

    /** Small "!" in the centre of a warning square */
    private void drawWarningIcon(Graphics2D g2d, Point pt) {
        g2d.setColor(new Color(200, 230, 255, 220));
        g2d.setFont(new Font("SansSerif", Font.BOLD, tileSize / 3));
        FontMetrics fm = g2d.getFontMetrics();
        String text = "!";
        int tx = pt.x + (tileSize - fm.stringWidth(text)) / 2;
        int ty = pt.y + (tileSize + fm.getAscent() - fm.getDescent()) / 2;
        g2d.drawString(text, tx, ty);
    }

    /** Flame emoji / symbol in the centre of a lava square */
    private void drawLavaIcon(Graphics2D g2d, Point pt) {
        g2d.setColor(new Color(255, 200, 50, 230));
        g2d.setFont(new Font("SansSerif", Font.BOLD, tileSize / 3));
        FontMetrics fm = g2d.getFontMetrics();
        String text = "🔥";
        int tx = pt.x + (tileSize - fm.stringWidth(text)) / 2;
        int ty = pt.y + (tileSize + fm.getAscent() - fm.getDescent()) / 2;
        g2d.drawString(text, tx, ty);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Standard highlights
    // ─────────────────────────────────────────────────────────────────────────

    private void drawLastMoveHighlight(Graphics2D g2d) {
        Move lastMove = game.getLastMove();
        if (lastMove == null) return;
        g2d.setColor(LAST_MOVE_HIGHLIGHT);
        Point sp = coordHelper.spotToDisplay(lastMove.start);
        Point ep = coordHelper.spotToDisplay(lastMove.end);
        g2d.fillRect(sp.x, sp.y, tileSize, tileSize);
        g2d.fillRect(ep.x, ep.y, tileSize, tileSize);
    }

    private void drawDragHighlight(Graphics2D g2d) {
        if (boardState.isDragging && boardState.dragHighlightSpot != null) {
            Point pt = coordHelper.spotToDisplay(boardState.dragHighlightSpot);
            g2d.setColor(DRAG_HIGHLIGHT);
            g2d.fillRect(pt.x, pt.y, tileSize, tileSize);
        }
    }

    private void drawSelectedSpotHighlight(Graphics2D g2d) {
        if (boardState.selectedSpot == null) return;

        Point pt = coordHelper.spotToDisplay(boardState.selectedSpot);
        g2d.setColor(SELECTED_HIGHLIGHT);
        g2d.fillRect(pt.x, pt.y, tileSize, tileSize);

        if (game.isLive()) {
            drawLegalMoveIndicators(g2d);
        }
    }

    private void drawLegalMoveIndicators(Graphics2D g2d) {
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                Spot target = game.getBoard().getBox(row, col);
                if (game.isMoveLegal(boardState.selectedSpot, target)) {
                    Point pt = coordHelper.spotToDisplay(target);
                    g2d.setColor(LEGAL_MOVE_DOT);
                    g2d.fillOval(pt.x + tileSize / 3, pt.y + tileSize / 3,
                            tileSize / 3, tileSize / 3);
                }
            }
        }
    }

    private void drawCheckHighlight(Graphics2D g2d) {
        if (game.getStatus() == GameStatus.CHECK && game.isLive()) {
            Spot kingSpot = game.findKing(game.isWhiteTurn());
            if (kingSpot != null) {
                Point pt = coordHelper.spotToDisplay(kingSpot);
                g2d.setColor(CHECK_HIGHLIGHT);
                g2d.fillOval(pt.x, pt.y, tileSize, tileSize);
            }
        }
    }

    private void drawGameEndedOverlay(Graphics2D g2d, Component component) {
        if (!game.isLive()) {
            g2d.setColor(GAME_ENDED_OVERLAY);
            g2d.fillRect(0, 0, component.getWidth(), component.getHeight());
        }
    }

    private void drawDraggedPiece(Graphics2D g2d, Component component) {
        if (boardState.isDragging && boardState.draggedPieceImage != null) {
            g2d.drawImage(boardState.draggedPieceImage,
                    boardState.dragX, boardState.dragY,
                    tileSize, tileSize, component);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns the top-left pixel of the given logical square,
     * respecting board flip.
     */
    private Point displayPoint(int logicalRow, int logicalCol) {
        Spot spot = game.getBoard().getBox(logicalRow, logicalCol);
        return coordHelper.spotToDisplay(spot);
    }
}
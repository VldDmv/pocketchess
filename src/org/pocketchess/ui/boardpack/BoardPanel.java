package org.pocketchess.ui.boardpack;

import org.pocketchess.core.general.Game;
import org.pocketchess.ui.gameframepack.frame.GameFrame;

import javax.swing.*;
import java.awt.*;

/**
 * Main chess board component - visual representation of the game.
 */
public class BoardPanel extends JPanel {
    private static final int TILE_SIZE = 80;

    private final Game game;

    // Helper components
    private final BoardCoordinateHelper coordHelper;
    private final BoardState boardState;
    private final BoardRenderer renderer;
    private final BoardMouseHandler mouseHandler;

    public BoardPanel(GameFrame gameFrame) {
        setPreferredSize(new Dimension(8 * TILE_SIZE, 8 * TILE_SIZE));
        this.game = gameFrame.getGame();

        // Initialize helper classes
        this.coordHelper = new BoardCoordinateHelper(TILE_SIZE);
        this.boardState = new BoardState();
        this.renderer = new BoardRenderer(game, coordHelper, boardState, TILE_SIZE);
        this.mouseHandler = new BoardMouseHandler(
                gameFrame,
                game,
                coordHelper,
                boardState,
                this::repaint,
                TILE_SIZE
        );

        // Register mouse event handlers
        addMouseListener(mouseHandler);
        addMouseMotionListener(mouseHandler);
    }

    /**
     * Flips the board (for playing as Black).
     */
    public void setFlipped(boolean flipped) {
        coordHelper.setFlipped(flipped);
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        renderer.render((Graphics2D) g, this);
    }
}
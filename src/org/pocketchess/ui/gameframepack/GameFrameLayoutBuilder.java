package org.pocketchess.ui.gameframepack;

import javax.swing.*;
import java.awt.*;

/**
 * Factory class for creating UI components for GameFrame.
 */
public class GameFrameLayoutBuilder {

    /**
     * Creates game status label.
     */
    public static JLabel createStatusLabel() {
        JLabel label = new JLabel("Game start");
        label.setFont(new Font("Arial", Font.BOLD, 16));
        return label;
    }

    /**
     * Creates west panel (for clocks and captured pieces).
     */
    public static JPanel createWestPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setPreferredSize(new Dimension(180, 0));
        return panel;
    }

    /**
     * Creates action buttons panel (undo, flip, draw, resign).
     */
    public static JPanel createActionsPanel(
            Runnable onUndo,
            Runnable onFlipBoard,
            Runnable onOfferDraw,
            Runnable onResign) {
        JPanel panel = new JPanel(new GridLayout(4, 1, 0, 10));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(20, 0, 20, 0));


        JButton undoButton = IconButton.createIconButton("⤺", "Undo move", new Color(52, 152, 219));
        JButton flipBoardButton = IconButton.createIconButton("⟲", "Flip board", new Color(155, 89, 182));
        JButton offerDrawButton = IconButton.createIconButton("½", "Offer draw", new Color(241, 196, 15));
        JButton resignButton = IconButton.createIconButton("⚑", "Resign", new Color(231, 76, 60));

        panel.add(undoButton);
        panel.add(flipBoardButton);
        panel.add(offerDrawButton);
        panel.add(resignButton);

        undoButton.addActionListener(e -> onUndo.run());
        flipBoardButton.addActionListener(e -> onFlipBoard.run());
        offerDrawButton.addActionListener(e -> onOfferDraw.run());
        resignButton.addActionListener(e -> onResign.run());

        return panel;
    }

    /**
     * Creates top panel with status, menu, and new game button.
     */
    public static JPanel createTopPanel(
            JLabel statusLabel,
            Runnable onNewGame,
            Runnable onLoadPgn,
            Runnable onExportPgn) {
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.add(statusLabel);

        JButton newGameButton = new JButton("New game");
        newGameButton.addActionListener(e -> onNewGame.run());
        topPanel.add(newGameButton);

        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("PGN");

        JMenuItem loadPgnItem = new JMenuItem("Load PGN...");
        loadPgnItem.addActionListener(e -> onLoadPgn.run());
        fileMenu.add(loadPgnItem);

        JMenuItem exportPgnItem = new JMenuItem("Export PGN...");
        exportPgnItem.addActionListener(e -> onExportPgn.run());
        fileMenu.add(exportPgnItem);

        menuBar.add(fileMenu);
        topPanel.add(menuBar);

        return topPanel;
    }

    /**
     * Updates button states based on game state.
     */
    public static void updateButtonStates(
            JPanel actionsPanel,
            boolean isLive,
            boolean isGameOver,
            boolean hasMoves,
            boolean isPvP
    ) {
        JButton undoButton = (JButton) actionsPanel.getComponent(0);
        JButton offerDrawButton = (JButton) actionsPanel.getComponent(2);
        JButton resignButton = (JButton) actionsPanel.getComponent(3);

        undoButton.setEnabled(isLive && hasMoves);
        resignButton.setEnabled(isLive && !isGameOver);
        offerDrawButton.setEnabled(isPvP && isLive && !isGameOver);
    }

    /**
     * Updates draw button text (Offer ↔ Accept).
     */
    public static void updateDrawButton(JPanel actionsPanel, boolean isDrawOffered, boolean isGameOver) {
        JButton offerDrawButton = (JButton) actionsPanel.getComponent(2);

        if (isDrawOffered && !isGameOver) {
            offerDrawButton.setText("✓");
            offerDrawButton.setToolTipText("Accept draw");
        } else {
            offerDrawButton.setText("½");
            offerDrawButton.setToolTipText("Offer draw");
        }
    }
}

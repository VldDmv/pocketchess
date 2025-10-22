package org.pocketchess.ui.gameframepack.piecesandclock;

import org.pocketchess.core.game.TimeControl;
import org.pocketchess.core.pieces.Piece;
import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * Manages the player panels on the side of the board.
 */
public class PlayerPanelManager {
    private final JLabel whiteClockLabel;
    private final JLabel blackClockLabel;
    private final CapturedPiecesPanel whiteCapturedPanel;
    private final CapturedPiecesPanel blackCapturedPanel;
    private final JPanel westPanel;      // The container for all player panels
    private final JPanel actionsPanel;   // Panel with game control buttons

    /**
     * Creates the player panel manager.
     * Initializes clocks and captured piece panels.
     */
    public PlayerPanelManager(JPanel westPanel, JPanel actionsPanel) {
        this.westPanel = westPanel;
        this.actionsPanel = actionsPanel;

        // Initialize UI components
        this.blackClockLabel = createClockLabel();
        this.blackCapturedPanel = new CapturedPiecesPanel();
        this.whiteClockLabel = createClockLabel();
        this.whiteCapturedPanel = new CapturedPiecesPanel();
    }

    /**
     * Creates a styled label for displaying time.
     */
    private JLabel createClockLabel() {
        JLabel label = new JLabel("00:00", SwingConstants.CENTER);
        label.setFont(new Font("Arial", Font.BOLD, 24));
        return label;
    }

    /**
     * Updates the layout of player panels.
     */
    public void updatePlayerPanels(boolean isBoardFlipped, TimeControl timeControl) {
        westPanel.removeAll();
        westPanel.setBackground(new Color(240, 240, 240));
        westPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        // Create containers for top and bottom player
        JPanel topPlayerPanel = new JPanel(new BorderLayout());
        topPlayerPanel.setOpaque(false);
        JPanel bottomPlayerPanel = new JPanel(new BorderLayout());
        bottomPlayerPanel.setOpaque(false);

        // Hide clocks if time is unlimited
        boolean isTimeUnlimited = timeControl.isUnlimited();
        whiteClockLabel.setVisible(!isTimeUnlimited);
        blackClockLabel.setVisible(!isTimeUnlimited);

        // Arrange panels based on board orientation
        if (isBoardFlipped) {
            // Board flipped: White on top, Black on bottom
            topPlayerPanel.add(whiteClockLabel, BorderLayout.NORTH);
            topPlayerPanel.add(whiteCapturedPanel, BorderLayout.CENTER);
            bottomPlayerPanel.add(blackCapturedPanel, BorderLayout.CENTER);
            bottomPlayerPanel.add(blackClockLabel, BorderLayout.SOUTH);
        } else {
            // Normal view: Black on top, White on bottom
            topPlayerPanel.add(blackClockLabel, BorderLayout.NORTH);
            topPlayerPanel.add(blackCapturedPanel, BorderLayout.CENTER);
            bottomPlayerPanel.add(whiteCapturedPanel, BorderLayout.CENTER);
            bottomPlayerPanel.add(whiteClockLabel, BorderLayout.SOUTH);
        }

        // Add panels to main container
        westPanel.add(topPlayerPanel, BorderLayout.NORTH);

        // Center the action buttons vertically
        JPanel middleContainer = new JPanel(new GridBagLayout());
        middleContainer.setOpaque(false);
        middleContainer.add(actionsPanel);
        westPanel.add(middleContainer, BorderLayout.CENTER);

        westPanel.add(bottomPlayerPanel, BorderLayout.SOUTH);

        westPanel.revalidate();
        westPanel.repaint();
    }

    /**
     * Updates the clock displays with new time values.
     */
    public void updateClocks(String whiteTime, String blackTime) {
        whiteClockLabel.setText(whiteTime);
        blackClockLabel.setText(blackTime);
    }

    /**
     * Updates the captured pieces displays.
     */
    public void updateCapturedPieces(List<Piece> whiteCaptured, List<Piece> blackCaptured) {
        whiteCapturedPanel.update(blackCaptured);
        blackCapturedPanel.update(whiteCaptured);
    }
}
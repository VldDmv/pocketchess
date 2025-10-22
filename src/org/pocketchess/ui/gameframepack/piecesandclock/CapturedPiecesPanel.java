package org.pocketchess.ui.gameframepack.piecesandclock;

import org.pocketchess.core.pieces.Piece;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * Panel that displays captured pieces for one player.
 */
public class CapturedPiecesPanel extends JPanel {
    private final int IMAGE_SIZE = 30;  // Size of each piece icon

    public CapturedPiecesPanel() {
        // Use custom WrapLayout that automatically wraps to new rows
        super(new WrapLayout(FlowLayout.LEFT, 2, 2));

        int panelHeight = 5 * (IMAGE_SIZE + 2);
        setPreferredSize(new Dimension(80, panelHeight));

        setBackground(new Color(211, 211, 211));
        setAlignmentY(Component.TOP_ALIGNMENT);
    }

    /**
     * Updates the panel to show the current list of captured pieces.
     */
    public void update(List<Piece> capturedPieces) {
        this.removeAll();  // Clear existing icons

        // Add an icon for each captured piece
        for (Piece piece : capturedPieces) {
            Image originalImage = ImageLoader.getImageForPiece(piece);
            if (originalImage != null) {
                // Scale image to fit the panel
                Image scaledImage = originalImage.getScaledInstance(IMAGE_SIZE, IMAGE_SIZE, Image.SCALE_SMOOTH);
                ImageIcon icon = new ImageIcon(scaledImage);
                this.add(new JLabel(icon));
            }
        }
        this.revalidate();  // Recalculate layout
        this.repaint();     // Redraw the panel
    }
}
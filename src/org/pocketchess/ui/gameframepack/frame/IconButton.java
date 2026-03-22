package org.pocketchess.ui.gameframepack.frame;

import javax.swing.*;
import java.awt.*;

/**
 * Factory for creating custom circular icon buttons.
 */
public class IconButton {
    private static final Font SYMBOL_FONT = getSymbolFont();

    /**
     * Finds the best available font for displaying symbols/emojis.
     */
    private static Font getSymbolFont() {
        // Preferred fonts (in order)
        String[] preferredFonts = {"Segoe UI Symbol", "Segoe UI Emoji", "Apple Color Emoji",
                "Noto Color Emoji", "Arial Unicode MS"};
        Font[] allFonts = GraphicsEnvironment.getLocalGraphicsEnvironment().getAllFonts();

        // Try to find a preferred font
        for (String preferredFont : preferredFonts) {
            for (Font font : allFonts) {
                if (font.getName().equals(preferredFont)) {
                    return new Font(preferredFont, Font.BOLD, 20);
                }
            }
        }

        // Fallback to system fonts
        String[] systemFonts = {"Dialog", "SansSerif", "Arial"};
        for (String systemFont : systemFonts) {
            Font testFont = new Font(systemFont, Font.BOLD, 32);
            if (testFont.getFamily().equals(systemFont)) {
                return testFont;
            }
        }

        // Last resort
        return new Font(Font.SANS_SERIF, Font.BOLD, 20);
    }

    /**
     * Creates a custom circular icon button.
     */
    public static JButton createIconButton(String symbol, String tooltip, Color bgColor) {
        JButton button = new JButton() {
            @Override
            protected void paintComponent(Graphics g) {


                Graphics2D g2d = (Graphics2D) g.create();

                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                // Calculate circle size and position
                int size = Math.min(getWidth(), getHeight()) - 8;
                int x = (getWidth() - size) / 2;
                int y = (getHeight() - size) / 2;

                // Determine color based on button state
                Color currentBgColor;
                if (!isEnabled()) {
                    currentBgColor = bgColor.darker().darker();
                } else if (getModel().isPressed()) {
                    currentBgColor = bgColor.darker();
                } else if (getModel().isRollover()) {
                    currentBgColor = bgColor.brighter();
                } else {
                    currentBgColor = bgColor;
                }

                // Draw circle background
                g2d.setColor(currentBgColor);
                g2d.fillOval(x, y, size, size);

                // Draw circle border
                g2d.setColor(currentBgColor.darker().darker());
                g2d.setStroke(new BasicStroke(2.0f));
                g2d.drawOval(x, y, size, size);

                // Draw symbol text centered
                g2d.setFont(SYMBOL_FONT);
                g2d.setColor(isEnabled() ? Color.WHITE : Color.LIGHT_GRAY);

                FontMetrics fm = g2d.getFontMetrics();
                int textWidth = fm.stringWidth(symbol);
                int textHeight = fm.getAscent();
                int textX = (getWidth() - textWidth) / 2;
                int textY = (getHeight() - fm.getDescent()) / 2 + textHeight / 2;

                g2d.drawString(symbol, textX, textY);
                g2d.dispose();
            }

            @Override
            public Dimension getPreferredSize() {
                return new Dimension(45, 45);
            }

            @Override
            public Dimension getMinimumSize() {
                return getPreferredSize();
            }

            @Override
            public Dimension getMaximumSize() {
                return getPreferredSize();
            }
        };

        // Configure button properties
        button.setToolTipText(tooltip);
        button.setBorder(null);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        button.setOpaque(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));

        button.setPreferredSize(new Dimension(45, 45));
        button.setMinimumSize(new Dimension(45, 45));
        button.setMaximumSize(new Dimension(45, 45));

        return button;
    }
}
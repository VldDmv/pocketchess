package org.pocketchess.ui.gameframepack.frame;

import org.pocketchess.core.ai.difficulty.AIDifficulty;

import javax.swing.*;
import java.awt.*;

/**
 * Dialog for selecting AI difficulty when starting PVE game.
 * DIFFICULTY LEVELS:
 * - EASY (~800-1200 ELO): Makes mistakes, good for beginners
 * - MEDIUM (~1200-1600 ELO): Intermediate play
 * - HARD (~1800-2400 ELO): Strong, challenging opponent
 */
public class DifficultySelectionDialog extends JDialog {
    private AIDifficulty selectedDifficulty = AIDifficulty.MEDIUM;
    private boolean confirmed = false;

    public DifficultySelectionDialog(JFrame parent) {
        super(parent, "Select AI Difficulty", true);
        setLayout(new BorderLayout(10, 10));
        setResizable(false);

        JPanel titlePanel = new JPanel();
        JLabel titleLabel = new JLabel("Choose AI Difficulty");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 16));
        titlePanel.add(titleLabel);
        add(titlePanel, BorderLayout.NORTH);

        JPanel difficultyPanel = new JPanel();
        difficultyPanel.setLayout(new GridLayout(3, 1, 10, 10));
        difficultyPanel.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));

        difficultyPanel.add(createDifficultyButton(AIDifficulty.EASY));
        difficultyPanel.add(createDifficultyButton(AIDifficulty.MEDIUM));
        difficultyPanel.add(createDifficultyButton(AIDifficulty.HARD));

        add(difficultyPanel, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel();
        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> {
            confirmed = false;
            dispose();
        });
        bottomPanel.add(cancelButton);
        add(bottomPanel, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(parent);
    }

    /**
     * Creates a difficulty selection button with description.
     */
    private JButton createDifficultyButton(AIDifficulty difficulty) {
        JButton button = new JButton();
        button.setLayout(new BorderLayout());
        button.setPreferredSize(new Dimension(300, 80));


        JLabel mainLabel = new JLabel(difficulty.getDisplayName(), SwingConstants.CENTER);
        mainLabel.setFont(new Font("Arial", Font.BOLD, 18));

        JLabel descLabel = getjLabel(difficulty);

        JPanel textPanel = new JPanel(new BorderLayout(5, 5));
        textPanel.setOpaque(false);
        textPanel.add(mainLabel, BorderLayout.NORTH);
        textPanel.add(descLabel, BorderLayout.CENTER);

        button.add(textPanel, BorderLayout.CENTER);

        button.addActionListener(e -> {
            selectedDifficulty = difficulty;
            confirmed = true;
            dispose();
        });

        return button;
    }


    private static JLabel getjLabel(AIDifficulty difficulty) {
        String description = switch (difficulty) {
            case EASY -> "<html><center>Makes occasional mistakes</center></html>";
            case MEDIUM -> "<html><center>Good for intermediate players</center></html>";
            case HARD -> "<html><center>Challenging opponent</center></html>";
        };
        JLabel descLabel = new JLabel(description, SwingConstants.CENTER);
        descLabel.setFont(new Font("Arial", Font.PLAIN, 11));
        return descLabel;
    }

    public AIDifficulty getSelectedDifficulty() {
        return selectedDifficulty;
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    /**
     * Static helper - shows dialog and returns result.
     */
    public static AIDifficulty showDialog(JFrame parent) {
        DifficultySelectionDialog dialog = new DifficultySelectionDialog(parent);
        dialog.setVisible(true);
        return dialog.isConfirmed() ? dialog.getSelectedDifficulty() : null;
    }
}
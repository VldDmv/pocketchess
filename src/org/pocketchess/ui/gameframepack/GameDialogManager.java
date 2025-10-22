package org.pocketchess.ui.gameframepack;

import org.pocketchess.core.game.GameMode;
import org.pocketchess.core.game.TimeControl;
import org.pocketchess.core.pieces.Piece;
import org.pocketchess.ui.gameframepack.piecesandclock.TimeSetupDialog;

import javax.swing.*;

/**
 * Manages all user dialogs for game setup and actions.
 * RESPONSIBILITIES:
 * - New game setup dialogs (mode, color, time)
 * - Resign confirmation
 * - PGN input/output messages
 */
public class GameDialogManager {
    private final JFrame parentFrame;

    public GameDialogManager(JFrame parentFrame) {
        this.parentFrame = parentFrame;
    }

    /**
     * Shows complete new game setup flow.
     */
    public GameSetupData promptForNewGame() {

        Object[] gameModes = {"Human versus Human", "Human versus AI"};
        int modeChoice = JOptionPane.showOptionDialog(
                parentFrame,
                "Choose game rules",
                "New Game",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                gameModes,
                gameModes[0]
        );

        if (modeChoice == JOptionPane.CLOSED_OPTION) return null;

        GameMode selectedMode = (modeChoice == 0) ? GameMode.PVP : GameMode.PVE;
        Piece.Color playerColor = Piece.Color.WHITE;

        if (selectedMode == GameMode.PVE) {
            Object[] colorOptions = {"Play as White", "Play as Black"};
            int colorChoice = JOptionPane.showOptionDialog(
                    parentFrame,
                    "Choose your color",
                    "Color",
                    JOptionPane.DEFAULT_OPTION,
                    JOptionPane.QUESTION_MESSAGE,
                    null,
                    colorOptions,
                    colorOptions[0]
            );
            if (colorChoice == 1) {
                playerColor = Piece.Color.BLACK;
            }
        }

        TimeControl selectedControl = TimeSetupDialog.showDialog(parentFrame);
        if (selectedControl == null) {
            return null;
        }

        return new GameSetupData(selectedControl, selectedMode, playerColor);
    }

    /**
     * Shows resign confirmation dialog.
     */
    public boolean confirmResign() {
        int choice = JOptionPane.showConfirmDialog(
                parentFrame,
                "Are you sure you want to resign?",
                "Resign",
                JOptionPane.YES_NO_OPTION
        );
        return choice == JOptionPane.YES_OPTION;
    }

    /**
     * Prompts user to paste PGN string.
     */
    public String promptForPgn() {
        return JOptionPane.showInputDialog(
                parentFrame,
                "Insert PGN here:",
                "Load PGN",
                JOptionPane.PLAIN_MESSAGE
        );
    }

    /**
     * Shows success message after PGN export.
     */
    public void showPgnExportSuccess() {
        JOptionPane.showMessageDialog(
                parentFrame,
                "PGN was copied to clipboard.",
                "Export PGN",
                JOptionPane.INFORMATION_MESSAGE
        );
    }

    /**
     * Data class for game setup parameters.
     */
    public static class GameSetupData {
        public final TimeControl timeControl;
        public final GameMode gameMode;
        public final Piece.Color playerColor;

        public GameSetupData(TimeControl timeControl, GameMode gameMode, Piece.Color playerColor) {
            this.timeControl = timeControl;
            this.gameMode = gameMode;
            this.playerColor = playerColor;
        }
    }

    public JFrame getParentFrame() {
        return parentFrame;
    }
}
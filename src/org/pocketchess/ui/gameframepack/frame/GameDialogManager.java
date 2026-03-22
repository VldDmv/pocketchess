package org.pocketchess.ui.gameframepack.frame;

import org.pocketchess.core.game.model.GameMode;
import org.pocketchess.core.game.model.TimeControl;
import org.pocketchess.core.gamemode.GameModeType;
import org.pocketchess.core.pieces.Piece;
import org.pocketchess.ui.gameframepack.piecesandclock.TimeSetupDialog;

import javax.swing.*;

/**
 * Manages all user dialogs for game setup and actions.
 *
 * New-game flow:
 *  1. Choose game mode  (PVP / PVE)
 *  2. Choose color      (PVE only)
 *  3. Choose time control
 *  4. Choose variant    (Classic | Chess 960 | Floor is Lava)
 */
public class GameDialogManager {
    private final JFrame parentFrame;

    public GameDialogManager(JFrame parentFrame) {
        this.parentFrame = parentFrame;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  New-game flow
    // ─────────────────────────────────────────────────────────────────────────

    public GameSetupData promptForNewGame() {

        // ── Step 1: game mode ────────────────────────────────────────────────
        Object[] gameModes = {"Human vs Human", "Human vs AI"};
        int modeChoice = JOptionPane.showOptionDialog(
                parentFrame, "Choose game mode", "New Game",
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, gameModes, gameModes[0]);

        if (modeChoice == JOptionPane.CLOSED_OPTION) return null;

        GameMode selectedMode = (modeChoice == 0) ? GameMode.PVP : GameMode.PVE;
        Piece.Color playerColor = Piece.Color.WHITE;

        // ── Step 2: color (PVE only) ─────────────────────────────────────────
        if (selectedMode == GameMode.PVE) {
            Object[] colorOptions = {"Play as White", "Play as Black"};
            int colorChoice = JOptionPane.showOptionDialog(
                    parentFrame, "Choose your color", "Color",
                    JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                    null, colorOptions, colorOptions[0]);

            if (colorChoice == JOptionPane.CLOSED_OPTION) return null;
            if (colorChoice == 1) playerColor = Piece.Color.BLACK;
        }

        // ── Step 3: time control ─────────────────────────────────────────────
        TimeControl selectedControl = TimeSetupDialog.showDialog(parentFrame);
        if (selectedControl == null) return null;

        // ── Step 4: game variant ─────────────────────────────────────────────
        GameModeType variant = promptForVariant();
        if (variant == null) return null;

        return new GameSetupData(selectedControl, selectedMode, playerColor, variant);
    }

    /**
     * Shows a compact three-button variant picker.
     */
    private GameModeType promptForVariant() {
        Object[] options = {"Classic", "Chess 960", "Floor is Lava"};
        int choice = JOptionPane.showOptionDialog(
                parentFrame,
                "Choose game variant",
                "Variant",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[0]);

        return switch (choice) {
            case 0 -> GameModeType.CLASSIC;
            case 1 -> GameModeType.CHESS960;
            case 2 -> GameModeType.LAVA;
            default -> null;   // dialog closed
        };
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Other dialogs
    // ─────────────────────────────────────────────────────────────────────────

    public boolean confirmResign() {
        return JOptionPane.showConfirmDialog(
                parentFrame, "Are you sure you want to resign?",
                "Resign", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
    }

    public String promptForPgn() {
        return JOptionPane.showInputDialog(
                parentFrame, "Insert PGN here:", "Load PGN", JOptionPane.PLAIN_MESSAGE);
    }

    public void showPgnExportSuccess() {
        JOptionPane.showMessageDialog(
                parentFrame, "PGN was copied to clipboard.",
                "Export PGN", JOptionPane.INFORMATION_MESSAGE);
    }

    public JFrame getParentFrame() { return parentFrame; }

    // ─────────────────────────────────────────────────────────────────────────
    //  Data class
    // ─────────────────────────────────────────────────────────────────────────

    public static class GameSetupData {
        public final TimeControl  timeControl;
        public final GameMode     gameMode;
        public final Piece.Color  playerColor;
        public final GameModeType variant;

        public GameSetupData(TimeControl timeControl, GameMode gameMode,
                             Piece.Color playerColor, GameModeType variant) {
            this.timeControl = timeControl;
            this.gameMode    = gameMode;
            this.playerColor = playerColor;
            this.variant     = variant;
        }

    }
}
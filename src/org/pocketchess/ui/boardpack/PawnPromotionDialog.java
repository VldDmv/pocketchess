package org.pocketchess.ui.boardpack;

import org.pocketchess.core.general.Game;
import org.pocketchess.core.pieces.*;
import org.pocketchess.ui.gameframepack.GameFrame;
import org.pocketchess.ui.gameframepack.sound.SoundManager;

import javax.swing.*;
import java.awt.*;

/**
 * Dialog for selecting piece when pawn reaches promotion square.
 */
public class PawnPromotionDialog {

    /**
     * Shows promotion dialog and executes promotion.
     */
    public static void showAndPromote(Game game, GameFrame gameFrame, Component parent) {
        SoundManager.playPromotionSound();


        Object[] options = {"Queen", "Rook", "Bishop", "Knight"};
        int choice = JOptionPane.showOptionDialog(
                parent,
                "Choose piece for promotion:",
                "Promoting pawn",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.PLAIN_MESSAGE,
                null,
                options,
                options[0] // Default to Queen
        );

        // Create piece based on choice
        boolean isWhiteTurnForPromotion = game.isWhiteTurn();
        Piece newPiece = switch (choice) {
            case 1 -> new Rook(isWhiteTurnForPromotion);
            case 2 -> new Bishop(isWhiteTurnForPromotion);
            case 3 -> new Knight(isWhiteTurnForPromotion);
            default -> new Queen(isWhiteTurnForPromotion);
        };


        game.promotePawn(newPiece);
        gameFrame.updateUI();
    }
}
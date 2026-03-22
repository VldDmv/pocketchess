package org.pocketchess.ui.gameframepack.frame;

import org.pocketchess.core.general.Game;

// Formats game status messages for display in the UI.
public class GameStatusFormatter {

    public static String formatStatus(Game game) {
        if (game.isDrawOffered() && !game.isGameOver()) {
            return "Draw is proposed. " + (game.isWhiteTurn() ? "White move." : "Black move.");
        }

        return switch (game.getStatus()) {
            case ACTIVE -> game.isWhiteTurn() ? "White move" : "Black move";
            case CHECK -> (game.isWhiteTurn() ? "White" : "Black") + " is in check.";
            case WHITE_WIN -> "Checkmate! White won.";
            case BLACK_WIN -> "Checkmate! Black won.";
            case STALEMATE -> "Draw! (Stalemate)";
            case AWAITING_PROMOTION -> "Choose piece for promotion...";
            case WHITE_WIN_ON_TIME -> "Time is up. White won.";
            case BLACK_WIN_ON_TIME -> "Time is up. Black won.";
            case DRAW_50_MOVES -> "Draw! (50 moves rule)";
            case DRAW_AGREED -> "Draw by agreement.";
            case WHITE_WINS_BY_RESIGNATION -> "Black resigned. White won.";
            case BLACK_WINS_BY_RESIGNATION -> "White resigned. Black won.";
            case DRAW_THREEFOLD_REPETITION -> "Draw! (Threefold repetition)";
            case DRAW_INSUFFICIENT_MATERIAL -> "Draw! (Insufficient material)";
        };
    }
}
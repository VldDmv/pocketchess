package org.pocketchess.ui.gameframepack.notation;

import org.pocketchess.core.game.model.GameMode;
import org.pocketchess.core.general.Game;
import org.pocketchess.core.game.moveanalyze.Move;
import org.pocketchess.core.game.gamenotation.NotationProvider;
import org.pocketchess.core.game.utils.PgnUtils;
import org.pocketchess.core.pieces.Piece;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Manages PGN (Portable Game Notation) export and import.
 */
public class PgnManager {
    private final Game game;
    private final NotationProvider notationProvider;

    public PgnManager(Game game, NotationProvider notationProvider) {
        this.game = game;
        this.notationProvider = notationProvider;
    }

    /**
     * Exports the current game to PGN format and copies it to the clipboard.
     */
    public void exportGameToPgn() {
        StringBuilder pgn = new StringBuilder();


        pgn.append("[Event \"Java Chess Game\"]\n");
        pgn.append("[Site \"Local\"]\n");
        pgn.append("[Date \"").append(new SimpleDateFormat("yyyy.MM.dd").format(new Date())).append("\"]\n");
        pgn.append("[Round \"-\"]\n");

        String whiteName = "?";
        String blackName = "?";

        if (game.getGameMode() == GameMode.PVP) {
            // Player vs Player
            whiteName = "Human";
            blackName = "Human";
        } else if (game.getGameMode() == GameMode.PVE) {
            // Player vs Engine
            if (game.getPlayerColor() == Piece.Color.WHITE) {
                whiteName = "Human";
                blackName = "AI";
            } else {
                whiteName = "AI";
                blackName = "Human";
            }
        }

        pgn.append("[White \"").append(whiteName).append("\"]\n");
        pgn.append("[Black \"").append(blackName).append("\"]\n");
        pgn.append("[Result \"").append(PgnUtils.getResultString(game.getStatus())).append("\"]\n\n");

        List<Move> history = game.getMoveHistory();
        for (int i = 0; i < history.size(); i++) {
            if (i % 2 == 0) {
                pgn.append((i / 2) + 1).append(". ");
            }


            pgn.append(notationProvider.getNotationForMove(history.get(i))).append(" ");
        }

        pgn.append(PgnUtils.getResultString(game.getStatus()));
        String pgnString = pgn.toString();


        StringSelection stringSelection = new StringSelection(pgnString);
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(stringSelection, null);
    }

    /**
     * Loads a game from PGN format.
     */
    public void loadPgn(String pgn) {
        if (pgn != null && !pgn.trim().isEmpty()) {
            PgnUtils.loadPgn(game, pgn);
        }
    }
}
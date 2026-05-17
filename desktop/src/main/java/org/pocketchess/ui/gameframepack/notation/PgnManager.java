package org.pocketchess.ui.gameframepack.notation;

import org.pocketchess.core.game.gamenotation.NotationProvider;
import org.pocketchess.core.game.gamenotation.PgnBuilder;
import org.pocketchess.core.game.utils.PgnUtils;
import org.pocketchess.core.general.Game;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;

/**
 * Manages PGN (Portable Game Notation) export (to clipboard) and import.
 */
public class PgnManager {
    private final Game game;
    private final NotationProvider notationProvider;

    public PgnManager(Game game, NotationProvider notationProvider) {
        this.game = game;
        this.notationProvider = notationProvider;
    }

    /** Builds the PGN string and copies it to the system clipboard. */
    public void exportGameToPgn() {
        String pgn = PgnBuilder.build(game, notationProvider);
        StringSelection stringSelection = new StringSelection(pgn);
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(stringSelection, null);
    }

    /** Loads a game from a PGN string. */
    public void loadPgn(String pgn) {
        if (pgn != null && !pgn.trim().isEmpty()) {
            PgnUtils.loadPgn(game, pgn);
        }
    }
}

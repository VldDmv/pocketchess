package org.pocketchess.core.game.gamenotation;

import org.pocketchess.core.game.model.GameMode;
import org.pocketchess.core.game.moveanalyze.Move;
import org.pocketchess.core.game.utils.FenUtils;
import org.pocketchess.core.game.utils.PgnUtils;
import org.pocketchess.core.gamemode.GameModeType;
import org.pocketchess.core.general.Board;
import org.pocketchess.core.general.Game;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Headless PGN builder. Extracted from the desktop's PgnManager so server
 * code can reuse the same output shape without depending on AWT clipboards.
 */
public final class PgnBuilder {

    private PgnBuilder() {}

    public static String build(Game game, NotationProvider notationProvider,
                               String whiteName, String blackName) {
        StringBuilder pgn = new StringBuilder();

        pgn.append("[Event \"PocketChess Game\"]\n");
        pgn.append("[Site \"PocketChess online\"]\n");
        pgn.append("[Date \"")
                .append(new SimpleDateFormat("yyyy.MM.dd").format(new Date()))
                .append("\"]\n");
        pgn.append("[Round \"-\"]\n");

        Board initialBoard = game.getBoard().getInitialSnapshot();
        String initialFen = initialBoard != null ? FenUtils.generateFEN(initialBoard, true) : "";

        if (game.getGameModeType() == GameModeType.CHESS960 && !initialFen.isEmpty()) {
            pgn.append("[Variant \"Chess960\"]\n");
            pgn.append("[SetUp \"1\"]\n");
            pgn.append("[FEN \"").append(initialFen).append("\"]\n");
        }

        String result = PgnUtils.getResultString(game.getStatus());
        pgn.append("[White \"").append(whiteName == null ? "?" : whiteName).append("\"]\n");
        pgn.append("[Black \"").append(blackName == null ? "?" : blackName).append("\"]\n");
        pgn.append("[Result \"").append(result).append("\"]\n\n");

        List<Move> history = game.getMoveHistory();
        for (int i = 0; i < history.size(); i++) {
            if (i % 2 == 0) pgn.append((i / 2) + 1).append(". ");
            pgn.append(notationProvider.getNotationForMove(history.get(i))).append(" ");
        }
        pgn.append(result);
        return pgn.toString();
    }

    /** Convenience overload that infers names from the offline {@link GameMode}. */
    public static String build(Game game, NotationProvider notationProvider) {
        if (game.getGameMode() == GameMode.PVE) {
            boolean playerWhite = game.getPlayerColor()
                    == org.pocketchess.core.pieces.Piece.Color.WHITE;
            return build(game, notationProvider,
                    playerWhite ? "Human" : "AI",
                    playerWhite ? "AI" : "Human");
        }
        return build(game, notationProvider, "Human", "Human");
    }
}

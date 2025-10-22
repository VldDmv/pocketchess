package org.pocketchess.core.game.gamenotation;

import org.pocketchess.core.game.moveanalyze.Move;

public interface NotationProvider {
    String getNotationForMove(Move move);
}
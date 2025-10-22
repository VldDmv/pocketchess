package org.pocketchess.core.game.status;

import org.pocketchess.core.game.GameStatus;

public interface GameStatusCallback {
    void onTimeExpired(boolean whiteExpired);

    boolean isWhiteTurn();

    GameStatus getStatus();
}
package org.pocketchess.core.game.moveanalyze;

/**
 * Callback interface for AI move completion events.
 * Implemented by the UI layer (GameFrame) so that core has no dependency on UI.
 */
public interface AICallback {
    /** Called after the AI move has been executed (or failed). */
    void onAIMoveCompleted(boolean moveSuccessful);

    /** Plays the appropriate sound for the last move. */
    void playSoundForLastMove();
}
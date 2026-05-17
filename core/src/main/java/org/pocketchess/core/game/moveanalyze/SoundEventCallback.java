package org.pocketchess.core.game.moveanalyze;

/**
 * Callback interface for sound events triggered by core game logic.
 * Implemented by GameFrame (UI layer), which wires it via Game.setSoundCallback().
 */
public interface SoundEventCallback {
    /** A piece was captured (including by lava). */
    void onCapture();

    /** Checkmate or king destroyed by lava. */
    void onCheckmate();

    /** Game ended in a draw (stalemate, agreement, 50-move, etc.). */
    void onDraw();

    /** A pawn was promoted. */
    void onPromotion();

    /** A new game started. */
    void onGameStart();
}
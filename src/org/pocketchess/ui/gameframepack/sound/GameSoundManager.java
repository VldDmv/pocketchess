package org.pocketchess.ui.gameframepack.sound;

import org.pocketchess.core.game.model.GameStatus;
import org.pocketchess.core.game.moveanalyze.Move;
import org.pocketchess.core.general.Game;

/**
 * High-level sound manager that decides which sound to play based on game events.
 */
public class GameSoundManager {
    private final Game game;

    public GameSoundManager(Game game) {
        this.game = game;
    }

    /**
     * Plays the appropriate sound for the last move made.
     */
    public void playSoundForLastMove() {
        Move lastMove = game.getLastMove();
        if (lastMove == null) {
            return;
        }

        GameStatus status = game.getStatus();

        // CHECKMATE
        if (status == GameStatus.WHITE_WIN || status == GameStatus.BLACK_WIN) {
            SoundManager.playCheckmateSound();
            return;
        }

        // DRAW
        if (isDrawStatus(status)) {
            SoundManager.playDrawSound();
            return;
        }

        // CHECK
        if (status == GameStatus.CHECK) {
            SoundManager.playCheckSound();
            return;
        }

        // CASTLING
        if (lastMove.wasCastlingMove) {
            SoundManager.playCastleSound();
            return;
        }

        // CAPTURE
        if (lastMove.pieceKilled != null) {
            SoundManager.playCaptureSound();
            return;
        }

        // NORMAL MOVE
        SoundManager.playMoveSound();
    }

    /**
     * Checks if the game status represents any type of draw.
     */
    private boolean isDrawStatus(GameStatus status) {
        return status == GameStatus.STALEMATE ||
                status == GameStatus.DRAW_50_MOVES ||
                status == GameStatus.DRAW_AGREED ||
                status == GameStatus.DRAW_THREEFOLD_REPETITION ||
                status == GameStatus.DRAW_INSUFFICIENT_MATERIAL;
    }

    /**
     * Plays the sound when a new game starts.
     */
    public void playStartSound() {
        SoundManager.playStartSound();
    }
}
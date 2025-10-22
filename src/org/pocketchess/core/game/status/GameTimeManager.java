package org.pocketchess.core.game.status;

import org.pocketchess.core.game.GameStatus;
import org.pocketchess.core.game.TimeControl;
import org.pocketchess.ui.gameframepack.sound.SoundManager;

import javax.swing.*;

/**
 * Manages the game time and timer.
 * - Counts down the time for both players
 * - Adds an increment
 * - Determines when the time ends
 * - Formats the time for display
 */
public class GameTimeManager {
    private final GameStatusCallback statusCallback;
    private long whiteTimeMillis;
    private long blackTimeMillis;
    private int incrementSeconds;
    private Timer timer;

    public GameTimeManager(GameStatusCallback callback) {
        this.statusCallback = callback;
    }

    /**
     * Initializes time according to control
     */
    public void resetTime(TimeControl tc) {
        if (tc.isUnlimited()) {
            this.whiteTimeMillis = Integer.MAX_VALUE;
            this.blackTimeMillis = Integer.MAX_VALUE;
        } else {
            long initialTime = tc.baseTimeSeconds() * 1000L;
            this.whiteTimeMillis = initialTime;
            this.blackTimeMillis = initialTime;
        }
        this.incrementSeconds = tc.incrementSeconds();
    }

    /**
     * Sets the timer to count down the time
     */
    public void setupTimer(Runnable uiUpdater) {
        if (timer != null) {
            timer.stop();
        }

        timer = new Timer(100, e -> {
            // Unlimited time - do nothing
            if (whiteTimeMillis == Integer.MAX_VALUE) {
                return;
            }

            GameStatus status = statusCallback.getStatus();

            // Time goes only when goes game
            if (status == GameStatus.ACTIVE || status == GameStatus.CHECK) {
                boolean isWhiteTurn = statusCallback.isWhiteTurn();

                if (isWhiteTurn) {
                    whiteTimeMillis -= 100;
                    if (whiteTimeMillis < 0) whiteTimeMillis = 0;
                } else {
                    blackTimeMillis -= 100;
                    if (blackTimeMillis < 0) blackTimeMillis = 0;
                }

                if (whiteTimeMillis == 0) {
                    statusCallback.onTimeExpired(true);
                    SoundManager.playCheckmateSound();
                    timer.stop();
                } else if (blackTimeMillis == 0) {
                    statusCallback.onTimeExpired(false);
                    SoundManager.playCheckmateSound();
                    timer.stop();
                }

                if (uiUpdater != null) {
                    SwingUtilities.invokeLater(uiUpdater);
                }
            }
        });
    }

    public void startTimer() {
        if (timer != null) {
            timer.start();
        }
    }

    public void stopTimer() {
        if (timer != null) {
            timer.stop();
        }
    }

    public void addIncrement(boolean forWhite) {
        if (incrementSeconds > 0) {
            if (forWhite) {
                whiteTimeMillis += incrementSeconds * 1000L;
            } else {
                blackTimeMillis += incrementSeconds * 1000L;
            }
        }
    }


    public String getWhiteTimeString() {
        return formatTime(whiteTimeMillis);
    }

    public String getBlackTimeString() {
        return formatTime(blackTimeMillis);
    }

    public long getWhiteTimeMillis() {
        return whiteTimeMillis;
    }

    public void setWhiteTimeMillis(long millis) {
        this.whiteTimeMillis = millis;
    }

    public long getBlackTimeMillis() {
        return blackTimeMillis;
    }

    public void setBlackTimeMillis(long millis) {
        this.blackTimeMillis = millis;
    }

    /**
     * Formats the time in milliseconds into a readable string
     */
    private String formatTime(long millis) {
        long deciseconds = millis / 100;
        long seconds = deciseconds / 10;
        long minutes = seconds / 60;
        seconds %= 60;
        deciseconds %= 10;


        if (minutes == 0 && seconds < 10) {
            return String.format("%d.%d", seconds, deciseconds);
        }


        return String.format("%02d:%02d", minutes, seconds);
    }
}
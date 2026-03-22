package org.pocketchess.ui.gameframepack.sound;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import java.io.BufferedInputStream;
import java.io.InputStream;

/**
 * Low-level sound manager that loads and plays audio clips.
 */
public class SoundManager {
    private static final Clip moveSound;
    private static final Clip captureSound;
    private static final Clip checkSound;
    private static final Clip castleSound;
    private static final Clip promotionSound;
    private static final Clip startSound;
    private static final Clip checkmateSound;
    private static final Clip drawSound;

    static {
        moveSound = loadSound("/sounds/move.wav");
        captureSound = loadSound("/sounds/capture.wav");
        checkSound = loadSound("/sounds/check.wav");
        castleSound = loadSound("/sounds/castle.wav");
        promotionSound = loadSound("/sounds/choice.wav");
        startSound = loadSound("/sounds/start.wav");
        checkmateSound = loadSound("/sounds/checkmate.wav");
        drawSound = loadSound("/sounds/draw.wav");
    }

    private static Clip loadSound(String path) {
        try {
            InputStream is = SoundManager.class.getResourceAsStream(path);
            if (is == null) return null;

            AudioInputStream audioStream = AudioSystem.getAudioInputStream(
                    new BufferedInputStream(is));
            Clip clip = AudioSystem.getClip();
            clip.open(audioStream);
            return clip;
        } catch (Exception e) {
            return null;
        }
    }

    private static void play(Clip clip) {
        if (clip != null) {
            clip.setFramePosition(0);
            clip.start();
        }
    }

    public static void playMoveSound() {
        play(moveSound);
    }

    public static void playCaptureSound() {
        play(captureSound);
    }

    public static void playCheckSound() {
        play(checkSound);
    }

    public static void playCastleSound() {
        play(castleSound);
    }

    public static void playPromotionSound() {
        play(promotionSound);
    }

    public static void playStartSound() {
        play(startSound);
    }

    public static void playCheckmateSound() {
        play(checkmateSound);
    }

    public static void playDrawSound() {
        play(drawSound);
    }
}
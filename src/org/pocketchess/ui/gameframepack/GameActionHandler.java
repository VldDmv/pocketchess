package org.pocketchess.ui.gameframepack;

import org.pocketchess.core.ai.difficulty.AIDifficulty;
import org.pocketchess.core.game.GameMode;
import org.pocketchess.core.general.Game;
import org.pocketchess.core.pieces.Piece;
import org.pocketchess.ui.gameframepack.notation.PgnManager;
import org.pocketchess.ui.gameframepack.sound.GameSoundManager;

/**
 * Handles game actions - coordinates dialogs and game state changes.
 * RESPONSIBILITIES:
 * - New game setup (mode, time, color, difficulty)
 * - Undo moves
 * - Draw offers
 * - Resign
 * - PGN import/export
 */
public class GameActionHandler {
    private final Game game;
    private final GameDialogManager dialogManager;
    private final PgnManager pgnManager;
    private final Runnable updateUICallback;
    private final GameFrameController frameController;
    private final GameSoundManager soundManager;

    public GameActionHandler(Game game, GameDialogManager dialogManager, PgnManager pgnManager,
                             Runnable updateUICallback, GameFrameController frameController,
                             GameSoundManager soundManager) {
        this.game = game;
        this.dialogManager = dialogManager;
        this.pgnManager = pgnManager;
        this.updateUICallback = updateUICallback;
        this.frameController = frameController;
        this.soundManager = soundManager;
    }

    /**
     * Handles new game creation with full setup flow.
     */
    public void handleNewGame() {

        GameDialogManager.GameSetupData setupData = dialogManager.promptForNewGame();
        if (setupData == null) {
            return;
        }

        AIDifficulty selectedDifficulty = AIDifficulty.MEDIUM;

        if (setupData.gameMode == GameMode.PVE) {
            AIDifficulty difficulty = DifficultySelectionDialog.showDialog(dialogManager.getParentFrame());
            if (difficulty == null) {
                return;
            }
            selectedDifficulty = difficulty;
        }

        game.resetGame(setupData.timeControl, setupData.gameMode, setupData.playerColor, selectedDifficulty);
        game.setupTimer(frameController::updateClocks);

        boolean shouldFlip = (setupData.gameMode == GameMode.PVE && setupData.playerColor == Piece.Color.BLACK);
        frameController.setBoardFlipped(shouldFlip);

        updateUICallback.run();
        soundManager.playStartSound();
    }

    public void handleUndo() {
        if (game.isLive() && !game.getMoveHistory().isEmpty()) {
            game.undoMove();
            updateUICallback.run();
        }
    }


    public void handleDrawOffer() {
        game.offerDraw();
        updateUICallback.run();
    }


    public void handleResign() {
        if (game.isLive() && !game.isGameOver()) {
            if (dialogManager.confirmResign()) {
                game.resign();
                updateUICallback.run();
            }
        }
    }


    public void handleExportPgn() {
        pgnManager.exportGameToPgn();
        dialogManager.showPgnExportSuccess();
    }


    public void handleLoadPgn() {
        String pgn = dialogManager.promptForPgn();
        if (pgn != null && !pgn.trim().isEmpty()) {
            pgnManager.loadPgn(pgn);
            updateUICallback.run();
        }
    }
}
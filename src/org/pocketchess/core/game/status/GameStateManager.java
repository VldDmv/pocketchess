package org.pocketchess.core.game.status;

import org.pocketchess.core.ai.difficulty.AIDifficulty;
import org.pocketchess.core.game.GameMode;
import org.pocketchess.core.game.GameStatus;
import org.pocketchess.core.pieces.Piece;
import org.pocketchess.core.pieces.Spot;

/**
 * Centralized game state management.
 * STORED STATE:
 * - Game mode (PVP/AI)
 * - Player color (when playing against AI)
 * - AI difficulty
 * - Turn order
 * - Current status (checkmate, stalemate, check, etc.)
 * - Pawn promotion position
 * - Proposed draw flag
 */
public class GameStateManager {
    public boolean isWhiteTurn = true;
    private GameMode gameMode = GameMode.PVP;
    private Piece.Color playerColor = Piece.Color.WHITE;
    private AIDifficulty aiDifficulty = AIDifficulty.MEDIUM;
    private GameStatus status = GameStatus.ACTIVE;
    private Spot promotionSpot = null;
    private boolean isDrawOffered = false;

    // GETTERS

    public GameMode getGameMode() {
        return gameMode;
    }

    public void setGameMode(GameMode gameMode) {
        this.gameMode = gameMode;
    }

    public Piece.Color getPlayerColor() {
        return playerColor;
    }

    public void setPlayerColor(Piece.Color playerColor) {
        this.playerColor = playerColor;
    }

    public AIDifficulty getAiDifficulty() {
        return aiDifficulty;
    }

    public void setAiDifficulty(AIDifficulty aiDifficulty) {
        this.aiDifficulty = aiDifficulty;
    }

    public boolean isWhiteTurn() {
        return isWhiteTurn;
    }

    public void setWhiteTurn(boolean isWhiteTurn) {
        this.isWhiteTurn = isWhiteTurn;
    }

    public GameStatus getStatus() {
        return status;
    }

    public void setStatus(GameStatus status) {
        this.status = status;
    }

    // SETTERS

    public Spot getPromotionSpot() {
        return promotionSpot;
    }

    public void setPromotionSpot(Spot promotionSpot) {
        this.promotionSpot = promotionSpot;
    }

    public boolean isDrawOffered() {
        return isDrawOffered;
    }

    public void setDrawOffered(boolean isDrawOffered) {
        this.isDrawOffered = isDrawOffered;
    }

    /**
     * Checks if the game is over.
     */
    public boolean isGameOver() {
        return status != GameStatus.ACTIVE &&
                status != GameStatus.CHECK &&
                status != GameStatus.AWAITING_PROMOTION;
    }

    /**
     * Checks whether it is AI's turn or human's.
     */
    public boolean isAIsTurn() {
        if (gameMode == GameMode.PVE) {
            return (isWhiteTurn && playerColor == Piece.Color.BLACK) ||
                    (!isWhiteTurn && playerColor == Piece.Color.WHITE);
        }
        return false;
    }

    public boolean isHumansTurn() {
        return !isAIsTurn();
    }

    /**
     * Changing turn
     */
    public void toggleTurn() {
        isWhiteTurn = !isWhiteTurn;
    }

    /**
     * Resets the game state to its initial state
     * Called when starting a new game
     */
    public void reset() {
        status = GameStatus.ACTIVE;
        isWhiteTurn = true;
        isDrawOffered = false;
        promotionSpot = null;
    }

    /**
     * Configures the game
     * Called when creating a new game
     */
    public void configure(GameMode mode, Piece.Color playerColor, AIDifficulty difficulty) {
        this.gameMode = mode;
        this.playerColor = playerColor;
        this.aiDifficulty = difficulty;
    }
}
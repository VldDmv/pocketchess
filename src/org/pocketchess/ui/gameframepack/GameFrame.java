package org.pocketchess.ui.gameframepack;

import org.pocketchess.core.game.GameMode;
import org.pocketchess.core.game.gamenotation.NotationProvider;
import org.pocketchess.core.game.moveanalyze.Move;
import org.pocketchess.core.general.Game;
import org.pocketchess.ui.boardpack.BoardPanel;
import org.pocketchess.ui.gameframepack.notation.ChessNotationFormatter;
import org.pocketchess.ui.gameframepack.notation.MoveHistoryManager;
import org.pocketchess.ui.gameframepack.notation.PgnManager;
import org.pocketchess.ui.gameframepack.piecesandclock.PlayerPanelManager;
import org.pocketchess.ui.gameframepack.sound.GameSoundManager;

import javax.swing.*;
import java.awt.*;

/**
 * Main game window - coordinates all UI components and managers.
 * MANAGERS:
 * - PlayerPanelManager: Manages player panels (clocks, captured pieces)
 * - MoveHistoryManager: Manages move history display and navigation
 * - GameDialogManager: Handles all user dialogs (new game, resign, etc.)
 * - PgnManager: Handles PGN import/export
 * - GameActionHandler: Processes game actions (undo, draw, resign)
 * - GameSoundManager: Plays appropriate sounds for game events
 */
public class GameFrame extends JFrame implements NotationProvider, GameFrameController {
    // Game logic
    private final Game game;

    // UI components
    private final BoardPanel boardPanel;
    private final JLabel statusLabel;
    private final JPanel westPanel;
    private final JPanel actionsPanel;

    // Managers
    private final PlayerPanelManager playerPanelManager;
    private final MoveHistoryManager moveHistoryManager;
    private final GameDialogManager dialogManager;
    private final PgnManager pgnManager;
    private final GameActionHandler actionHandler;
    private final ChessNotationFormatter notationFormatter;
    private final GameSoundManager soundManager;

    // State
    private boolean isBoardFlipped = false;

    public GameFrame() {
        this.game = new Game();
        this.game.setGameFrame(this);

        // Initialize helper managers
        this.dialogManager = new GameDialogManager(this);
        this.notationFormatter = new ChessNotationFormatter(game);
        this.pgnManager = new PgnManager(game, this);
        this.soundManager = new GameSoundManager(game);

        // Create UI components
        this.statusLabel = GameFrameLayoutBuilder.createStatusLabel();
        this.boardPanel = new BoardPanel(this);
        this.westPanel = GameFrameLayoutBuilder.createWestPanel();
        this.actionsPanel = GameFrameLayoutBuilder.createActionsPanel(
                this::handleUndo,
                this::handleFlipBoard,
                this::handleDrawOffer,
                this::handleResign
        );

        // Create managers that depend on UI
        this.playerPanelManager = new PlayerPanelManager(westPanel, actionsPanel);
        this.moveHistoryManager = new MoveHistoryManager(game, this, this::updateUI);
        this.actionHandler = new GameActionHandler(
                game, dialogManager, pgnManager, this::updateUI, this, soundManager
        );

        // Setup and start
        setupFrame();
        layoutComponents();
        game.setupTimer(this::updateClocks);
        actionHandler.handleNewGame();
    }

    /**
     * Configures frame properties.
     */
    private void setupFrame() {
        setTitle("Chesso");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);
        setLayout(new BorderLayout(10, 0));
    }

    /**
     * Arranges all components in the frame.
     */
    private void layoutComponents() {
        JPanel topPanel = GameFrameLayoutBuilder.createTopPanel(
                statusLabel,
                actionHandler::handleNewGame,
                actionHandler::handleLoadPgn,
                actionHandler::handleExportPgn
        );

        add(topPanel, BorderLayout.NORTH);
        add(westPanel, BorderLayout.WEST);
        add(boardPanel, BorderLayout.CENTER);
        add(moveHistoryManager.createScrollPane(), BorderLayout.EAST);

        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    // ========== ACTION HANDLERS ==========

    private void handleUndo() {
        actionHandler.handleUndo();
    }

    private void handleFlipBoard() {
        setBoardFlipped(!isBoardFlipped);
    }

    private void handleDrawOffer() {
        actionHandler.handleDrawOffer();
    }

    private void handleResign() {
        actionHandler.handleResign();
    }

    // ========== UI UPDATES ==========

    /**
     * Master update method - refreshes all UI components.
     */
    public void updateUI() {
        playerPanelManager.updateCapturedPieces(
                game.getWhiteCapturedPieces(),
                game.getBlackCapturedPieces()
        );
        updateClocks();
        updateStatus();
        moveHistoryManager.updateMoveHistory();
        updateButtonStates();
        boardPanel.repaint();
    }

    @Override
    public void updateClocks() {
        playerPanelManager.updateClocks(
                game.getWhiteTimeString(),
                game.getBlackTimeString()
        );
    }

    /**
     * Updates status label and draw button text.
     */
    private void updateStatus() {
        String statusText = GameStatusFormatter.formatStatus(game);
        statusLabel.setText(statusText);

        GameFrameLayoutBuilder.updateDrawButton(
                actionsPanel,
                game.isDrawOffered(),
                game.isGameOver()
        );
    }

    /**
     * Enables/disables buttons based on game state.
     */
    private void updateButtonStates() {
        GameFrameLayoutBuilder.updateButtonStates(
                actionsPanel,
                game.isLive(),
                game.isGameOver(),
                !game.getMoveHistory().isEmpty(),
                game.getGameMode() == GameMode.PVP
        );
    }


    @Override
    public void setBoardFlipped(boolean flipped) {
        this.isBoardFlipped = flipped;
        boardPanel.setFlipped(flipped);
        playerPanelManager.updatePlayerPanels(flipped, game.getTimeControl());
    }

    // ========== NOTATION DELEGATION ==========

    @Override
    public String getNotationForMove(Move move) {
        return notationFormatter.getNotationForMove(move);
    }

    // ========== SOUNDS ==========

    public void playSoundForLastMove() {
        soundManager.playSoundForLastMove();
    }

    // ========== GETTERS ==========

    public Game getGame() {
        return game;
    }

    // ========== MAIN ==========

    public static void main(String[] args) {
        SwingUtilities.invokeLater(GameFrame::new);
    }
}
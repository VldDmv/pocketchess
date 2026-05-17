package org.pocketchess.ui.gameframepack.frame;

import org.pocketchess.core.game.model.GameMode;
import org.pocketchess.core.game.gamenotation.NotationProvider;
import org.pocketchess.core.game.moveanalyze.AICallback;
import org.pocketchess.core.game.moveanalyze.Move;
import org.pocketchess.core.game.moveanalyze.SoundEventCallback;
import org.pocketchess.core.general.Game;
import org.pocketchess.ui.boardpack.BoardPanel;
import org.pocketchess.ui.gameframepack.notation.ChessNotationFormatter;
import org.pocketchess.ui.gameframepack.notation.MoveHistoryManager;
import org.pocketchess.ui.gameframepack.notation.PgnManager;
import org.pocketchess.ui.gameframepack.piecesandclock.PlayerPanelManager;
import org.pocketchess.ui.gameframepack.sound.GameSoundManager;
import org.pocketchess.ui.gameframepack.sound.SoundManager;

import javax.swing.*;
import java.awt.*;

/**
 * Main game window.
 *
 * Implements {@link SoundEventCallback} and passes itself to
 * {@link Game#setSoundCallback(SoundEventCallback)}, keeping all sound
 * playback in the UI layer with no core→ui dependency.
 */
public class GameFrame extends JFrame
        implements NotationProvider, GameFrameController, AICallback, SoundEventCallback {

    private final Game game;

    private final BoardPanel boardPanel;
    private final JLabel statusLabel;
    private final JPanel westPanel;
    private final JPanel actionsPanel;

    private final PlayerPanelManager playerPanelManager;
    private final MoveHistoryManager moveHistoryManager;
    private final GameDialogManager dialogManager;
    private final PgnManager pgnManager;
    private final GameActionHandler actionHandler;
    private final ChessNotationFormatter notationFormatter;
    private final GameSoundManager soundManager;

    private boolean isBoardFlipped = false;

    public GameFrame() {
        this.game = new Game();

        game.setSoundCallback(this);
        game.setCallback(this);

        this.dialogManager      = new GameDialogManager(this);
        this.notationFormatter  = new ChessNotationFormatter(game);
        this.pgnManager         = new PgnManager(game, this);
        this.soundManager       = new GameSoundManager(game);

        this.statusLabel  = GameFrameLayoutBuilder.createStatusLabel();
        this.boardPanel   = new BoardPanel(this);
        this.westPanel    = GameFrameLayoutBuilder.createWestPanel();
        this.actionsPanel = GameFrameLayoutBuilder.createActionsPanel(
                this::handleUndo,
                this::handleFlipBoard,
                this::handleDrawOffer,
                this::handleResign
        );

        this.playerPanelManager = new PlayerPanelManager(westPanel, actionsPanel);
        this.moveHistoryManager = new MoveHistoryManager(game, this, this::updateUI);
        this.actionHandler = new GameActionHandler(
                game, dialogManager, pgnManager, this::updateUI, this, soundManager
        );

        setupFrame();
        layoutComponents();
        game.setupTimer(this::updateClocks);
        actionHandler.handleNewGame();
    }

    private void setupFrame() {
        setTitle("Chesso");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);
        setLayout(new BorderLayout(10, 0));
    }

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

    // ── Action handlers ───────────────────────────────────────────────────────

    private void handleUndo()       { actionHandler.handleUndo(); }
    private void handleFlipBoard()  { setBoardFlipped(!isBoardFlipped); }
    private void handleDrawOffer()  { actionHandler.handleDrawOffer(); }
    private void handleResign()     { actionHandler.handleResign(); }

    // ── UI updates ────────────────────────────────────────────────────────────

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

    private void updateStatus() {
        String statusText = GameStatusFormatter.formatStatus(game);
        statusLabel.setText(statusText);
        GameFrameLayoutBuilder.updateDrawButton(
                actionsPanel, game.isDrawOffered(), game.isGameOver());
    }

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

    @Override
    public void onAIMoveCompleted(boolean moveSuccessful) { updateUI(); }

    // ── SoundEventCallback implementation ─────────────────────────────────────

    @Override
    public void onCapture()    { SoundManager.playCaptureSound(); }

    @Override
    public void onCheckmate()  { SoundManager.playCheckmateSound(); }

    @Override
    public void onDraw()       { SoundManager.playDrawSound(); }

    @Override
    public void onPromotion()  { SoundManager.playPromotionSound(); }

    @Override
    public void onGameStart()  { SoundManager.playStartSound(); }

    // ── NotationProvider ──────────────────────────────────────────────────────

    @Override
    public String getNotationForMove(Move move) {
        return notationFormatter.getNotationForMove(move);
    }

    // ── AICallback sound ──────────────────────────────────────────────────────

    @Override
    public void playSoundForLastMove() { soundManager.playSoundForLastMove(); }

    // ── Getters ───────────────────────────────────────────────────────────────

    public Game getGame() { return game; }

    // ── Main ──────────────────────────────────────────────────────────────────

    public static void main(String[] args) {
        SwingUtilities.invokeLater(GameFrame::new);
    }
}
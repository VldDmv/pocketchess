package org.pocketchess.core.general;

import org.pocketchess.core.ai.difficulty.AIDifficulty;
import org.pocketchess.core.game.GameMode;
import org.pocketchess.core.game.GameStatus;
import org.pocketchess.core.game.TimeControl;
import org.pocketchess.core.game.gamenotation.GameHistoryManager;
import org.pocketchess.core.game.gamenotation.GamePositionTracker;
import org.pocketchess.core.game.gamenotation.HistoryNavigationService;
import org.pocketchess.core.game.moveanalyze.*;
import org.pocketchess.core.game.status.*;
import org.pocketchess.core.pieces.King;
import org.pocketchess.core.pieces.Piece;
import org.pocketchess.core.pieces.Rook;
import org.pocketchess.core.pieces.Spot;
import org.pocketchess.ui.gameframepack.GameFrame;
import org.pocketchess.ui.gameframepack.sound.SoundManager;

import java.util.List;

/**
 * Main Game class - coordinates all chess game components.
 * RESPONSIBILITIES:
 * - Coordinates game flow (setup, moves, game ending)
 * - Delegates to specialized services
 * - Provides unified interface for UI
 * - Implements GameStatusCallback for timer events
 * COMPONENTS:
 * - Board & RuleEngine: Core game logic
 * - State Managers: Track game state (turn, status, time, position)
 * - Services: Handle specific tasks (player moves, AI moves, navigation)
 */
public class Game implements GameStatusCallback {
    // Core components
    private final Board board;
    private final ChessRules ruleEngine;

    // State managers
    private final GameStateManager stateManager;
    private final GameTimeManager timeManager;
    private final GamePositionTracker positionTracker;
    private final GameStatusManager statusManager;
    private final GameHistoryManager historyManager;
    private final GameMoveExecutor moveExecutor;

    // Services (Single Responsibility Principle)
    private final PlayerMoveService playerMoveService;
    private final AIMoveService aiMoveService;
    private final HistoryNavigationService historyNavigationService;
    private final TurnFinisher turnFinisher;
    private final TemporaryMoveHandler temporaryMoveHandler;

    private TimeControl timeControl;

    /**
     * Default constructor - initializes a new game.
     * Default settings: 5 minutes, PVP mode, White player.
     */
    public Game() {
        // Initialize org.pocketchess.core components
        this.board = new Board();
        this.ruleEngine = new RuleEngine();
        this.stateManager = new GameStateManager();
        this.positionTracker = new GamePositionTracker();
        this.statusManager = new GameStatusManager(ruleEngine, positionTracker);
        this.moveExecutor = new GameMoveExecutor(board);
        this.historyManager = new GameHistoryManager(board, moveExecutor, positionTracker);
        this.timeManager = new GameTimeManager(this);
        this.temporaryMoveHandler = new TemporaryMoveHandler(board, positionTracker);

        // Initialize services
        this.turnFinisher = new TurnFinisher(stateManager, timeManager, positionTracker,
                statusManager, board);
        this.playerMoveService = new PlayerMoveService(board, ruleEngine, stateManager,
                moveExecutor, historyManager,
                positionTracker, timeManager, turnFinisher);
        this.aiMoveService = new AIMoveService(this, stateManager, playerMoveService);
        this.historyNavigationService = new HistoryNavigationService(historyManager, stateManager,
                timeManager, statusManager, board);

        resetGame(new TimeControl(5, 0), GameMode.PVP, Piece.Color.WHITE, AIDifficulty.MEDIUM);
    }

    /**
     * Copy constructor - creates a deep copy of the game.
     * Used by AI to simulate moves without affecting the actual game.
     * Copies board state, piece positions, turn, and game parameters.
     */
    public Game(Game other) {
        this.board = new Board(other.board);
        this.ruleEngine = other.ruleEngine;
        this.timeControl = other.timeControl;
        this.stateManager = new GameStateManager();
        this.stateManager.setWhiteTurn(other.stateManager.isWhiteTurn());
        this.stateManager.setStatus(other.stateManager.getStatus());
        this.stateManager.setAiDifficulty(other.stateManager.getAiDifficulty());

        this.positionTracker = new GamePositionTracker(other.positionTracker);
        this.statusManager = new GameStatusManager(ruleEngine, positionTracker);
        this.moveExecutor = new GameMoveExecutor(board);
        this.historyManager = new GameHistoryManager(board, moveExecutor, positionTracker);
        this.timeManager = new GameTimeManager(this);
        this.temporaryMoveHandler = new TemporaryMoveHandler(board, positionTracker);

        this.turnFinisher = new TurnFinisher(stateManager, timeManager, positionTracker,
                statusManager, board);
        this.playerMoveService = new PlayerMoveService(board, ruleEngine, stateManager,
                moveExecutor, historyManager,
                positionTracker, timeManager, turnFinisher);
        this.aiMoveService = new AIMoveService(this, stateManager, playerMoveService);
        this.historyNavigationService = new HistoryNavigationService(historyManager, stateManager,
                timeManager, statusManager, board);
    }

    // ========== GETTERS ==========

    public void setGameFrame(GameFrame frame) {
        aiMoveService.setGameFrame(frame);
    }

    public Board getBoard() {
        return board;
    }

    public boolean isThreefoldRepetition() {
        return positionTracker.isThreefoldRepetition(board, stateManager.isWhiteTurn());
    }

    public boolean isFiftyMoveRule() {
        return positionTracker.isFiftyMoveRule();
    }

    @Override
    public boolean isWhiteTurn() {
        return stateManager.isWhiteTurn();
    }

    @Override
    public GameStatus getStatus() {
        return stateManager.getStatus();
    }

    public Move getLastMove() {
        return historyManager.getLastMove();
    }

    public GameMode getGameMode() {
        return stateManager.getGameMode();
    }

    public Piece.Color getPlayerColor() {
        return stateManager.getPlayerColor();
    }

    public List<Move> getMoveHistory() {
        return historyManager.getMoveHistory();
    }

    public int getCurrentMoveIndex() {
        return historyManager.getCurrentMoveIndex();
    }

    public List<Piece> getWhiteCapturedPieces() {
        return moveExecutor.getWhiteCapturedPieces();
    }

    public List<Piece> getBlackCapturedPieces() {
        return moveExecutor.getBlackCapturedPieces();
    }

    public String getWhiteTimeString() {
        return timeManager.getWhiteTimeString();
    }

    public String getBlackTimeString() {
        return timeManager.getBlackTimeString();
    }

    public boolean isDrawOffered() {
        return stateManager.isDrawOffered();
    }

    public boolean isGameOver() {
        return stateManager.isGameOver();
    }

    public boolean isLive() {
        return historyManager.isLive();
    }

    public TimeControl getTimeControl() {
        return this.timeControl;
    }

    public boolean isAIsTurn() {
        return stateManager.isAIsTurn();
    }

    // ========== TIMER CALLBACK ==========


    @Override
    public void onTimeExpired(boolean whiteExpired) {
        stateManager.setStatus(whiteExpired ?
                GameStatus.BLACK_WIN_ON_TIME : GameStatus.WHITE_WIN_ON_TIME);
    }

    // ========== CORE METHODS ==========


    public void resetGame(TimeControl tc, GameMode mode, Piece.Color playerColor,
                          AIDifficulty difficulty) {
        this.timeControl = tc;
        stateManager.configure(mode, playerColor, difficulty);

        board.resetBoard();
        moveExecutor.clearCapturedPieces();
        historyManager.clearHistory();
        positionTracker.reset();
        positionTracker.recordInitialPosition(board);
        timeManager.resetTime(tc);

        stateManager.reset();

        if (stateManager.isAIsTurn()) {
            aiMoveService.makeAIMoveWithDelay();
        }
    }

    public void resetGame(TimeControl tc, GameMode mode, Piece.Color playerColor) {
        resetGame(tc, mode, playerColor, AIDifficulty.MEDIUM);
    }

    // ========== DELEGATION TO SERVICES ==========

    public boolean playerMove(int startX, int startY, int endX, int endY) {
        boolean moveSuccessful = playerMoveService.executeMove(startX, startY, endX, endY);

        if (moveSuccessful && stateManager.isAIsTurn() && !stateManager.isGameOver()) {
            aiMoveService.makeAIMoveWithDelay();
        }

        return moveSuccessful;
    }


    public void playerMove(int startX, int startY, int endX, int endY, Piece promotionPiece) {
        playerMoveService.executeMoveWithPromotion(startX, startY, endX, endY, promotionPiece);
    }


    public void promotePawn(Piece newPiece) {
        playerMoveService.promotePawn(newPiece);

        if (stateManager.isAIsTurn() && !stateManager.isGameOver()) {
            aiMoveService.makeAIMoveWithDelay();
        }
    }

    /**
     * Undoes the last move (or last 2 moves in PVE mode).
     */
    public void undoMove() {
        historyNavigationService.undoMove();
    }


    public void goToMove(int moveIndex) {
        historyNavigationService.goToMove(moveIndex);
    }

    // ========== GAME ACTIONS ==========

    /**
     * Offers or accepts a draw.
     * First call: offers draw to opponent
     * Second call: accepts and ends game
     */
    public void offerDraw() {
        if (stateManager.isDrawOffered()) {
            stateManager.setStatus(GameStatus.DRAW_AGREED);
            stateManager.setDrawOffered(false);
            timeManager.stopTimer();
            SoundManager.playDrawSound();
        } else {
            stateManager.setDrawOffered(true);
        }
    }


    public void resign() {
        stateManager.setStatus(stateManager.isWhiteTurn() ?
                GameStatus.BLACK_WINS_BY_RESIGNATION : GameStatus.WHITE_WINS_BY_RESIGNATION);
        timeManager.stopTimer();
        SoundManager.playStartSound();
    }

    public void setupTimer(Runnable uiUpdater) {
        timeManager.setupTimer(uiUpdater);
    }

    // ========== DELEGATION TO RULE ENGINE ==========

    public boolean isMoveLegal(Spot start, Spot end) {
        return ruleEngine.isMoveLegal(board, start, end);
    }

    public Spot findKing(boolean isWhite) {
        return ruleEngine.findKing(board, isWhite);
    }

    public boolean isKingInCheck(boolean isWhite) {
        return ruleEngine.isKingInCheck(board, isWhite);
    }

    public boolean isSquareUnderAttack(Spot spot, boolean isAttackerWhite) {
        return ruleEngine.isSquareUnderAttack(board, spot, isAttackerWhite);
    }


    public boolean canCastle(boolean isWhite, boolean isKingside) {
        int rank = isWhite ? 7 : 0;
        int rookCol = isKingside ? 7 : 0;
        int kingCol = 4;

        Piece king = board.getBox(rank, kingCol).getPiece();
        if (!(king instanceof King) || ((King) king).hasMoved() || king.isWhite() != isWhite) {
            return false;
        }

        Piece rook = board.getBox(rank, rookCol).getPiece();
        return rook instanceof Rook && !((Rook) rook).hasMoved() && rook.isWhite() == isWhite;
    }

    // TEMPORARY MOVES FOR AI

    /**
     * Makes a temporary move (for AI search).
     */
    public void makeTemporaryMove(Move move) {
        stateManager.setWhiteTurn(
                temporaryMoveHandler.makeTemporaryMove(move, stateManager.isWhiteTurn())
        );
    }

    public void undoTemporaryMove(Move move) {
        stateManager.setWhiteTurn(
                temporaryMoveHandler.undoTemporaryMove(move, stateManager.isWhiteTurn())
        );
    }

    public void makeNullMove() {
        stateManager.setWhiteTurn(
                temporaryMoveHandler.makeNullMove(stateManager.isWhiteTurn())
        );
    }

    public void undoNullMove() {
        stateManager.setWhiteTurn(
                temporaryMoveHandler.undoNullMove(stateManager.isWhiteTurn())
        );
    }
}
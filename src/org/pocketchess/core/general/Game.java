package org.pocketchess.core.general;

import org.pocketchess.core.ai.difficulty.AIDifficulty;
import org.pocketchess.core.game.gamenotation.GameHistoryManager;
import org.pocketchess.core.game.gamenotation.GamePositionTracker;
import org.pocketchess.core.game.gamenotation.HistoryNavigationService;
import org.pocketchess.core.game.model.GameMode;
import org.pocketchess.core.game.model.GameStatus;
import org.pocketchess.core.game.model.TimeControl;
import org.pocketchess.core.game.moveanalyze.*;
import org.pocketchess.core.game.status.*;
import org.pocketchess.core.gamemode.Chess960Setup;
import org.pocketchess.core.gamemode.GameModeType;
import org.pocketchess.core.gamemode.LavaEffectService;
import org.pocketchess.core.gamemode.LavaManager;
import org.pocketchess.core.pieces.King;
import org.pocketchess.core.pieces.Piece;
import org.pocketchess.core.pieces.Rook;
import org.pocketchess.core.pieces.Spot;

import java.util.List;

/**
 * Main Game class – coordinates all chess game components.
 * <p>
 * Sound events are routed through {@link SoundEventCallback}, set by the UI layer via
 * {@link #setSoundCallback(SoundEventCallback)}. AI copies never call setSoundCallback,
 * so they have no dependency on the UI package.
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

    // Services
    private final PlayerMoveService playerMoveService;
    private final AIMoveService aiMoveService;
    private final HistoryNavigationService historyNavigationService;
    private final TurnFinisher turnFinisher;
    private final TemporaryMoveHandler temporaryMoveHandler;
    private final LavaManager lavaManager;
    private final LavaEffectService lavaEffectService;

    private TimeControl timeControl;

    private SoundEventCallback soundCallback;

    // ─────────────────────────────────────────────────────────────────────────
    //  Default constructor
    // ─────────────────────────────────────────────────────────────────────────

    public Game() {
        this.board = new Board();
        this.lavaManager = new LavaManager();
        this.ruleEngine = new LavaAwareRuleEngine(new RuleEngine(), lavaManager, board);
        this.stateManager = new GameStateManager();
        this.positionTracker = new GamePositionTracker();
        this.statusManager = new GameStatusManager(ruleEngine, positionTracker);
        this.moveExecutor = new GameMoveExecutor(board);
        this.historyManager = new GameHistoryManager(board, moveExecutor, positionTracker);
        this.timeManager = new GameTimeManager(this);
        this.temporaryMoveHandler = new TemporaryMoveHandler(board, positionTracker);

        this.turnFinisher = new TurnFinisher(stateManager, timeManager,
                positionTracker, statusManager, board);

        this.lavaEffectService = new LavaEffectService(lavaManager, board,
                stateManager, timeManager, moveExecutor, ruleEngine);
        this.turnFinisher.setPostMoveCallback(
                () -> lavaEffectService.apply(historyManager.getMoveHistory().size()));

        this.playerMoveService = new PlayerMoveService(board, ruleEngine, stateManager,
                moveExecutor, historyManager, positionTracker, timeManager, turnFinisher);
        this.aiMoveService = new AIMoveService(this, stateManager, playerMoveService);
        this.historyNavigationService = new HistoryNavigationService(historyManager, stateManager,
                timeManager, statusManager, board);

        resetGame(new TimeControl(5, 0), GameMode.PVP, Piece.Color.WHITE,
                AIDifficulty.MEDIUM, false);
    }

    public Game(Game other) {
        this.board = new Board(other.board);
        this.timeControl = other.timeControl;

        this.lavaManager = new LavaManager(other.lavaManager);
        LavaAwareRuleEngine aiRuleEngine = new LavaAwareRuleEngine(new RuleEngine(), lavaManager, board);
        aiRuleEngine.setAISimulation(true);
        this.ruleEngine = aiRuleEngine;

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

        // AI copies: no lava effect callback, no sound callback
        this.turnFinisher = new TurnFinisher(stateManager, timeManager,
                positionTracker, statusManager, board);

        this.lavaEffectService = new LavaEffectService(lavaManager, board,
                stateManager, timeManager, moveExecutor, ruleEngine);

        this.playerMoveService = new PlayerMoveService(board, ruleEngine, stateManager,
                moveExecutor, historyManager, positionTracker, timeManager, turnFinisher);
        this.aiMoveService = new AIMoveService(this, stateManager, playerMoveService);
        this.historyNavigationService = new HistoryNavigationService(historyManager, stateManager,
                timeManager, statusManager, board);

        // soundCallback intentionally null for AI copies
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Sound callback wiring  (called only by the real game, never AI copies)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Wires in the UI sound callback.
     * Must be called by GameFrame after constructing the real Game.
     * AI copies never call this, so they stay silent with zero UI dependency.
     */
    public void setSoundCallback(SoundEventCallback callback) {
        this.soundCallback = callback;
        this.lavaEffectService.setSoundCallback(callback);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Lava
    // ─────────────────────────────────────────────────────────────────────────

    public LavaManager getLavaManager() {
        return lavaManager;
    }

    public boolean isLavaMode() {
        return lavaManager.isEnabled();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Getters
    // ─────────────────────────────────────────────────────────────────────────

    public void setCallback(AICallback callback) {
        aiMoveService.setCallback(callback);
    }

    public Board getBoard() {
        return board;
    }

    public boolean isThreefoldRepetition() {
        return positionTracker.isThreefoldRepetition(board, stateManager.isWhiteTurn());
    }
    /**
     * Returns how many times the current position has appeared in this game.
     * Used by NegamaxEngine to detect and penalise moves that build toward
     * a threefold repetition before it actually occurs.
     *
     *  count == 1  →  first time here (normal)
     *  count == 2  →  position repeated once  →  engine applies penalty
     *  count >= 3  →  threefold repetition    →  scored as draw
     */
    public int getPositionCount() {
        return positionTracker.getPositionCount(board, stateManager.isWhiteTurn());
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

    public ChessRules getRuleEngine() {
        return ruleEngine;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Timer callback
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void onTimeExpired(boolean whiteExpired) {
        stateManager.setStatus(whiteExpired
                ? GameStatus.BLACK_WIN_ON_TIME : GameStatus.WHITE_WIN_ON_TIME);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Reset
    // ─────────────────────────────────────────────────────────────────────────

    public void resetGame(TimeControl tc, GameMode mode, Piece.Color playerColor,
                          AIDifficulty difficulty, GameModeType variant) {
        this.timeControl = tc;
        stateManager.configure(mode, playerColor, difficulty);

        board.resetBoard();

        if (variant == GameModeType.CHESS960) {
            applyChess960Setup();
        }

        board.saveAsInitial();

        moveExecutor.clearCapturedPieces();
        historyManager.clearHistory();
        positionTracker.reset();
        positionTracker.recordInitialPosition(board);
        timeManager.resetTime(tc);
        stateManager.reset();

        if (variant == GameModeType.LAVA) {
            lavaManager.enable(board);
        } else {
            lavaManager.disable();
        }

        if (soundCallback != null) soundCallback.onGameStart();

        if (stateManager.isAIsTurn()) {
            aiMoveService.makeAIMoveWithDelay();
        }
    }

    private void applyChess960Setup() {
        Piece[] whiteRank = Chess960Setup.generateWhiteBackRank();
        Piece[] blackRank = Chess960Setup.generateBlackBackRank(whiteRank);
        for (int col = 0; col < 8; col++) {
            board.getBox(7, col).setPiece(whiteRank[col]);
            board.getBox(0, col).setPiece(blackRank[col]);
        }
    }

    public void resetGame(TimeControl tc, GameMode mode, Piece.Color playerColor,
                          AIDifficulty difficulty, boolean lavaMode) {
        resetGame(tc, mode, playerColor, difficulty,
                lavaMode ? GameModeType.LAVA : GameModeType.CLASSIC);
    }

    public void resetGame(TimeControl tc, GameMode mode, Piece.Color playerColor,
                          AIDifficulty difficulty) {
        resetGame(tc, mode, playerColor, difficulty, false);
    }

    public void resetGame(TimeControl tc, GameMode mode, Piece.Color playerColor) {
        resetGame(tc, mode, playerColor, AIDifficulty.MEDIUM, false);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Player moves
    // ─────────────────────────────────────────────────────────────────────────

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

    public void undoMove() {
        if (!lavaManager.isEnabled()) {
            historyNavigationService.undoMove();
            return;
        }

        int sizeBefore = historyManager.getMoveHistory().size();
        historyNavigationService.undoMove();
        int sizeAfter = historyManager.getMoveHistory().size();
        int undoneCount = sizeBefore - sizeAfter;
        for (int i = 0; i < undoneCount; i++) {
            lavaManager.popLatestSnapshot();
        }
        lavaManager.reapplyEatenPieces(
                board,
                lavaManager.getSnapshotCounter(),
                moveExecutor.getWhiteCapturedPieces(),
                moveExecutor.getBlackCapturedPieces()
        );
    }

    public void goToMove(int moveIndex) {
        historyNavigationService.goToMove(moveIndex);
        if (!lavaManager.isEnabled()) return;
        int targetKey = moveIndex + 1;
        lavaManager.restoreToSnapshot(targetKey);
        lavaManager.reapplyEatenPieces(
                board, targetKey,
                moveExecutor.getWhiteCapturedPieces(),
                moveExecutor.getBlackCapturedPieces()
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Game actions
    // ─────────────────────────────────────────────────────────────────────────

    public void offerDraw() {
        if (stateManager.isDrawOffered()) {
            stateManager.setStatus(GameStatus.DRAW_AGREED);
            stateManager.setDrawOffered(false);
            timeManager.stopTimer();
            if (soundCallback != null) soundCallback.onDraw();
        } else {
            stateManager.setDrawOffered(true);
        }
    }

    public void resign() {
        stateManager.setStatus(stateManager.isWhiteTurn()
                ? GameStatus.BLACK_WINS_BY_RESIGNATION
                : GameStatus.WHITE_WINS_BY_RESIGNATION);
        timeManager.stopTimer();
        if (soundCallback != null) soundCallback.onCheckmate();
    }

    public void setupTimer(Runnable uiUpdater) {
        timeManager.setupTimer(uiUpdater);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Rule engine delegation
    // ─────────────────────────────────────────────────────────────────────────

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
        Spot kingSpot = ruleEngine.findKing(board, isWhite);
        if (kingSpot == null || kingSpot.getX() != rank) return false;
        Piece king = kingSpot.getPiece();
        if (!(king instanceof King) || ((King) king).hasMoved()) return false;

        int direction = isKingside ? 1 : -1;
        for (int c = kingSpot.getY() + direction; c >= 0 && c < 8; c += direction) {
            Piece p = board.getBox(rank, c).getPiece();
            if (p instanceof Rook && !((Rook) p).hasMoved() && p.isWhite() == isWhite) return true;
            if (p != null) break;
        }
        return false;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Temporary moves for AI
    // ─────────────────────────────────────────────────────────────────────────

    public void makeTemporaryMove(Move move) {
        stateManager.setWhiteTurn(
                temporaryMoveHandler.makeTemporaryMove(move, stateManager.isWhiteTurn()));
    }

    public void undoTemporaryMove(Move move) {
        stateManager.setWhiteTurn(
                temporaryMoveHandler.undoTemporaryMove(move, stateManager.isWhiteTurn()));
    }

    public void makeNullMove() {
        stateManager.setWhiteTurn(
                temporaryMoveHandler.makeNullMove(stateManager.isWhiteTurn()));
    }

    public void undoNullMove() {
        stateManager.setWhiteTurn(
                temporaryMoveHandler.undoNullMove(stateManager.isWhiteTurn()));
    }
}
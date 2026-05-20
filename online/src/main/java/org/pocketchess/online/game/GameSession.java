package org.pocketchess.online.game;

import org.pocketchess.core.ai.difficulty.AIDifficulty;
import org.pocketchess.core.game.model.GameStatus;
import org.pocketchess.core.game.model.TimeControl;
import org.pocketchess.core.gamemode.GameModeType;
import org.pocketchess.online.engine.ChessEngineAdapter;
import org.pocketchess.online.engine.MoveResult;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * In-memory state for one online game. Holds the engine adapter, two seats,
 * a shadow clock (server-authoritative), and minimal draw/undo bookkeeping.
 *
 * <p>Not thread-safe — all mutations go through {@link GameService}, which
 * synchronises on the session instance.
 */
public class GameSession {

    public enum LifecycleStage { WAITING_FOR_OPPONENT, ACTIVE, FINISHED, ABORTED }

    /** True for PGN-import "review" sessions — not persisted, not in your-games list. */
    private boolean review;
    public boolean isReview() { return review; }
    public void markReview() { this.review = true; }

    private final String id = UUID.randomUUID().toString();
    private final TimeControl timeControl;
    private final GameModeType variant;
    private final AIDifficulty aiDifficulty;
    private final ChessEngineAdapter engine;
    private final List<String> moveHistory = new ArrayList<>();
    private final List<String> fenHistory = new ArrayList<>();
    private final List<ChatLine> chat = new ArrayList<>();

    private Player white;
    private Player black;
    private LifecycleStage stage;

    /**
     * Reference time used to decrement the active side's clock. The clock
     * stays frozen at its initial value until the first move is played
     * (lichess-style — gives both sides a moment to settle).
     */
    private long turnStartMillis;
    private long whiteMillisLeft;
    private long blackMillisLeft;
    private boolean clockRunning;

    /** Display name of the player who currently has an open draw offer; null if none. */
    private String drawOfferBy;
    /** Display name of the player who currently has an open undo request; null if none. */
    private String undoRequestBy;
    /** Display name of the player who currently has an open rematch offer; null if none. */
    private String rematchOfferBy;
    /** New game id if the rematch was accepted — clients use it to redirect. */
    private String rematchToGameId;
    /** Wall-clock millis when the white player went offline; 0 if online. */
    private long whiteDisconnectedAt;
    /** Wall-clock millis when the black player went offline; 0 if online. */
    private long blackDisconnectedAt;
    /** Stamped at game creation so a mid-game DB lookup isn't needed. */
    private Integer whiteRating;
    private Integer blackRating;

    GameSession(Player white, Player black,
                TimeControl tc, GameModeType variant, AIDifficulty aiDifficulty) {
        this.white = white;
        this.black = black;
        this.timeControl = tc;
        this.variant = variant;
        this.aiDifficulty = aiDifficulty;
        this.engine = ChessEngineAdapter.newGame(tc, aiDifficulty, variant);

        boolean ready = white != null && black != null;
        this.stage = ready ? LifecycleStage.ACTIVE : LifecycleStage.WAITING_FOR_OPPONENT;
        long initial = tc.isUnlimited() ? Long.MAX_VALUE : tc.baseTimeSeconds() * 1000L;
        this.whiteMillisLeft = initial;
        this.blackMillisLeft = initial;
        this.turnStartMillis = 0L;
        this.clockRunning = false;
        this.fenHistory.add(engine.fen());           // ply 0 — the starting position
    }

    public String id() { return id; }
    public Player white() { return white; }
    public Player black() { return black; }
    public TimeControl timeControl() { return timeControl; }
    public GameModeType variant() { return variant; }
    public AIDifficulty aiDifficulty() { return aiDifficulty; }
    public ChessEngineAdapter engine() { return engine; }
    public List<String> moveHistory() { return moveHistory; }
    public List<String> fenHistory() { return fenHistory; }
    public List<ChatLine> chat() { return chat; }
    public LifecycleStage stage() { return stage; }
    public String drawOfferBy() { return drawOfferBy; }
    public String rematchOfferBy() { return rematchOfferBy; }
    public String rematchToGameId() { return rematchToGameId; }
    public long whiteDisconnectedAt() { return whiteDisconnectedAt; }
    public long blackDisconnectedAt() { return blackDisconnectedAt; }
    public Integer whiteRating() { return whiteRating; }
    public Integer blackRating() { return blackRating; }
    public void setWhiteRating(Integer r) { this.whiteRating = r; }
    public void setBlackRating(Integer r) { this.blackRating = r; }
    public String undoRequestBy() { return undoRequestBy; }

    public boolean whiteToMove() { return engine.isWhiteTurn(); }
    public GameStatus status() { return engine.status(); }
    public String fen() { return engine.fen(); }

    public Player sideToMove() { return whiteToMove() ? white : black; }
    public Player otherSide() { return whiteToMove() ? black : white; }

    public Player playerByName(String displayName) {
        if (displayName == null) return null;
        if (white != null && displayName.equals(white.name())) return white;
        if (black != null && displayName.equals(black.name())) return black;
        return null;
    }

    public boolean isOpenSeat() {
        return stage == LifecycleStage.WAITING_FOR_OPPONENT;
    }

    void seatOpponent(Player p) {
        if (white == null) white = p;
        else if (black == null) black = p;
        else throw new IllegalStateException("No seat free");
        if (white != null && black != null) {
            stage = LifecycleStage.ACTIVE;
        }
    }

    void markActive() {
        stage = LifecycleStage.ACTIVE;
    }

    void markFinished() {
        stage = LifecycleStage.FINISHED;
    }

    void markAborted() {
        stage = LifecycleStage.ABORTED;
    }

    boolean isClockRunning() { return clockRunning; }
    void setWhiteMillisLeft(long m) { this.whiteMillisLeft = m; }
    void setBlackMillisLeft(long m) { this.blackMillisLeft = m; }

    /** Snapshots remaining time for the live side; called right after a move. */
    long onMoveCompleted() {
        long now = System.currentTimeMillis();
        if (!timeControl.isUnlimited() && clockRunning) {
            long elapsed = Math.max(0, now - turnStartMillis);
            // Decrement the side that JUST moved (opposite of new whoToMove).
            if (engine.isWhiteTurn()) {
                blackMillisLeft = Math.max(0, blackMillisLeft - elapsed)
                        + timeControl.incrementSeconds() * 1000L;
            } else {
                whiteMillisLeft = Math.max(0, whiteMillisLeft - elapsed)
                        + timeControl.incrementSeconds() * 1000L;
            }
        }
        // The very first move sets the clock ticking from now on.
        clockRunning = true;
        turnStartMillis = now;
        return now;
    }

    /** Returns the millis-left for each side based on wall clock. */
    long[] livePresentation() {
        long w = whiteMillisLeft;
        long b = blackMillisLeft;
        if (stage == LifecycleStage.ACTIVE && clockRunning && !timeControl.isUnlimited()) {
            long now = System.currentTimeMillis();
            long elapsed = Math.max(0, now - turnStartMillis);
            if (engine.isWhiteTurn()) w = Math.max(0, w - elapsed);
            else                      b = Math.max(0, b - elapsed);
        }
        return new long[]{w, b};
    }

    /** Returns how long the currently moving side has before the flag falls. */
    long flagFallDelayMillis() {
        if (timeControl.isUnlimited()) return Long.MAX_VALUE;
        if (!clockRunning) return Long.MAX_VALUE;
        long left = engine.isWhiteTurn() ? whiteMillisLeft : blackMillisLeft;
        long now = System.currentTimeMillis();
        long elapsed = Math.max(0, now - turnStartMillis);
        return Math.max(0, left - elapsed);
    }

    void setDrawOfferBy(String name) { this.drawOfferBy = name; }
    void setUndoRequestBy(String name) { this.undoRequestBy = name; }
    void setRematchOfferBy(String name) { this.rematchOfferBy = name; }
    void setRematchToGameId(String id) { this.rematchToGameId = id; }
    void clearDrawOffer() { this.drawOfferBy = null; }
    void clearUndoRequest() { this.undoRequestBy = null; }
    void clearRematchOffer() { this.rematchOfferBy = null; }
    void setWhiteDisconnectedAt(long t) { this.whiteDisconnectedAt = t; }
    void setBlackDisconnectedAt(long t) { this.blackDisconnectedAt = t; }

    void recordMove(MoveResult mr) {
        moveHistory.add(mr.uci());
        fenHistory.add(mr.fen());
    }

    void rollbackLastMove() {
        if (!moveHistory.isEmpty()) {
            moveHistory.remove(moveHistory.size() - 1);
        }
        if (fenHistory.size() > 1) {
            fenHistory.remove(fenHistory.size() - 1);
        }
    }

    public record ChatLine(String from, String text, long timestamp) {}
}

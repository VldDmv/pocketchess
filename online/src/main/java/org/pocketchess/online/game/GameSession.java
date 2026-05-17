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

    public enum LifecycleStage { WAITING_FOR_OPPONENT, ACTIVE, FINISHED }

    private final String id = UUID.randomUUID().toString();
    private final TimeControl timeControl;
    private final GameModeType variant;
    private final AIDifficulty aiDifficulty;
    private final ChessEngineAdapter engine;
    private final List<String> moveHistory = new ArrayList<>();
    private final List<ChatLine> chat = new ArrayList<>();

    private Player white;
    private Player black;
    private LifecycleStage stage;

    /** Reference time used to decrement the active side's clock. */
    private long turnStartMillis;
    private long whiteMillisLeft;
    private long blackMillisLeft;

    /** Display name of the player who currently has an open draw offer; null if none. */
    private String drawOfferBy;
    /** Display name of the player who currently has an open undo request; null if none. */
    private String undoRequestBy;

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
        this.turnStartMillis = ready ? System.currentTimeMillis() : 0L;
    }

    public String id() { return id; }
    public Player white() { return white; }
    public Player black() { return black; }
    public TimeControl timeControl() { return timeControl; }
    public GameModeType variant() { return variant; }
    public AIDifficulty aiDifficulty() { return aiDifficulty; }
    public ChessEngineAdapter engine() { return engine; }
    public List<String> moveHistory() { return moveHistory; }
    public List<ChatLine> chat() { return chat; }
    public LifecycleStage stage() { return stage; }
    public String drawOfferBy() { return drawOfferBy; }
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
            turnStartMillis = System.currentTimeMillis();
        }
    }

    void markActive() {
        stage = LifecycleStage.ACTIVE;
        turnStartMillis = System.currentTimeMillis();
    }

    void markFinished() {
        stage = LifecycleStage.FINISHED;
    }

    /** Snapshots remaining time for the live side; called right after a move. */
    long onMoveCompleted() {
        long now = System.currentTimeMillis();
        if (!timeControl.isUnlimited()) {
            long elapsed = Math.max(0, now - turnStartMillis);
            // Time is decremented from the side that JUST moved — which is
            // the opposite of the new "whoToMove" reported by the engine.
            if (engine.isWhiteTurn()) {
                blackMillisLeft = Math.max(0, blackMillisLeft - elapsed)
                        + timeControl.incrementSeconds() * 1000L;
            } else {
                whiteMillisLeft = Math.max(0, whiteMillisLeft - elapsed)
                        + timeControl.incrementSeconds() * 1000L;
            }
        }
        turnStartMillis = now;
        return now;
    }

    /** Returns the millis-left for each side based on wall clock. */
    long[] livePresentation() {
        long now = System.currentTimeMillis();
        long w = whiteMillisLeft;
        long b = blackMillisLeft;
        if (stage == LifecycleStage.ACTIVE && !timeControl.isUnlimited()) {
            long elapsed = Math.max(0, now - turnStartMillis);
            if (engine.isWhiteTurn()) w = Math.max(0, w - elapsed);
            else                      b = Math.max(0, b - elapsed);
        }
        return new long[]{w, b};
    }

    /** Returns how long the currently moving side has before the flag falls. */
    long flagFallDelayMillis() {
        if (timeControl.isUnlimited()) return Long.MAX_VALUE;
        long left = engine.isWhiteTurn() ? whiteMillisLeft : blackMillisLeft;
        long now = System.currentTimeMillis();
        long elapsed = Math.max(0, now - turnStartMillis);
        return Math.max(0, left - elapsed);
    }

    void setDrawOfferBy(String name) { this.drawOfferBy = name; }
    void setUndoRequestBy(String name) { this.undoRequestBy = name; }
    void clearDrawOffer() { this.drawOfferBy = null; }
    void clearUndoRequest() { this.undoRequestBy = null; }

    void recordMove(MoveResult mr) {
        moveHistory.add(mr.uci());
    }

    void rollbackLastMove() {
        if (!moveHistory.isEmpty()) {
            moveHistory.remove(moveHistory.size() - 1);
        }
    }

    public record ChatLine(String from, String text, long timestamp) {}
}

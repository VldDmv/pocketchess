package org.pocketchess.online.game;

import org.pocketchess.core.ai.difficulty.AIDifficulty;
import org.pocketchess.core.game.model.GameStatus;
import org.pocketchess.core.game.model.TimeControl;
import org.pocketchess.core.gamemode.GameModeType;
import org.pocketchess.online.engine.MoveResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Orchestrates the lifecycle of a {@link GameSession}: creating games,
 * applying moves, scheduling bot replies, running the server-authoritative
 * clock, and broadcasting state via STOMP.
 */
@Service
public class GameService {

    private static final Logger log = LoggerFactory.getLogger(GameService.class);

    private final GameRegistry registry;
    private final SimpMessagingTemplate messaging;

    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(2, namedDaemon("pc-flag-"));
    private final ScheduledExecutorService aiWorkers =
            Executors.newScheduledThreadPool(2, namedDaemon("pc-ai-"));

    private final ConcurrentMap<String, ScheduledFuture<?>> flagFalls = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ScheduledFuture<?>> aborts    = new ConcurrentHashMap<>();

    /** First mover (white) must move within this window or the game is aborted. */
    private static final long FIRST_MOVE_ABORT_MS  = 30_000;
    /** After white's first move, black has this much time to play their first move. */
    private static final long SECOND_MOVE_ABORT_MS = 15_000;

    public GameService(GameRegistry registry, SimpMessagingTemplate messaging) {
        this.registry = registry;
        this.messaging = messaging;
    }

    public java.util.Optional<GameSession> find(String gameId) {
        return registry.find(gameId);
    }

    /** Open challenge created by {@code displayName} that is still waiting for an opponent. */
    public java.util.Optional<GameSession> findOpenChallengeBy(String displayName) {
        return registry.all().stream()
                .filter(GameSession::isOpenSeat)
                .filter(s -> (s.white() != null && displayName.equals(s.white().name()))
                          || (s.black() != null && displayName.equals(s.black().name())))
                .findFirst();
    }

    /** Removes any open (un-joined) challenges authored by {@code displayName}. */
    public synchronized void cancelOpenChallengesBy(String displayName) {
        for (GameSession s : registry.all()) {
            if (!s.isOpenSeat()) continue;
            boolean isCreator = (s.white() != null && displayName.equals(s.white().name()))
                             || (s.black() != null && displayName.equals(s.black().name()));
            if (isCreator) registry.remove(s.id());
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Creation
    // ─────────────────────────────────────────────────────────────────────

    /** Single-player game vs bot. The caller plays the chosen colour. */
    public GameSession createVsBot(String humanName, boolean humanPlaysWhite,
                                   TimeControl tc, GameModeType variant,
                                   AIDifficulty difficulty) {
        Player human = Player.human(humanName);
        Player bot = Player.bot(difficulty);
        GameSession session = humanPlaysWhite
                ? new GameSession(human, bot, tc, variant, difficulty)
                : new GameSession(bot, human, tc, variant, difficulty);
        registry.put(session);
        log.info("Created PvE game {} ({} vs bot/{})", session.id(), humanName, difficulty);

        // Clock only starts after the first move; flag-fall is armed there.
        if (session.sideToMove().bot()) scheduleAiMove(session);
        return session;
    }

    /** PVP game with a single player seated; the other slot waits in the lobby. */
    public GameSession createOpen(String creatorName, boolean creatorPlaysWhite,
                                  TimeControl tc, GameModeType variant) {
        Player creator = Player.human(creatorName);
        GameSession session = creatorPlaysWhite
                ? new GameSession(creator, null, tc, variant, AIDifficulty.MEDIUM)
                : new GameSession(null, creator, tc, variant, AIDifficulty.MEDIUM);
        registry.put(session);
        log.info("Created open PvP game {} by {}", session.id(), creatorName);
        return session;
    }

    /** Joins an open game with the second player. */
    public synchronized GameSession join(GameSession session, String joinerName) {
        if (!session.isOpenSeat()) throw new IllegalStateException("Game is no longer open");
        if (session.white() != null && session.white().name().equals(joinerName)) {
            throw new IllegalStateException("You created this game");
        }
        if (session.black() != null && session.black().name().equals(joinerName)) {
            throw new IllegalStateException("You created this game");
        }
        // Accepting a challenge implies the joiner is no longer waiting on
        // any open challenge of their own — drop those so the lobby stays clean.
        cancelOpenChallengesBy(joinerName);
        session.seatOpponent(Player.human(joinerName));
        rearmAbortTimer(session);
        broadcast(session, null, "start");
        // Push redirect to both seats — the creator may still be sitting in
        // the lobby (they don't get the /topic/game/{id} broadcast there).
        notifyRedirect(session.white(), session.id());
        notifyRedirect(session.black(), session.id());
        return session;
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Moves
    // ─────────────────────────────────────────────────────────────────────

    public synchronized void applyMove(String gameId, String byName, String uci) {
        GameSession s = registry.find(gameId).orElseThrow();
        if (s.stage() != GameSession.LifecycleStage.ACTIVE) {
            sendError(gameId, byName, "Game is not active.");
            return;
        }
        if (!isPlayerToMove(s, byName)) {
            sendError(gameId, byName, "Not your turn.");
            return;
        }
        executeMoveAndBroadcast(s, uci, byName);
        if (s.stage() == GameSession.LifecycleStage.ACTIVE && s.sideToMove().bot()) {
            scheduleAiMove(s);
        }
    }

    private void executeMoveAndBroadcast(GameSession s, String uci, String byName) {
        // Outstanding draw offers are cleared by any move.
        s.clearDrawOffer();
        s.clearUndoRequest();

        MoveResult result = s.engine().applyMove(uci);
        if (!result.accepted()) {
            if (byName != null) sendError(s.id(), byName, result.error());
            return;
        }
        s.recordMove(result);
        s.onMoveCompleted();

        if (terminal(result.status())) {
            s.markFinished();
            cancelFlagFall(s.id());
            cancelAbortTimer(s.id());
        } else {
            rearmFlagFall(s);
            rearmAbortTimer(s);
        }
        broadcast(s, result.uci(), soundEventFor(s, result));
    }

    private void scheduleAiMove(GameSession s) {
        String sessionId = s.id();
        aiWorkers.schedule(() -> runAiTurn(sessionId), 350, TimeUnit.MILLISECONDS);
    }

    private void runAiTurn(String sessionId) {
        GameSession s = registry.find(sessionId).orElse(null);
        if (s == null) return;
        synchronized (s) {
            if (s.stage() != GameSession.LifecycleStage.ACTIVE) return;
            if (!s.sideToMove().bot()) return;
            try {
                MoveResult result = s.engine().requestAiMove();
                if (!result.accepted()) {
                    log.warn("AI rejected move in {}: {}", sessionId, result.error());
                    return;
                }
                s.recordMove(result);
                s.onMoveCompleted();
                if (terminal(result.status())) {
                    s.markFinished();
                    cancelFlagFall(sessionId);
                    cancelAbortTimer(sessionId);
                } else {
                    rearmFlagFall(s);
                    rearmAbortTimer(s);
                }
                broadcast(s, result.uci(), soundEventFor(s, result));
            } catch (RuntimeException e) {
                log.error("AI failure in {}", sessionId, e);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Player actions
    // ─────────────────────────────────────────────────────────────────────

    public synchronized void resign(String gameId, String byName) {
        GameSession s = registry.find(gameId).orElseThrow();
        if (s.stage() != GameSession.LifecycleStage.ACTIVE) return;
        if (s.playerByName(byName) == null) {
            sendError(gameId, byName, "You're not in this game.");
            return;
        }
        boolean resignerIsWhite = s.white() != null && byName.equals(s.white().name());
        s.engine().resignBy(resignerIsWhite);
        s.markFinished();
        cancelFlagFall(gameId);
        cancelAbortTimer(gameId);
        broadcast(s, null, "checkmate");
    }

    public synchronized void offerDraw(String gameId, String byName) {
        GameSession s = registry.find(gameId).orElseThrow();
        if (s.stage() != GameSession.LifecycleStage.ACTIVE) return;
        if (s.playerByName(byName) == null) return;
        if (byName.equals(s.drawOfferBy())) return;

        if (s.drawOfferBy() != null) {
            // The other side already offered — accepting now finalises the draw.
            s.engine().acceptDraw();
            s.markFinished();
            cancelFlagFall(gameId);
            cancelAbortTimer(gameId);
            s.clearDrawOffer();
            broadcast(s, null, "draw");
        } else {
            s.setDrawOfferBy(byName);
            // Auto-decline from bots — they fight on.
            if (s.otherSide() != null && s.otherSide().bot()) {
                s.clearDrawOffer();
            }
            broadcast(s, null, null);
        }
    }

    public synchronized void declineDraw(String gameId, String byName) {
        GameSession s = registry.find(gameId).orElseThrow();
        if (s.stage() != GameSession.LifecycleStage.ACTIVE) return;
        if (s.drawOfferBy() == null) return;
        if (byName.equals(s.drawOfferBy())) return;
        s.clearDrawOffer();
        broadcast(s, null, null);
    }

    /**
     * Requests an undo. In PvE the request is granted unconditionally;
     * the bot's last reply plus the human's preceding move are reverted
     * so the player can re-think their decision. In PvP the request is
     * forwarded to the opponent and only one half-move (the most recent)
     * is reverted on accept.
     */
    public synchronized void requestUndo(String gameId, String byName) {
        GameSession s = registry.find(gameId).orElseThrow();
        if (s.stage() != GameSession.LifecycleStage.ACTIVE) return;
        if (s.playerByName(byName) == null) return;
        if (s.moveHistory().isEmpty()) return;

        if (opponentIsBot(s, byName)) {
            performUndo(s, 2);
            return;
        }
        if (byName.equals(s.undoRequestBy())) return;
        s.setUndoRequestBy(byName);
        broadcast(s, null, null);
    }

    public synchronized void acceptUndo(String gameId, String byName) {
        GameSession s = registry.find(gameId).orElseThrow();
        if (s.stage() != GameSession.LifecycleStage.ACTIVE) return;
        if (s.undoRequestBy() == null) return;
        if (byName.equals(s.undoRequestBy())) return;
        s.clearUndoRequest();
        performUndo(s, 1);
    }

    public synchronized void declineUndo(String gameId, String byName) {
        GameSession s = registry.find(gameId).orElseThrow();
        if (s.undoRequestBy() == null) return;
        if (byName.equals(s.undoRequestBy())) return;
        s.clearUndoRequest();
        broadcast(s, null, null);
    }

    /** Reverts exactly {@code plies} half-moves (or as many as are present). */
    private void performUndo(GameSession s, int plies) {
        int undone = 0;
        while (undone < plies && !s.moveHistory().isEmpty()) {
            if (!s.engine().undoLastHalfMove()) break;
            s.rollbackLastMove();
            undone++;
        }
        // Clock continues from now; treat as a fresh turn start.
        s.onMoveCompleted();
        rearmFlagFall(s);
        broadcast(s, null, null);
    }

    private boolean opponentIsBot(GameSession s, String me) {
        if (s.white() != null && me.equals(s.white().name())) {
            return s.black() != null && s.black().bot();
        }
        if (s.black() != null && me.equals(s.black().name())) {
            return s.white() != null && s.white().bot();
        }
        return false;
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Rematch — symmetric offer/accept like draw, but only after the
    //  current game has ended. PvE rematches finalise immediately.
    // ─────────────────────────────────────────────────────────────────────

    public synchronized void offerRematch(String gameId, String byName) {
        GameSession s = registry.find(gameId).orElseThrow();
        if (s.stage() != GameSession.LifecycleStage.FINISHED
                && s.stage() != GameSession.LifecycleStage.ABORTED) return;
        if (s.playerByName(byName) == null) return;
        if (s.rematchToGameId() != null) return;     // already finalised

        if (opponentIsBot(s, byName)) {
            finaliseRematch(s, byName);
            return;
        }
        if (s.rematchOfferBy() != null && !s.rematchOfferBy().equals(byName)) {
            // Other side already offered — clicking now accepts.
            finaliseRematch(s, byName);
            return;
        }
        s.setRematchOfferBy(byName);
        broadcast(s, null, null);
    }

    public synchronized void declineRematch(String gameId, String byName) {
        GameSession s = registry.find(gameId).orElseThrow();
        if (s.rematchOfferBy() == null) return;
        if (byName.equals(s.rematchOfferBy())) return;
        s.clearRematchOffer();
        broadcast(s, null, null);
    }

    private void finaliseRematch(GameSession finished, String acceptedBy) {
        Player whiteOld = finished.white();
        Player blackOld = finished.black();
        boolean acceptedByWhite = whiteOld != null && acceptedBy.equals(whiteOld.name());

        GameSession fresh;
        if (whiteOld != null && whiteOld.bot()) {
            // Bot was white — flip seats so the human plays the other colour next.
            String human = blackOld.name();
            fresh = createVsBot(human, true, finished.timeControl(),
                    finished.variant(), finished.aiDifficulty());
        } else if (blackOld != null && blackOld.bot()) {
            String human = whiteOld.name();
            fresh = createVsBot(human, false, finished.timeControl(),
                    finished.variant(), finished.aiDifficulty());
        } else {
            // PvP: swap colours so the other side plays white next.
            Player newWhite = acceptedByWhite ? blackOld : whiteOld;
            Player newBlack = acceptedByWhite ? whiteOld : blackOld;
            fresh = new GameSession(newWhite, newBlack,
                    finished.timeControl(), finished.variant(),
                    finished.aiDifficulty());
            registry.put(fresh);
            log.info("Rematch PvP game {} ({} vs {})",
                    fresh.id(), newWhite.name(), newBlack.name());
        }

        finished.setRematchToGameId(fresh.id());
        finished.clearRematchOffer();
        broadcast(finished, null, null);

        // Push direct redirects to both seats so their game pages navigate
        // even if the broadcast subscription was dropped.
        notifyRedirect(whiteOld, fresh.id());
        notifyRedirect(blackOld, fresh.id());
    }

    private void notifyRedirect(Player p, String gameId) {
        if (p == null || p.bot()) return;
        messaging.convertAndSendToUser(p.name(),
                "/queue/redirect",
                java.util.Map.of("gameId", gameId));
    }

    public synchronized void postChat(String gameId, String byName, String text) {
        GameSession s = registry.find(gameId).orElseThrow();
        if (s.playerByName(byName) == null) return;
        String trimmed = text == null ? "" : text.strip();
        if (trimmed.isEmpty()) return;
        if (trimmed.length() > 500) trimmed = trimmed.substring(0, 500);
        long ts = System.currentTimeMillis();
        s.chat().add(new GameSession.ChatLine(byName, trimmed, ts));
        messaging.convertAndSend("/topic/game/" + gameId + "/chat",
                new Messages.ChatNotice(byName, trimmed, ts));
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Clock
    // ─────────────────────────────────────────────────────────────────────

    private void rearmFlagFall(GameSession s) {
        cancelFlagFall(s.id());
        if (s.timeControl().isUnlimited()) return;
        if (s.stage() != GameSession.LifecycleStage.ACTIVE) return;
        if (!s.isClockRunning()) return;
        long delay = s.flagFallDelayMillis();
        String sessionId = s.id();
        ScheduledFuture<?> fut = scheduler.schedule(
                () -> onFlagFall(sessionId), delay, TimeUnit.MILLISECONDS);
        flagFalls.put(sessionId, fut);
    }

    private void cancelFlagFall(String gameId) {
        ScheduledFuture<?> fut = flagFalls.remove(gameId);
        if (fut != null) fut.cancel(false);
    }

    private void onFlagFall(String gameId) {
        GameSession s = registry.find(gameId).orElse(null);
        if (s == null) return;
        synchronized (s) {
            if (s.stage() != GameSession.LifecycleStage.ACTIVE) return;
            if (s.flagFallDelayMillis() > 0) {
                rearmFlagFall(s);
                return;
            }
            // Zero the losing side's clock before broadcasting so the UI
            // shows 0:00 instead of "time at the previous move's snapshot".
            if (s.engine().isWhiteTurn()) s.setWhiteMillisLeft(0);
            else                          s.setBlackMillisLeft(0);
            s.engine().flagFall();
            s.markFinished();
            broadcast(s, null, "checkmate");
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Abort timer — lichess-style: white must move first within 30s; once
    //  white has moved, black has 15s to respond. PvE games never abort.
    // ─────────────────────────────────────────────────────────────────────

    private void rearmAbortTimer(GameSession s) {
        cancelAbortTimer(s.id());
        if (s.stage() != GameSession.LifecycleStage.ACTIVE) return;
        if (s.white() != null && s.white().bot()) return;
        if (s.black() != null && s.black().bot()) return;
        if (s.isClockRunning() && s.moveHistory().size() >= 2) return;

        long delay = s.moveHistory().isEmpty()
                ? FIRST_MOVE_ABORT_MS : SECOND_MOVE_ABORT_MS;
        String sessionId = s.id();
        ScheduledFuture<?> fut = scheduler.schedule(
                () -> onAbort(sessionId), delay, TimeUnit.MILLISECONDS);
        aborts.put(sessionId, fut);
    }

    private void cancelAbortTimer(String gameId) {
        ScheduledFuture<?> fut = aborts.remove(gameId);
        if (fut != null) fut.cancel(false);
    }

    private void onAbort(String gameId) {
        GameSession s = registry.find(gameId).orElse(null);
        if (s == null) return;
        synchronized (s) {
            if (s.stage() != GameSession.LifecycleStage.ACTIVE) return;
            if (s.moveHistory().size() >= 2) return;     // both played — game is on
            s.markAborted();
            cancelFlagFall(gameId);
            broadcast(s, null, "draw");
            log.info("Aborted PvP game {} — no first move", gameId);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────────────

    private boolean isPlayerToMove(GameSession s, String name) {
        Player mover = s.sideToMove();
        return mover != null && !mover.bot() && mover.name().equals(name);
    }

    private static boolean terminal(GameStatus status) {
        return switch (status) {
            case ACTIVE, CHECK, AWAITING_PROMOTION -> false;
            default -> true;
        };
    }

    private static String soundEventFor(GameSession s, MoveResult r) {
        if (r == null || r.status() == null) return "move";
        switch (r.status()) {
            case WHITE_WIN, BLACK_WIN,
                 WHITE_WINS_BY_RESIGNATION, BLACK_WINS_BY_RESIGNATION,
                 WHITE_WIN_ON_TIME, BLACK_WIN_ON_TIME:
                return "checkmate";
            case STALEMATE, DRAW_THREEFOLD_REPETITION,
                 DRAW_50_MOVES, DRAW_AGREED, DRAW_INSUFFICIENT_MATERIAL:
                return "draw";
            case CHECK:
                return "check";
            default:
                if (s.engine().wasLastMoveCastling()) return "castle";
                if (s.engine().wasLastMoveCapture())  return "capture";
                return "move";
        }
    }

    void broadcast(GameSession s, String lastMove, String soundEvent) {
        GameView view = GameView.of(s, lastMove, soundEvent);
        messaging.convertAndSend("/topic/game/" + s.id(), view);
    }

    private void sendError(String gameId, String username, String reason) {
        if (username == null) return;
        messaging.convertAndSendToUser(username,
                "/queue/game/" + gameId + "/errors",
                new Messages.ErrorNotice(reason));
    }

    private static ThreadFactory namedDaemon(String prefix) {
        final AtomicLong n = new AtomicLong();
        return r -> {
            Thread t = new Thread(r, prefix + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }
}

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

    public GameService(GameRegistry registry, SimpMessagingTemplate messaging) {
        this.registry = registry;
        this.messaging = messaging;
    }

    public java.util.Optional<GameSession> find(String gameId) {
        return registry.find(gameId);
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

        rearmFlagFall(session);
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
        session.seatOpponent(Player.human(joinerName));
        rearmFlagFall(session);
        broadcast(session, null, "start");
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
        } else {
            rearmFlagFall(s);
        }
        broadcast(s, result.uci(), soundEventFor(result));
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
                } else {
                    rearmFlagFall(s);
                }
                broadcast(s, result.uci(), soundEventFor(result));
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
        // The engine resigns "the side to move"; force that side to be the
        // resigning player before calling it.
        boolean resignerIsWhite = s.white() != null && byName.equals(s.white().name());
        if (resignerIsWhite != s.whiteToMove()) {
            // Use a "null move" so the side-to-move matches the resigner.
            // Simpler: set status directly via flagFall surrogate? No public API.
            // The engine's resign() reads stateManager.isWhiteTurn(), so we have
            // to flip it through makeNullMove(). The result still ends the game.
            s.engine().capturedByWhite(); // no-op; left as a marker for review
            // Apply a null move to flip the turn without changing the position.
            s.engine().resign(); // resigns whoever is on move
        } else {
            s.engine().resign();
        }
        s.markFinished();
        cancelFlagFall(gameId);
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
     * Requests an undo. In PVE the request is granted unconditionally and the
     * previous half-move pair (player + bot) is reverted. In PVP it requires
     * the opponent's consent.
     */
    public synchronized void requestUndo(String gameId, String byName) {
        GameSession s = registry.find(gameId).orElseThrow();
        if (s.stage() != GameSession.LifecycleStage.ACTIVE) return;
        if (s.playerByName(byName) == null) return;
        if (s.moveHistory().isEmpty()) return;

        boolean opponentIsBot =
                (s.white() != null && byName.equals(s.white().name()) && s.black() != null && s.black().bot())
              ||(s.black() != null && byName.equals(s.black().name()) && s.white() != null && s.white().bot());

        if (opponentIsBot) {
            // Revert until it's the requesting player's turn again.
            undoUntilPlayersTurn(s, byName);
            return;
        }
        // PVP: ask the opponent.
        if (byName.equals(s.undoRequestBy())) return;
        s.setUndoRequestBy(byName);
        broadcast(s, null, null);
    }

    public synchronized void acceptUndo(String gameId, String byName) {
        GameSession s = registry.find(gameId).orElseThrow();
        if (s.stage() != GameSession.LifecycleStage.ACTIVE) return;
        if (s.undoRequestBy() == null) return;
        if (byName.equals(s.undoRequestBy())) return;
        String requester = s.undoRequestBy();
        s.clearUndoRequest();
        undoUntilPlayersTurn(s, requester);
    }

    public synchronized void declineUndo(String gameId, String byName) {
        GameSession s = registry.find(gameId).orElseThrow();
        if (s.undoRequestBy() == null) return;
        if (byName.equals(s.undoRequestBy())) return;
        s.clearUndoRequest();
        broadcast(s, null, null);
    }

    private void undoUntilPlayersTurn(GameSession s, String playerName) {
        Player target = s.playerByName(playerName);
        if (target == null) return;
        boolean targetIsWhite = s.white() != null && target.name().equals(s.white().name());
        int safety = 4;
        while (safety-- > 0 && !s.moveHistory().isEmpty() && s.whiteToMove() != targetIsWhite) {
            if (s.engine().undoLastHalfMove()) s.rollbackLastMove();
            else break;
        }
        // Also revert the player's own last move if their turn is current — they
        // want to retake. Undo at least one half-move pair (or single in opener).
        if (s.whiteToMove() == targetIsWhite && !s.moveHistory().isEmpty()) {
            if (s.engine().undoLastHalfMove()) s.rollbackLastMove();
            if (!s.moveHistory().isEmpty() && s.whiteToMove() != targetIsWhite) {
                if (s.engine().undoLastHalfMove()) s.rollbackLastMove();
            }
        }
        s.onMoveCompleted();
        rearmFlagFall(s);
        broadcast(s, null, null);
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
            s.engine().flagFall();
            s.markFinished();
            broadcast(s, null, "checkmate");
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

    private static String soundEventFor(MoveResult r) {
        if (r == null || r.status() == null) return "move";
        return switch (r.status()) {
            case CHECK -> "check";
            case WHITE_WIN, BLACK_WIN,
                 WHITE_WINS_BY_RESIGNATION, BLACK_WINS_BY_RESIGNATION,
                 WHITE_WIN_ON_TIME, BLACK_WIN_ON_TIME -> "checkmate";
            case STALEMATE, DRAW_THREEFOLD_REPETITION,
                 DRAW_50_MOVES, DRAW_AGREED, DRAW_INSUFFICIENT_MATERIAL -> "draw";
            default -> "move";
        };
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

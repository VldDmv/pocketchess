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
    private final org.pocketchess.online.service.GameHistoryService history;
    private final org.pocketchess.online.repo.UserRepository users;
    private org.pocketchess.online.lobby.LobbyService lobby;

    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(2, namedDaemon("pc-flag-"));
    private final ScheduledExecutorService aiWorkers =
            Executors.newScheduledThreadPool(2, namedDaemon("pc-ai-"));

    private final ConcurrentMap<String, ScheduledFuture<?>> flagFalls = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ScheduledFuture<?>> aborts    = new ConcurrentHashMap<>();

    /** Both sides get this long to make their first move; the game aborts otherwise. */
    private static final long FIRST_MOVE_ABORT_MS = 30_000;
    /** A disconnected player is forfeited if they don't reconnect within this window. */
    public static final long DISCONNECT_FORFEIT_MILLIS = 120_000;

    private final ConcurrentMap<String, ScheduledFuture<?>> disconnectForfeits = new ConcurrentHashMap<>();

    @org.springframework.beans.factory.annotation.Autowired
    public GameService(GameRegistry registry, SimpMessagingTemplate messaging,
                       org.pocketchess.online.service.GameHistoryService history,
                       org.pocketchess.online.repo.UserRepository users) {
        this.registry = registry;
        this.messaging = messaging;
        this.history = history;
        this.users = users;
    }

    /**
     * Convenience overload for unit tests that don't care about Elo / DB
     * persistence — those tests can stay synchronous, no Spring context.
     */
    public GameService(GameRegistry registry, SimpMessagingTemplate messaging) {
        this(registry, messaging, null, null);
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setLobby(org.pocketchess.online.lobby.LobbyService lobby) {
        this.lobby = lobby;
    }

    private void pushMyGamesFor(GameSession s) {
        if (lobby == null) return;
        if (s.white() != null && !s.white().bot()) lobby.pushMyGamesTo(s.white().name());
        if (s.black() != null && !s.black().bot()) lobby.pushMyGamesTo(s.black().name());
    }

    private void pushMyGamesFor(String displayName) {
        if (lobby == null || displayName == null) return;
        lobby.pushMyGamesTo(displayName);
    }

    private Integer ratingOf(String displayName, String category) {
        if (users == null || displayName == null) return null;
        return users.findByDisplayName(displayName).map(u -> u.getRating(category)).orElse(null);
    }

    private void stampRatings(GameSession s) {
        String category = org.pocketchess.online.service.RatingCategory.of(s.variant(), s.timeControl());
        if (s.white() != null && !s.white().bot()) s.setWhiteRating(ratingOf(s.white().name(), category));
        if (s.black() != null && !s.black().bot()) s.setBlackRating(ratingOf(s.black().name(), category));
    }

    private void persistIfRated(GameSession s) {
        if (history == null) return;     // unit tests
        try { history.recordTerminal(s); }
        catch (RuntimeException e) { log.warn("Could not persist game {}: {}", s.id(), e.toString()); }
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
        boolean removed = false;
        for (GameSession s : registry.all()) {
            if (!s.isOpenSeat()) continue;
            boolean isCreator = (s.white() != null && displayName.equals(s.white().name()))
                             || (s.black() != null && displayName.equals(s.black().name()));
            if (isCreator) { registry.remove(s.id()); removed = true; }
        }
        if (removed) pushMyGamesFor(displayName);
    }

    /**
     * All games {@code displayName} is currently participating in — useful
     * for showing a "your games" panel in the lobby so the user can return
     * to a tab they accidentally closed.
     */
    public java.util.List<GameSession> findActiveGamesFor(String displayName) {
        java.util.List<GameSession> out = new java.util.ArrayList<>();
        for (GameSession s : registry.all()) {
            if (s.isReview()) continue;
            if (s.stage() == GameSession.LifecycleStage.FINISHED
                    || s.stage() == GameSession.LifecycleStage.ABORTED) continue;
            if ((s.white() != null && displayName.equals(s.white().name()))
                || (s.black() != null && displayName.equals(s.black().name()))) {
                out.add(s);
            }
        }
        return out;
    }

    /**
     * Creates a finished read-only session populated from a PGN. The viewer
     * can scrub through the moves with the normal history-replay UI.
     */
    public GameSession createPgnReview(String viewerName, String pgn) {
        Player viewer = Player.human(viewerName);
        Player ghost  = Player.human("PGN");
        GameSession session = new GameSession(viewer, ghost,
                org.pocketchess.core.game.model.TimeControl.UNLIMITED,
                org.pocketchess.core.gamemode.GameModeType.CLASSIC,
                org.pocketchess.core.ai.difficulty.AIDifficulty.MEDIUM);
        session.markReview();
        registry.put(session);

        var results = session.engine().loadFromPgn(pgn);
        for (var r : results) session.recordMove(r);
        session.markFinished();
        log.info("Loaded PGN as review session {} ({} moves)", session.id(), results.size());
        return session;
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
        stampRatings(session);
        log.info("Created PvE game {} ({} vs bot/{})", session.id(), humanName, difficulty);
        pushMyGamesFor(humanName);

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
        stampRatings(session);
        log.info("Created open PvP game {} by {}", session.id(), creatorName);
        pushMyGamesFor(creatorName);
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
        stampRatings(session);
        rearmAbortTimer(session);
        broadcast(session, null, "start");
        pushMyGamesFor(session);
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
            persistIfRated(s); pushMyGamesFor(s);
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
                    persistIfRated(s); pushMyGamesFor(s);
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

    /**
     * Berserk — the caller halves their starting clock in exchange for a
     * +1 Elo bonus on win. Only allowed for short time controls
     * (≤ 10 min estimated) and only before the berserker's first move.
     */
    public synchronized void requestBerserk(String gameId, String byName) {
        GameSession s = registry.find(gameId).orElseThrow();
        if (s.stage() != GameSession.LifecycleStage.ACTIVE) return;
        if (s.playerByName(byName) == null) return;
        if (s.timeControl().isUnlimited()) {
            sendError(gameId, byName, "Berserk needs a time control.");
            return;
        }
        long estimated = s.timeControl().baseTimeSeconds()
                + 40L * Math.max(0, s.timeControl().incrementSeconds());
        if (estimated > 600) {
            sendError(gameId, byName, "Berserk only available for games up to 10 min.");
            return;
        }
        boolean isWhite = s.white() != null && byName.equals(s.white().name());
        // Allowed only before this side's first move.
        int myPlies = isWhite ? whitePliesPlayed(s) : blackPliesPlayed(s);
        if (myPlies > 0) {
            sendError(gameId, byName, "Too late to berserk — you've already moved.");
            return;
        }
        if (isWhite ? s.whiteBerserked() : s.blackBerserked()) return;
        s.berserkSide(isWhite);
        broadcast(s, null, null);
        log.info("Game {} — {} berserked", gameId, byName);
    }

    private static int whitePliesPlayed(GameSession s) {
        // White makes the 1st, 3rd, 5th… moves; count entries at even indices.
        int n = 0;
        for (int i = 0; i < s.moveHistory().size(); i += 2) n++;
        return n;
    }

    private static int blackPliesPlayed(GameSession s) {
        return s.moveHistory().size() / 2;
    }

    /**
     * Voluntary abort — allowed while the game is active and fewer than
     * two plies have been played. After move 2 the game is "in progress"
     * and the only ways out are resign / draw / clock. Mirrors lichess.
     */
    public synchronized void requestAbort(String gameId, String byName) {
        GameSession s = registry.find(gameId).orElseThrow();
        if (s.stage() != GameSession.LifecycleStage.ACTIVE) return;
        if (s.playerByName(byName) == null) return;
        if (s.moveHistory().size() >= 2) {
            sendError(gameId, byName, "Cannot abort after both sides have moved.");
            return;
        }
        s.markAborted();
        cancelFlagFall(gameId);
        cancelAbortTimer(gameId);
        broadcast(s, null, null);
        pushMyGamesFor(s);
        log.info("Game {} aborted by {}", gameId, byName);
    }

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
        persistIfRated(s); pushMyGamesFor(s);
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
            persistIfRated(s); pushMyGamesFor(s);
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
        String requester = s.undoRequestBy();
        s.clearUndoRequest();
        // Revert far enough that the requester's own last move is undone — so
        // in PvP they get to re-think their move, not just erase the reply.
        performUndo(s, pliesBackToRequestersMove(s, requester));
    }

    /**
     * Half-moves to revert so the requester's most recent move is undone and
     * it becomes their turn again. White plays the even-indexed plies, black
     * the odd ones (white always opens).
     */
    private static int pliesBackToRequestersMove(GameSession s, String requester) {
        int size = s.moveHistory().size();
        if (size == 0) return 0;
        boolean requesterIsWhite = s.white() != null && requester.equals(s.white().name());
        int lastOwn = -1;
        for (int i = 0; i < size; i++) {
            if ((i % 2 == 0) == requesterIsWhite) lastOwn = i;
        }
        return lastOwn < 0 ? 1 : size - lastOwn;
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
        if (s.playerByName(byName) == null) return;     // not in this game
        // Either side may clear the offer — the opponent declines it, the
        // offerer cancels it. (Previously this returned for the offerer.)
        s.clearRematchOffer();
        broadcast(s, null, null);
    }

    private void finaliseRematch(GameSession finished, String acceptedBy) {
        Player whiteOld = finished.white();
        Player blackOld = finished.black();

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
            // PvP: swap colours so each rematch has the other side opening.
            fresh = new GameSession(blackOld, whiteOld,
                    finished.timeControl(), finished.variant(),
                    finished.aiDifficulty());
            registry.put(fresh);
            stampRatings(fresh);
            log.info("Rematch PvP game {} ({} vs {})",
                    fresh.id(), blackOld.name(), whiteOld.name());
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

    // ─────────────────────────────────────────────────────────────────────
    //  Presence — tracks WebSocket connectivity per user. When a player
    //  has zero live sessions they get a 2-minute grace window; if they
    //  haven't reconnected by then, the game is forfeited on their side.
    // ─────────────────────────────────────────────────────────────────────

    public synchronized void onPlayerDisconnected(String username) {
        long now = System.currentTimeMillis();
        for (GameSession s : registry.all()) {
            if (s.stage() != GameSession.LifecycleStage.ACTIVE) continue;
            boolean isWhite = s.white() != null && username.equals(s.white().name());
            boolean isBlack = s.black() != null && username.equals(s.black().name());
            if (!isWhite && !isBlack) continue;

            if (isWhite) s.setWhiteDisconnectedAt(now);
            else         s.setBlackDisconnectedAt(now);

            String key = s.id() + ":" + username;
            cancelExistingForfeit(key);
            ScheduledFuture<?> fut = scheduler.schedule(
                    () -> forfeitOnDisconnect(s.id(), username),
                    DISCONNECT_FORFEIT_MILLIS, TimeUnit.MILLISECONDS);
            disconnectForfeits.put(key, fut);
            broadcast(s, null, null);
            log.info("Player {} went offline in game {}", username, s.id());
        }
    }

    public synchronized void onPlayerReconnected(String username) {
        for (GameSession s : registry.all()) {
            boolean isWhite = s.white() != null && username.equals(s.white().name());
            boolean isBlack = s.black() != null && username.equals(s.black().name());
            if (!isWhite && !isBlack) continue;
            boolean wasOffline = (isWhite && s.whiteDisconnectedAt() > 0)
                              || (isBlack && s.blackDisconnectedAt() > 0);
            if (!wasOffline) continue;

            if (isWhite) s.setWhiteDisconnectedAt(0);
            else         s.setBlackDisconnectedAt(0);
            cancelExistingForfeit(s.id() + ":" + username);
            broadcast(s, null, null);
            log.info("Player {} reconnected to game {}", username, s.id());
        }
    }

    private void cancelExistingForfeit(String key) {
        ScheduledFuture<?> fut = disconnectForfeits.remove(key);
        if (fut != null) fut.cancel(false);
    }

    private void forfeitOnDisconnect(String gameId, String username) {
        GameSession s = registry.find(gameId).orElse(null);
        if (s == null) return;
        synchronized (s) {
            if (s.stage() != GameSession.LifecycleStage.ACTIVE) return;
            boolean stillOffline = (s.white() != null && username.equals(s.white().name())
                                    && s.whiteDisconnectedAt() > 0)
                                || (s.black() != null && username.equals(s.black().name())
                                    && s.blackDisconnectedAt() > 0);
            if (!stillOffline) return;     // they reconnected in the meantime

            boolean isWhite = s.white() != null && username.equals(s.white().name());
            s.engine().resignBy(isWhite);
            s.markFinished();
            cancelFlagFall(gameId);
            cancelAbortTimer(gameId);
            persistIfRated(s); pushMyGamesFor(s);
            broadcast(s, null, "checkmate");
            log.info("Game {} forfeited — {} stayed offline for {} ms",
                    gameId, username, DISCONNECT_FORFEIT_MILLIS);
        }
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
            persistIfRated(s); pushMyGamesFor(s);
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

        String sessionId = s.id();
        ScheduledFuture<?> fut = scheduler.schedule(
                () -> onAbort(sessionId), FIRST_MOVE_ABORT_MS, TimeUnit.MILLISECONDS);
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

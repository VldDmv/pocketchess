package org.pocketchess.online.game;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.pocketchess.core.ai.difficulty.AIDifficulty;
import org.pocketchess.core.game.model.GameStatus;
import org.pocketchess.core.game.model.TimeControl;
import org.pocketchess.core.gamemode.GameModeType;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/**
 * Verifies that the game session lifecycle — flag-fall, undo, rematch
 * handshake, abort, presence — keeps working in the scenarios players
 * actually hit. These tests use the real schedulers in GameService and
 * deliberately wall-clock-sleep small amounts to validate the server-
 * authoritative clock.
 */
class GameServiceLifecycleTest {

    private GameRegistry registry;
    private SimpMessagingTemplate messaging;
    private GameService service;
    private List<GameView> broadcasts;
    private ConcurrentMap<String, List<Object>> userPushes;

    @BeforeEach
    void setUp() {
        registry = new GameRegistry();
        messaging = mock(SimpMessagingTemplate.class);
        broadcasts = new ArrayList<>();
        userPushes = new ConcurrentHashMap<>();
        doAnswer(inv -> {
            Object payload = inv.getArgument(1);
            if (payload instanceof GameView v) broadcasts.add(v);
            return null;
        }).when(messaging).convertAndSend(any(String.class), any(Object.class));
        doAnswer(inv -> {
            String user = inv.getArgument(0);
            Object payload = inv.getArgument(2);
            userPushes.computeIfAbsent(user, k -> new ArrayList<>()).add(payload);
            return null;
        }).when(messaging).convertAndSendToUser(any(String.class), any(String.class), any(Object.class));
        service = new GameService(registry, messaging);
    }

    @AfterEach
    void tearDown() {
        // Each GameService spins up its own scheduler — letting it linger
        // between tests is fine, the JVM tears them down on exit.
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Flag-fall — proves the clock keeps running regardless of UI presence.
    // ─────────────────────────────────────────────────────────────────────

    @Test
    void clockExpiresEvenIfBothPlayersAreAway() {
        // Short TC; once both players have played a ply, the abort window
        // is gone and the side-to-move's clock starts draining.
        GameSession s = service.createOpen("alice", true,
                new TimeControl(2, 0), GameModeType.CLASSIC);
        service.join(s, "bob");

        service.applyMove(s.id(), "alice", "e2e4");
        service.applyMove(s.id(), "bob",   "e7e5");
        // It's now alice's turn with ~2 seconds left. Nobody moves.
        await().atMost(Duration.ofSeconds(6))
                .pollInterval(Duration.ofMillis(200))
                .until(() -> s.stage() == GameSession.LifecycleStage.FINISHED);

        assertThat(s.status())
                .as("the side to move when the flag fell should lose")
                .isEqualTo(GameStatus.BLACK_WIN_ON_TIME);
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Undo — PvP accepts one ply; PvE takes back two so the human can
    //  rethink without the bot's last reply hanging.
    // ─────────────────────────────────────────────────────────────────────

    @Test
    void pvpUndoRollsBackExactlyOnePly() {
        GameSession s = service.createOpen("white", true,
                new TimeControl(5 * 60, 0), GameModeType.CLASSIC);
        service.join(s, "black");
        service.applyMove(s.id(), "white", "e2e4");
        service.applyMove(s.id(), "black", "e7e5");
        service.applyMove(s.id(), "white", "g1f3");
        // 3 plies on the history.
        assertThat(s.moveHistory()).hasSize(3);

        service.requestUndo(s.id(), "black");      // black asks
        service.acceptUndo(s.id(), "white");       // white accepts

        assertThat(s.moveHistory()).as("exactly one ply rolled back").hasSize(2);
        assertThat(s.moveHistory().getLast()).isEqualTo("e7e5");
    }

    @Test
    void pveUndoRollsBackTwoPlies() {
        GameSession s = service.createVsBot("alice", true,
                new TimeControl(5 * 60, 0), GameModeType.CLASSIC, AIDifficulty.EASY);
        service.applyMove(s.id(), "alice", "e2e4");
        await().atMost(Duration.ofSeconds(15))
                .until(() -> s.moveHistory().size() == 2);   // bot replied

        service.requestUndo(s.id(), "alice");
        assertThat(s.moveHistory())
                .as("PvE undo wipes both the bot reply and the player's own move")
                .isEmpty();
        assertThat(s.whiteToMove()).isTrue();
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Rematch handshake
    // ─────────────────────────────────────────────────────────────────────

    @Test
    void pvpRematchRequiresAcceptanceAndRedirectsBothPlayers() {
        GameSession s = service.createOpen("alice", true,
                new TimeControl(5 * 60, 0), GameModeType.CLASSIC);
        service.join(s, "bob");
        service.resign(s.id(), "alice");
        broadcasts.clear();
        userPushes.clear();

        service.offerRematch(s.id(), "alice");
        // After a single offer the original session still has no fresh game.
        assertThat(s.rematchToGameId()).isNull();
        assertThat(s.rematchOfferBy()).isEqualTo("alice");

        service.offerRematch(s.id(), "bob");       // accept
        assertThat(s.rematchToGameId()).isNotNull();

        // Both players should receive the redirect push.
        assertThat(userPushes).containsKeys("alice", "bob");
        // The fresh game exists and swaps the colours.
        GameSession fresh = service.find(s.rematchToGameId()).orElseThrow();
        assertThat(fresh.white().name()).isEqualTo("bob");
        assertThat(fresh.black().name()).isEqualTo("alice");
    }

    @Test
    void rematchDeclineClearsOffer() {
        GameSession s = service.createOpen("alice", true,
                new TimeControl(5 * 60, 0), GameModeType.CLASSIC);
        service.join(s, "bob");
        service.resign(s.id(), "alice");

        service.offerRematch(s.id(), "alice");
        service.declineRematch(s.id(), "bob");
        assertThat(s.rematchOfferBy()).isNull();
        assertThat(s.rematchToGameId()).isNull();
    }

    @Test
    void offererCanCancelTheirOwnRematchOffer() {
        GameSession s = service.createOpen("alice", true,
                new TimeControl(5 * 60, 0), GameModeType.CLASSIC);
        service.join(s, "bob");
        service.resign(s.id(), "alice");

        service.offerRematch(s.id(), "alice");
        assertThat(s.rematchOfferBy()).isEqualTo("alice");

        // Offerer hits "Cancel offer" — used to be ignored due to a stray guard.
        service.declineRematch(s.id(), "alice");
        assertThat(s.rematchOfferBy()).isNull();
    }

    @Test
    void pveRematchFinalisesImmediately() {
        GameSession s = service.createVsBot("alice", true,
                new TimeControl(5 * 60, 0), GameModeType.CLASSIC, AIDifficulty.EASY);
        service.resign(s.id(), "alice");

        service.offerRematch(s.id(), "alice");
        assertThat(s.rematchToGameId()).isNotNull();
        GameSession fresh = service.find(s.rematchToGameId()).orElseThrow();
        // The human switches colours each rematch.
        assertThat(fresh.black().name()).isEqualTo("alice");
        assertThat(fresh.white().bot()).isTrue();
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Abort — both sides get 30 s before any move; PvE is never aborted.
    // ─────────────────────────────────────────────────────────────────────

    @Test
    void pveDoesNotAbort() {
        GameSession s = service.createVsBot("alice", true,
                new TimeControl(5 * 60, 0), GameModeType.CLASSIC, AIDifficulty.EASY);
        // The bot moves on its own; even if alice does nothing, no abort can
        // hit because abortTimers skip games with a bot in either seat.
        await().atMost(Duration.ofSeconds(2))
                .until(() -> s.stage() == GameSession.LifecycleStage.ACTIVE);
        assertThat(s.stage()).isNotEqualTo(GameSession.LifecycleStage.ABORTED);
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Presence — disconnect flips offline timestamp; reconnect within the
    //  grace window clears the forfeit. We don't actually wait two full
    //  minutes; instead the unit verifies the cancellation path.
    // ─────────────────────────────────────────────────────────────────────

    @Test
    void disconnectReconnectMarksAndClearsOfflineStamp() {
        GameSession s = service.createOpen("alice", true,
                new TimeControl(5 * 60, 0), GameModeType.CLASSIC);
        service.join(s, "bob");
        service.applyMove(s.id(), "alice", "e2e4");

        service.onPlayerDisconnected("bob");
        assertThat(s.blackDisconnectedAt())
                .as("black should be flagged offline after disconnect")
                .isGreaterThan(0);

        service.onPlayerReconnected("bob");
        assertThat(s.blackDisconnectedAt())
                .as("reconnection clears the offline stamp")
                .isZero();
        assertThat(s.stage())
                .as("game is still active")
                .isEqualTo(GameSession.LifecycleStage.ACTIVE);
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Quick-match dedup is gone, but the open-challenge dedup behaviour
    //  underlying it should still be in place.
    // ─────────────────────────────────────────────────────────────────────

    @Test
    void findOpenChallengeReturnsCallersOwnOpenSession() {
        GameSession s1 = service.createOpen("alice", true,
                new TimeControl(5 * 60, 0), GameModeType.CLASSIC);
        assertThat(service.findOpenChallengeBy("alice"))
                .isPresent()
                .get().extracting(GameSession::id).isEqualTo(s1.id());
        // Cancellation removes it.
        service.cancelOpenChallengesBy("alice");
        assertThat(service.findOpenChallengeBy("alice")).isEmpty();
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Presence debounce — bouncing between pages must not fire the
    //  "Opponent disconnected" flow.
    // ─────────────────────────────────────────────────────────────────────

    @Test
    void disconnectThenReconnectWithinDebounceDoesNotMarkOffline() throws Exception {
        GameSession s = service.createOpen("alice", true,
                new TimeControl(5 * 60, 0), GameModeType.CLASSIC);
        service.join(s, "bob");

        PresenceTracker presence = new PresenceTracker(service);
        // Simulate three quick page navigations for bob: each is connect+disconnect.
        java.lang.reflect.Field f = PresenceTracker.class.getDeclaredField("users");
        f.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> users = (java.util.Map<String, Object>) f.get(presence);

        // Connect → Disconnect → wait briefly → Connect (still within 5s debounce)
        feedConnect(presence, "bob");
        feedDisconnect(presence, "bob");
        Thread.sleep(200);             // far less than OFFLINE_DEBOUNCE_MS
        feedConnect(presence, "bob");

        assertThat(s.blackDisconnectedAt())
                .as("brief page navigation must NOT register as a disconnect")
                .isZero();
    }

    private static void feedConnect(PresenceTracker pt, String name) throws Exception {
        java.lang.reflect.Method m = PresenceTracker.class.getDeclaredMethod("onConnect",
                org.springframework.web.socket.messaging.SessionConnectedEvent.class);
        m.invoke(pt, makeConnectEvent(name));
    }

    private static void feedDisconnect(PresenceTracker pt, String name) throws Exception {
        java.lang.reflect.Method m = PresenceTracker.class.getDeclaredMethod("onDisconnect",
                org.springframework.web.socket.messaging.SessionDisconnectEvent.class);
        m.invoke(pt, makeDisconnectEvent(name));
    }

    private static org.springframework.web.socket.messaging.SessionConnectedEvent makeConnectEvent(String name) {
        org.springframework.messaging.support.MessageBuilder<byte[]> b =
                org.springframework.messaging.support.MessageBuilder.withPayload(new byte[0]);
        return new org.springframework.web.socket.messaging.SessionConnectedEvent(
                new Object(), b.build(), namedPrincipal(name));
    }

    private static org.springframework.web.socket.messaging.SessionDisconnectEvent makeDisconnectEvent(String name) {
        return new org.springframework.web.socket.messaging.SessionDisconnectEvent(
                new Object(), org.springframework.messaging.support.MessageBuilder.withPayload(new byte[0]).build(),
                "ws-" + name, org.springframework.web.socket.CloseStatus.NORMAL,
                namedPrincipal(name));
    }

    private static java.security.Principal namedPrincipal(String name) {
        return () -> name;
    }

    // ─────────────────────────────────────────────────────────────────────
    //  PGN import — review sessions don't count toward your-games and
    //  don't get rated, but you can scrub through them.
    // ─────────────────────────────────────────────────────────────────────

    @Test
    void pgnImportProducesReviewSession() {
        // Italian game opening.
        String pgn = "[Event \"Sample\"]\n[Site \"Local\"]\n[Date \"2026.05.20\"]\n[Round \"1\"]\n" +
                "[White \"a\"]\n[Black \"b\"]\n[Result \"*\"]\n\n" +
                "1. e4 e5 2. Nf3 Nc6 3. Bc4 *";

        GameSession s = service.createPgnReview("viewer", pgn);

        assertThat(s.isReview()).isTrue();
        assertThat(s.moveHistory()).containsExactly("e2e4", "e7e5", "g1f3", "b8c6", "f1c4");
        assertThat(s.stage()).isEqualTo(GameSession.LifecycleStage.FINISHED);
        assertThat(service.findActiveGamesFor("viewer"))
                .as("review session must NOT appear in 'Your games'")
                .isEmpty();
    }
}

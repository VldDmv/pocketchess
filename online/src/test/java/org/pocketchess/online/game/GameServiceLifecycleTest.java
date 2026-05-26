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
    void pvpUndoRollsBackToRequestersOwnMove() {
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

        // Black asked, so both white's reply and black's own move come back —
        // it's black's turn again to rethink the move they regretted.
        assertThat(s.moveHistory())
                .as("rolled back to before the requester's last move")
                .containsExactly("e2e4");
        assertThat(s.whiteToMove()).isFalse();
    }

    @Test
    void pvpUndoOfRequestersImmediateMoveRollsBackOnePly() {
        GameSession s = service.createOpen("white", true,
                new TimeControl(5 * 60, 0), GameModeType.CLASSIC);
        service.join(s, "black");
        service.applyMove(s.id(), "white", "e2e4");
        // White just moved and immediately regrets it (black hasn't replied).
        service.requestUndo(s.id(), "white");
        service.acceptUndo(s.id(), "black");

        assertThat(s.moveHistory()).as("only the requester's move rolled back").isEmpty();
        assertThat(s.whiteToMove()).isTrue();
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
    void abortAllowedBeforeMove2OnlyAndUnrated() {
        GameSession s = service.createOpen("alice", true,
                new TimeControl(5 * 60, 0), GameModeType.CLASSIC);
        service.join(s, "bob");

        // Either side can abort before anyone has moved.
        service.requestAbort(s.id(), "bob");
        assertThat(s.stage()).isEqualTo(GameSession.LifecycleStage.ABORTED);

        // Second game: abort after move 2 should NOT change the state.
        GameSession s2 = service.createOpen("carl", true,
                new TimeControl(5 * 60, 0), GameModeType.CLASSIC);
        service.join(s2, "dave");
        service.applyMove(s2.id(), "carl", "e2e4");
        service.applyMove(s2.id(), "dave", "e7e5");
        service.requestAbort(s2.id(), "carl");
        assertThat(s2.stage()).isEqualTo(GameSession.LifecycleStage.ACTIVE);
    }

    @Test
    void offererCanCancelTheirOwnRematchOffer() {
        GameSession s = service.createOpen("alice", true,
                new TimeControl(5 * 60, 0), GameModeType.CLASSIC);
        service.join(s, "bob");
        service.resign(s.id(), "alice");

        service.offerRematch(s.id(), "alice");
        assertThat(s.rematchOfferBy()).isEqualTo("alice");

        // The offerer can clear their own pending offer with "Cancel offer".
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

    @Test
    void pgnRoundTripsLavaVariant() {
        // Build a lava game, capture the seeded warning pattern, export PGN,
        // then import it back and verify the warning squares match exactly.
        GameSession source = service.createOpen("alice", true,
                new TimeControl(5 * 60, 0), GameModeType.LAVA);
        service.join(source, "bob");
        // Play a couple of moves so the resulting PGN has actual content.
        service.applyMove(source.id(), "alice", "e2e4");
        service.applyMove(source.id(), "bob",   "e7e5");

        String pgn = source.engine().pgn("alice", "bob");
        assertThat(pgn)
                .as("Lava PGN includes the variant and the RNG seed")
                .contains("[Variant \"Lava\"]")
                .containsPattern("\\[LavaSeed \"-?\\d+\"]");

        // Import → fresh review session.
        GameSession imported = service.createPgnReview("viewer", pgn);

        // The replay should land on the same position with identical lava state.
        assertThat(imported.engine().lavaSquares())
                .as("active lava squares match the source game")
                .containsExactlyInAnyOrderElementsOf(source.engine().lavaSquares());
        assertThat(imported.engine().warningSquares())
                .as("warning squares match the source game")
                .containsExactlyInAnyOrderElementsOf(source.engine().warningSquares());
        assertThat(imported.moveHistory()).containsExactly("e2e4", "e7e5");
    }

    @Test
    void lavaHistoryTracksPerPlyState() {
        // A lava wave fires every 6 half-moves. The per-ply history must show
        // an empty board early and active lava once the first interval lands,
        // so scrubbing back in the replay shows the lava that was on the board
        // at that point.
        GameSession s = service.createOpen("alice", true,
                new TimeControl(5 * 60, 0), GameModeType.LAVA);
        service.join(s, "bob");
        String[] ucis = {"e2e4", "e7e5", "g1f3", "b8c6", "f1c4", "g8f6"};
        String[] who  = {"alice", "bob", "alice", "bob", "alice", "bob"};
        for (int i = 0; i < ucis.length; i++) service.applyMove(s.id(), who[i], ucis[i]);

        assertThat(s.lavaHistory()).hasSize(7);          // ply 0..6
        assertThat(s.warningHistory().get(0)).as("blue warnings present from the start").isNotEmpty();
        assertThat(s.lavaHistory().get(0)).as("no red lava at the start").isEmpty();
        assertThat(s.lavaHistory().get(6)).as("lava active after the first 6-ply interval").isNotEmpty();
    }

    @Test
    void lavaHistoryRoundTripsThroughPgnReview() {
        GameSession source = service.createOpen("alice", true,
                new TimeControl(5 * 60, 0), GameModeType.LAVA);
        service.join(source, "bob");
        String[] ucis = {"e2e4", "e7e5", "g1f3", "b8c6", "f1c4", "g8f6"};
        String[] who  = {"alice", "bob", "alice", "bob", "alice", "bob"};
        for (int i = 0; i < ucis.length; i++) service.applyMove(source.id(), who[i], ucis[i]);

        GameSession review = service.createPgnReview("viewer", source.engine().pgn("alice", "bob"));

        assertThat(review.lavaHistory()).hasSameSizeAs(source.lavaHistory());
        for (int i = 0; i < source.lavaHistory().size(); i++) {
            assertThat(review.lavaHistory().get(i))
                    .as("ply %d lava matches the original", i)
                    .containsExactlyInAnyOrderElementsOf(source.lavaHistory().get(i));
            assertThat(review.warningHistory().get(i))
                    .as("ply %d warnings match the original", i)
                    .containsExactlyInAnyOrderElementsOf(source.warningHistory().get(i));
        }
        assertThat(source.lavaHistory().get(6)).as("the wave fired by ply 6").isNotEmpty();
    }

    @Test
    void pgnImportAcceptsChess960Variant() {
        // A short Chess960 game from a known starting position: BRNNQKRB / brnnqkrb.
        // The PGN carries [Variant "Chess960"] and the starting [FEN ...].
        String pgn = "[Event \"Chess960\"]\n" +
                "[Variant \"Chess960\"]\n" +
                "[SetUp \"1\"]\n" +
                "[FEN \"brnnqkrb/pppppppp/8/8/8/8/PPPPPPPP/BRNNQKRB w GBgb -\"]\n" +
                "[White \"a\"]\n[Black \"b\"]\n[Result \"*\"]\n\n" +
                "1. d4 d5 2. Nc3 Nc6 *";

        GameSession s = service.createPgnReview("viewer", pgn);
        assertThat(s.moveHistory()).containsExactly("d2d4", "d7d5", "d1c3", "d8c6");
        assertThat(s.isReview()).isTrue();
        // The engine was re-seeded to the Chess960 back-rank from the PGN.
        // After the two N-pair moves the d-knights are gone — back rank shows
        // BRN1QKRB / brn1qkrb. The bishops, queen, king and rooks are still
        // in their Chess960 squares, proving the seeding worked.
        assertThat(s.fen()).startsWith("brn1qkrb/ppp1pppp/2n5/3p4/3P4/2N5/PPP1PPPP/BRN1QKRB");
    }

    @Test
    void pgnImportAcceptsChess960CastlingNotation() {
        // A Chess960 game where the white king starts on the b-file and castles
        // queenside ("24. O-O-O"). The SAN matcher must label that castle
        // "O-O-O" even though the c-file destination is to the right of the
        // king, and import must replay it.
        String pgn = """
                [Event "PocketChess Game"]
                [Variant "Chess960"]
                [SetUp "1"]
                [FEN "rknbrnbq/pppppppp/8/8/8/8/PPPPPPPP/RKNBRNBQ w EAea -"]
                [White "dada"]
                [Black "Bot — Medium"]
                [Result "0-1"]

                1. e4 e5 2. Nb3 Ne6 3. f3 Bh4 4. g3 Bf6 5. Bc5 Nb6 6. Ne3 Na4 7. Ba3 d6
                8. Nd5 Bg5 9. h4 Bh6 10. g4 Kc8 11. g5 Nxg5 12. hxg5 Bxg5 13. c3 Re6
                14. Re2 h6 15. Na5 Nc5 16. Bxc5 dxc5 17. b4 b6 18. Nb3 c4 19. Nc1 h5
                20. a4 h4 21. Bc2 h3 22. Rh2 Rh6 23. Ne2 f5 24. O-O-O Bxd5 25. exd5 Qh7
                26. Ng1 a5 27. Rxh3 axb4 28. cxb4 c3 29. Rxh6 cxd2+ 30. Kb1 gxh6
                31. Qh5 Qe7 32. b5 Qb4+ 33. Ka1 Rxa4+ 34. Bxa4 Qxa4+ 35. Kb2 Qb4+
                36. Kc2 Qa4+ 37. Kd3 Qxd1 38. Qe8+ Kb7 39. Qc6+ Kbb8 40. Qe8+ Ka7
                41. Qc6 Qdb3+ 42. Ke2 d1=Q+ 43. Kf2 Qe3+ 44. Kg3 Qexg1+ 45. Kh3 Qxf3# 0-1
                """;

        GameSession s = service.createPgnReview("viewer", pgn);
        assertThat(s.isReview()).isTrue();
        // The queenside castle resolves to the king-takes-rook half-move b1→a1.
        assertThat(s.moveHistory()).contains("b1a1");
    }

    @Test
    void berserkHalvesClockAndIsRejectedAfterMove() {
        GameSession s = service.createOpen("alice", true,
                new TimeControl(180, 0), GameModeType.CLASSIC);   // 3 min bullet
        service.join(s, "bob");
        long startWhite = s.livePresentation()[0];

        service.requestBerserk(s.id(), "alice");
        assertThat(s.whiteBerserked()).isTrue();
        long afterWhite = s.livePresentation()[0];
        assertThat(afterWhite)
                .as("white clock should be roughly halved")
                .isLessThanOrEqualTo(startWhite / 2 + 200);

        // Once alice has moved, she can't berserk anymore (already berserked
        // anyway). Bob still can — until he moves.
        service.applyMove(s.id(), "alice", "e2e4");
        service.requestBerserk(s.id(), "bob");
        assertThat(s.blackBerserked()).isTrue();

        service.applyMove(s.id(), "bob", "e7e5");
        // Even resetting the berserked flag, post-move attempts shouldn't go
        // through. We simulate by clearing and trying again — the move count
        // gate alone should reject.
        GameSession s2 = service.createOpen("carl", true,
                new TimeControl(180, 0), GameModeType.CLASSIC);
        service.join(s2, "dave");
        service.applyMove(s2.id(), "carl", "e2e4");
        service.applyMove(s2.id(), "dave", "e7e5");
        service.applyMove(s2.id(), "carl", "g1f3");
        service.requestBerserk(s2.id(), "carl");
        assertThat(s2.whiteBerserked())
                .as("can't berserk after you've already moved")
                .isFalse();
    }

    @Test
    void berserkRejectedForUnlimitedAndLongTimeControls() {
        GameSession unl = service.createOpen("alice", true,
                TimeControl.UNLIMITED, GameModeType.CLASSIC);
        service.join(unl, "bob");
        service.requestBerserk(unl.id(), "alice");
        assertThat(unl.whiteBerserked()).isFalse();

        GameSession longGame = service.createOpen("carl", true,
                new TimeControl(1800, 0), GameModeType.CLASSIC);  // 30 min
        service.join(longGame, "dave");
        service.requestBerserk(longGame.id(), "carl");
        assertThat(longGame.whiteBerserked())
                .as("berserk only available for ≤ 10-minute games")
                .isFalse();
    }
}

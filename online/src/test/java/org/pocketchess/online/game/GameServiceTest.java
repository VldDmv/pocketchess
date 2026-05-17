package org.pocketchess.online.game;

import org.junit.jupiter.api.Test;
import org.pocketchess.core.ai.difficulty.AIDifficulty;
import org.pocketchess.core.game.model.GameStatus;
import org.pocketchess.core.game.model.TimeControl;
import org.pocketchess.core.gamemode.GameModeType;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;

class GameServiceTest {

    @Test
    void pveGame_appliesPlayerMoveAndScheduledBotReply() {
        SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
        GameRegistry registry = new GameRegistry();
        GameService service = new GameService(registry, messaging);

        List<GameView> views = new ArrayList<>();
        doAnswer(inv -> {
            Object payload = inv.getArgument(1);
            if (payload instanceof GameView v) views.add(v);
            return null;
        }).when(messaging).convertAndSend(any(String.class), any(Object.class));

        GameSession s = service.createVsBot("alice", true,
                new TimeControl(5 * 60, 0),
                GameModeType.CLASSIC, AIDifficulty.EASY);

        service.applyMove(s.id(), "alice", "e2e4");

        // First broadcast = the human's move; second = bot's reply (asynchronously).
        await().atMost(Duration.ofSeconds(15)).until(() -> views.size() >= 2);

        GameView afterHuman = views.get(0);
        assertThat(afterHuman.lastMove()).isEqualTo("e2e4");
        assertThat(afterHuman.moveHistory()).containsExactly("e2e4");
        assertThat(afterHuman.sanHistory()).containsExactly("e4");
        assertThat(afterHuman.whiteToMove()).isFalse();
        assertThat(afterHuman.legalMoves()).isNotEmpty();
        assertThat(afterHuman.legalMoves()).allMatch(uci -> uci.length() == 4 || uci.length() == 5);

        GameView afterBot = views.get(1);
        assertThat(afterBot.moveHistory()).hasSize(2);
        assertThat(afterBot.lastMove()).matches("[a-h][1-8][a-h][1-8][qrbn]?");
        assertThat(afterBot.whiteToMove()).isTrue();
        assertThat(afterBot.status()).isEqualTo(GameStatus.ACTIVE);
    }

    @Test
    void rejectsMoveFromWrongPlayer() {
        SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
        GameService service = new GameService(new GameRegistry(), messaging);

        GameSession s = service.createOpen("white-player", true,
                new TimeControl(5 * 60, 0), GameModeType.CLASSIC);
        service.join(s, "black-player");

        // It's white's turn but black tries to move.
        service.applyMove(s.id(), "black-player", "e7e5");

        assertThat(s.moveHistory()).isEmpty();
        assertThat(s.fen()).startsWith("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w");
    }

    @Test
    void resignEndsGameWithCorrectWinner() {
        SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
        GameService service = new GameService(new GameRegistry(), messaging);
        GameSession s = service.createOpen("white-player", true,
                new TimeControl(5 * 60, 0), GameModeType.CLASSIC);
        service.join(s, "black-player");

        service.resign(s.id(), "white-player");

        assertThat(s.stage()).isEqualTo(GameSession.LifecycleStage.FINISHED);
        // Whoever resigned should lose: white-player resigned, so black wins.
        assertThat(s.status()).isEqualTo(GameStatus.BLACK_WINS_BY_RESIGNATION);
    }
}

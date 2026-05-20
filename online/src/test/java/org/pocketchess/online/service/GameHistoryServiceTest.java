package org.pocketchess.online.service;

import org.junit.jupiter.api.Test;
import org.pocketchess.core.ai.difficulty.AIDifficulty;
import org.pocketchess.core.game.model.TimeControl;
import org.pocketchess.core.gamemode.GameModeType;
import org.pocketchess.online.domain.GameRecord;
import org.pocketchess.online.domain.User;
import org.pocketchess.online.game.GameRegistry;
import org.pocketchess.online.game.GameService;
import org.pocketchess.online.game.GameSession;
import org.pocketchess.online.repo.GameRecordRepository;
import org.pocketchess.online.repo.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DataJpaTest
@Import({EloService.class, GameHistoryService.class})
class GameHistoryServiceTest {

    @Autowired UserRepository users;
    @Autowired GameRecordRepository records;
    @Autowired GameHistoryService history;
    @Autowired EloService elo;

    @Test
    void recordsTerminalPvPGameAndUpdatesRatings() {
        User w = newUser("alice", 1200);
        User b = newUser("bob",   1200);

        GameRegistry reg = new GameRegistry();
        GameService svc = new GameService(reg, mock(SimpMessagingTemplate.class), history, users);
        GameSession s = svc.createOpen("alice", true,
                new TimeControl(300, 0), GameModeType.CLASSIC);
        svc.join(s, "bob");
        svc.applyMove(s.id(), "alice", "e2e4");
        svc.applyMove(s.id(), "bob",   "e7e5");
        // White resigns → black wins.
        svc.resign(s.id(), "alice");

        // The record was persisted and ratings shifted in alice's favour... no
        // wait — she's the resigner, so she loses 16 and bob gains 16 at 1200/1200.
        User aliceNow = users.findByDisplayName("alice").orElseThrow();
        User bobNow   = users.findByDisplayName("bob").orElseThrow();
        assertThat(aliceNow.getElo()).isEqualTo(1184);
        assertThat(bobNow.getElo()).isEqualTo(1216);

        List<GameRecord> recent = records.findRecentByUser(aliceNow,
                org.springframework.data.domain.PageRequest.of(0, 5));
        assertThat(recent).hasSize(1);
        GameRecord r = recent.get(0);
        assertThat(r.getOutcome()).isEqualTo(GameRecord.Outcome.BLACK_WIN);
        assertThat(r.getWhiteEloBefore()).isEqualTo(1200);
        assertThat(r.getWhiteEloAfter()).isEqualTo(1184);
        assertThat(r.getBlackEloBefore()).isEqualTo(1200);
        assertThat(r.getBlackEloAfter()).isEqualTo(1216);
        assertThat(r.getPgn()).contains("[White \"alice\"]")
                              .contains("[Black \"bob\"]");
    }

    @Test
    void recordingIsIdempotent() {
        User w = newUser("carol", 1500);
        User b = newUser("dave",  1500);

        GameRegistry reg = new GameRegistry();
        GameService svc = new GameService(reg, mock(SimpMessagingTemplate.class), history, users);
        GameSession s = svc.createOpen("carol", true,
                new TimeControl(300, 0), GameModeType.CLASSIC);
        svc.join(s, "dave");
        svc.resign(s.id(), "dave");

        // Invoke a second time directly — must not double-apply the Elo swing.
        history.recordTerminal(s);

        assertThat(users.findByDisplayName("carol").orElseThrow().getElo()).isEqualTo(1516);
        assertThat(users.findByDisplayName("dave" ).orElseThrow().getElo()).isEqualTo(1484);
        assertThat(records.findBySessionId(s.id())).isPresent();
    }

    @Test
    void pveGameIsNotRated() {
        User human = newUser("eve", 1400);
        GameRegistry reg = new GameRegistry();
        GameService svc = new GameService(reg, mock(SimpMessagingTemplate.class), history, users);
        GameSession s = svc.createVsBot("eve", true,
                new TimeControl(300, 0), GameModeType.CLASSIC, AIDifficulty.EASY);
        svc.resign(s.id(), "eve");

        assertThat(users.findByDisplayName("eve").orElseThrow().getElo())
                .as("PvE games should not move the human's rating")
                .isEqualTo(1400);
        assertThat(records.findBySessionId(s.id()))
                .as("PvE games should not be persisted")
                .isEmpty();
    }

    private User newUser(String name, int rating) {
        User u = new User();
        u.setDisplayName(name);
        u.setPasswordHash("x");
        u.setElo(rating);
        return users.save(u);
    }
}

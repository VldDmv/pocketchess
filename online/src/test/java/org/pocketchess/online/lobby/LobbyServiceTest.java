package org.pocketchess.online.lobby;

import org.junit.jupiter.api.Test;
import org.pocketchess.core.game.model.TimeControl;
import org.pocketchess.core.gamemode.GameModeType;
import org.pocketchess.online.game.GameRegistry;
import org.pocketchess.online.game.GameService;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class LobbyServiceTest {

    @Test
    void categorisesByLichessFormula() {
        // estimated = base + 40 * increment
        assertThat(LobbyEntry.categorise(15,   0, false)).isEqualTo("ULTRABULLET"); // 15
        assertThat(LobbyEntry.categorise(29,   0, false)).isEqualTo("ULTRABULLET"); // 29
        assertThat(LobbyEntry.categorise(30,   0, false)).isEqualTo("BULLET");      // 30
        assertThat(LobbyEntry.categorise(60,   0, false)).isEqualTo("BULLET");      // 60
        assertThat(LobbyEntry.categorise(60,   1, false)).isEqualTo("BULLET");      // 100
        assertThat(LobbyEntry.categorise(179,  0, false)).isEqualTo("BULLET");      // 179
        assertThat(LobbyEntry.categorise(180,  0, false)).isEqualTo("BLITZ");       // 180
        assertThat(LobbyEntry.categorise(120,  2, false)).isEqualTo("BLITZ");       // 200
        assertThat(LobbyEntry.categorise(180,  2, false)).isEqualTo("BLITZ");       // 260
        assertThat(LobbyEntry.categorise(300,  3, false)).isEqualTo("BLITZ");       // 420
        assertThat(LobbyEntry.categorise(300,  5, false)).isEqualTo("RAPID");       // 500
        assertThat(LobbyEntry.categorise(479,  0, false)).isEqualTo("BLITZ");       // 479
        assertThat(LobbyEntry.categorise(480,  0, false)).isEqualTo("RAPID");       // 480
        assertThat(LobbyEntry.categorise(900,  0, false)).isEqualTo("RAPID");       // 900
        assertThat(LobbyEntry.categorise(900, 10, false)).isEqualTo("RAPID");       // 1300
        assertThat(LobbyEntry.categorise(1499, 0, false)).isEqualTo("RAPID");       // 1499
        assertThat(LobbyEntry.categorise(1500, 0, false)).isEqualTo("CLASSICAL");   // 1500
        assertThat(LobbyEntry.categorise(3600, 0, false)).isEqualTo("CLASSICAL");   // 3600
        assertThat(LobbyEntry.categorise(60,   0, true )).isEqualTo("UNLIMITED");
    }

    @Test
    void openGamesAreSortedByCategoryThenBaseTime() {
        SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
        GameRegistry registry = new GameRegistry();
        GameService gameService = new GameService(registry, messaging);
        LobbyService lobby = new LobbyService(registry, messaging);

        // Create challenges in a deliberately mixed order.
        gameService.createOpen("classy",  true, new TimeControl(1800, 0), GameModeType.CLASSIC);
        gameService.createOpen("rapider", true, new TimeControl( 600, 0), GameModeType.CLASSIC);
        gameService.createOpen("bullet1", true, new TimeControl(  30, 0), GameModeType.CLASSIC);
        gameService.createOpen("blitz5",  true, new TimeControl( 300, 0), GameModeType.CLASSIC);
        gameService.createOpen("bullet2", true, new TimeControl(  60, 0), GameModeType.CLASSIC);

        List<LobbyEntry> ordered = lobby.openGames();
        assertThat(ordered).extracting(LobbyEntry::creatorName)
                .as("BULLET (shorter first), then BLITZ, RAPID, CLASSICAL")
                .containsExactly("bullet1", "bullet2", "blitz5", "rapider", "classy");
    }

    @Test
    void cancelledOpenChallengesDropOutOfTheList() {
        SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
        GameRegistry registry = new GameRegistry();
        GameService gameService = new GameService(registry, messaging);
        LobbyService lobby = new LobbyService(registry, messaging);

        gameService.createOpen("alice", true,  new TimeControl(300, 0), GameModeType.CLASSIC);
        gameService.createOpen("bob",   false, new TimeControl(180, 0), GameModeType.CLASSIC);
        assertThat(lobby.openGames()).hasSize(2);

        gameService.cancelOpenChallengesBy("alice");
        assertThat(lobby.openGames())
                .extracting(LobbyEntry::creatorName)
                .containsExactly("bob");
    }

    @Test
    void joinedChallengesDisappearFromOpenList() {
        SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
        GameRegistry registry = new GameRegistry();
        GameService gameService = new GameService(registry, messaging);
        LobbyService lobby = new LobbyService(registry, messaging);

        var s = gameService.createOpen("alice", true,
                new TimeControl(300, 0), GameModeType.CLASSIC);
        gameService.join(s, "bob");
        assertThat(lobby.openGames()).isEmpty();
    }
}

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
    void categorisesByBaseTimeSeconds() {
        assertThat(LobbyEntry.categorise(15,   false)).isEqualTo("BULLET");
        assertThat(LobbyEntry.categorise(60,   false)).isEqualTo("BULLET");
        assertThat(LobbyEntry.categorise(179,  false)).isEqualTo("BULLET");
        assertThat(LobbyEntry.categorise(180,  false)).isEqualTo("BLITZ");
        assertThat(LobbyEntry.categorise(300,  false)).isEqualTo("BLITZ");
        assertThat(LobbyEntry.categorise(479,  false)).isEqualTo("BLITZ");
        assertThat(LobbyEntry.categorise(480,  false)).isEqualTo("RAPID");
        assertThat(LobbyEntry.categorise(900,  false)).isEqualTo("RAPID");
        assertThat(LobbyEntry.categorise(1499, false)).isEqualTo("RAPID");
        assertThat(LobbyEntry.categorise(1500, false)).isEqualTo("CLASSICAL");
        assertThat(LobbyEntry.categorise(3600, false)).isEqualTo("CLASSICAL");
        assertThat(LobbyEntry.categorise(60,   true )).isEqualTo("UNLIMITED");
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

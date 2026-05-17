package org.pocketchess.online.lobby;

import org.pocketchess.online.game.GameRegistry;
import org.pocketchess.online.game.GameSession;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class LobbyService {

    private final GameRegistry games;
    private final SimpMessagingTemplate messaging;

    public LobbyService(GameRegistry games, SimpMessagingTemplate messaging) {
        this.games = games;
        this.messaging = messaging;
    }

    public List<LobbyEntry> openGames() {
        return games.all().stream()
                .filter(GameSession::isOpenSeat)
                .map(LobbyService::toEntry)
                .collect(Collectors.toList());
    }

    public void broadcastUpdate() {
        messaging.convertAndSend("/topic/lobby", openGames());
    }

    private static LobbyEntry toEntry(GameSession s) {
        String creator;
        String colour;
        if (s.white() != null) {
            creator = s.white().name();
            colour = "white";
        } else {
            creator = s.black().name();
            colour = "black";
        }
        return new LobbyEntry(
                s.id(), creator, colour,
                s.timeControl().baseTimeSeconds(),
                s.timeControl().incrementSeconds(),
                s.timeControl().isUnlimited(),
                s.variant().name()
        );
    }
}

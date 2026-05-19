package org.pocketchess.online.lobby;

import org.pocketchess.online.game.GameRegistry;
import org.pocketchess.online.game.GameSession;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Comparator;
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
                .sorted(Comparator.comparingInt(LobbyService::categoryOrder)
                        .thenComparing(LobbyEntry::baseTimeSeconds))
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
        String category = LobbyEntry.categorise(
                s.timeControl().baseTimeSeconds(),
                s.timeControl().isUnlimited());
        return new LobbyEntry(
                s.id(), creator, colour,
                s.timeControl().baseTimeSeconds(),
                s.timeControl().incrementSeconds(),
                s.timeControl().isUnlimited(),
                s.variant().name(),
                category
        );
    }

    private static int categoryOrder(LobbyEntry e) {
        return switch (e.category()) {
            case "BULLET"    -> 0;
            case "BLITZ"     -> 1;
            case "RAPID"     -> 2;
            case "CLASSICAL" -> 3;
            case "UNLIMITED" -> 4;
            default          -> 5;
        };
    }
}

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

    /** Active (incl. waiting) PvP & PvE games {@code displayName} is in. */
    public List<MyGameRow> myActiveGamesFor(String displayName) {
        if (displayName == null) return List.of();
        return games.all().stream()
                .filter(s -> !s.isReview())
                .filter(s -> s.stage() != GameSession.LifecycleStage.FINISHED
                          && s.stage() != GameSession.LifecycleStage.ABORTED)
                .filter(s -> (s.white() != null && displayName.equals(s.white().name()))
                          || (s.black() != null && displayName.equals(s.black().name())))
                .map(s -> toMyGameRow(s, displayName))
                .collect(Collectors.toList());
    }

    /** Push the user's "Your games" panel snapshot to their private queue. */
    public void pushMyGamesTo(String displayName) {
        if (displayName == null) return;
        messaging.convertAndSendToUser(displayName, "/queue/my-games",
                myActiveGamesFor(displayName));
    }

    private static MyGameRow toMyGameRow(GameSession s, String me) {
        String opponent;
        if (s.white() != null && me.equals(s.white().name())) {
            opponent = s.black() == null ? "—" : s.black().name();
        } else {
            opponent = s.white() == null ? "—" : s.white().name();
        }
        String stage = switch (s.stage()) {
            case WAITING_FOR_OPPONENT -> "Waiting for opponent";
            case ACTIVE               -> "In progress";
            case FINISHED, ABORTED    -> "Finished";
        };
        return new MyGameRow(s.id(), opponent, s.variant().name(),
                s.timeControl().baseTimeSeconds(),
                s.timeControl().incrementSeconds(),
                s.timeControl().isUnlimited(),
                stage);
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
                s.timeControl().incrementSeconds(),
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
            case "ULTRABULLET" -> 0;
            case "BULLET"      -> 1;
            case "BLITZ"       -> 2;
            case "RAPID"       -> 3;
            case "CLASSICAL"   -> 4;
            case "UNLIMITED"   -> 5;
            default            -> 6;
        };
    }
}

package org.pocketchess.online.web;

import org.pocketchess.online.game.GameService;
import org.pocketchess.online.game.GameSession;
import org.pocketchess.online.lobby.LobbyEntry;
import org.pocketchess.online.lobby.LobbyService;
import org.pocketchess.online.security.CurrentUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Controller
public class LobbyController {

    private final LobbyService lobbyService;
    private final GameService gameService;

    public LobbyController(LobbyService lobbyService, GameService gameService) {
        this.lobbyService = lobbyService;
        this.gameService = gameService;
    }

    @GetMapping("/lobby")
    public String lobby(Model model, Principal principal) {
        String me = CurrentUser.displayNameOf(principal);

        List<LobbyEntry> games = new ArrayList<>(lobbyService.openGames());
        // Pin the caller's own open challenge to the top of the open-games table.
        games.sort(Comparator.comparing((LobbyEntry e) -> !e.creatorName().equals(me)));

        // Active games (incl. waiting) the user is participating in. These cover
        // the "I accidentally closed the tab, how do I get back?" case.
        List<MyGameRow> myActive = gameService.findActiveGamesFor(me).stream()
                .map(s -> toRow(s, me))
                .toList();

        model.addAttribute("openGames", games);
        model.addAttribute("myActiveGames", myActive);
        model.addAttribute("me", me);
        return "lobby";
    }

    private MyGameRow toRow(GameSession s, String me) {
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

    /** Row in the "your games" panel — kept distinct from {@link LobbyEntry}. */
    public record MyGameRow(
            String gameId,
            String opponentName,
            String variant,
            int baseTimeSeconds,
            int incrementSeconds,
            boolean unlimitedTime,
            String stage
    ) {}
}

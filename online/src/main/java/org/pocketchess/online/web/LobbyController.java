package org.pocketchess.online.web;

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

    public LobbyController(LobbyService lobbyService) {
        this.lobbyService = lobbyService;
    }

    @GetMapping("/lobby")
    public String lobby(Model model, Principal principal) {
        String me = CurrentUser.displayNameOf(principal);
        List<LobbyEntry> games = new ArrayList<>(lobbyService.openGames());
        games.sort(Comparator.comparing((LobbyEntry e) -> !e.creatorName().equals(me)));
        model.addAttribute("openGames", games);
        model.addAttribute("myActiveGames", lobbyService.myActiveGamesFor(me));
        model.addAttribute("me", me);
        return "lobby";
    }
}

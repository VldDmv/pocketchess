package org.pocketchess.online.web;

import org.pocketchess.online.lobby.LobbyService;
import org.pocketchess.online.security.CurrentUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.security.Principal;

@Controller
public class LobbyController {

    private final LobbyService lobbyService;

    public LobbyController(LobbyService lobbyService) {
        this.lobbyService = lobbyService;
    }

    @GetMapping("/lobby")
    public String lobby(Model model, Principal principal) {
        model.addAttribute("openGames", lobbyService.openGames());
        model.addAttribute("me", CurrentUser.displayNameOf(principal));
        return "lobby";
    }
}

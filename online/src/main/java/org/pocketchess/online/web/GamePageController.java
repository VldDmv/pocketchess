package org.pocketchess.online.web;

import org.pocketchess.online.game.GameService;
import org.pocketchess.online.game.GameSession;
import org.pocketchess.online.game.GameView;
import org.pocketchess.online.security.CurrentUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.security.Principal;

@Controller
public class GamePageController {

    private final GameService gameService;

    public GamePageController(GameService gameService) {
        this.gameService = gameService;
    }

    @GetMapping("/game/{id}")
    public String game(@PathVariable("id") String id, Model model, Principal principal) {
        GameSession session = gameService.find(id).orElse(null);
        if (session == null) {
            return "redirect:/lobby?missing";
        }
        String me = CurrentUser.displayNameOf(principal);
        GameView view = GameView.of(session, null, null);

        boolean meIsWhite = session.white() != null && me.equals(session.white().name());
        boolean meIsBlack = session.black() != null && me.equals(session.black().name());
        String orientation = meIsBlack ? "black" : "white";
        boolean spectator = !meIsWhite && !meIsBlack;

        model.addAttribute("gameId", id);
        model.addAttribute("me", me);
        model.addAttribute("orientation", orientation);
        model.addAttribute("spectator", spectator);
        model.addAttribute("initialView", view);
        return "game";
    }
}

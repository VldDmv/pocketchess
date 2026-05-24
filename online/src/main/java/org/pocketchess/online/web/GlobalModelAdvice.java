package org.pocketchess.online.web;

import org.pocketchess.online.repo.UserRepository;
import org.pocketchess.online.security.CurrentUser;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.security.Principal;

/**
 * Injects per-request metadata into every Thymeleaf-rendered model so the
 * layout fragment can show things like the current player's rating
 * without each controller having to plumb it through.
 */
@ControllerAdvice(basePackages = "org.pocketchess.online.web")
public class GlobalModelAdvice {

    private final UserRepository users;

    public GlobalModelAdvice(UserRepository users) {
        this.users = users;
    }

    @ModelAttribute("currentElo")
    public Integer currentElo(Principal principal) {
        String name = CurrentUser.displayNameOf(principal);
        if (name == null) return null;
        return users.findByDisplayName(name).map(u -> u.getHeadlineRating()).orElse(null);
    }
}

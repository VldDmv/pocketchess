package org.pocketchess.online.web;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class HomeController {

    private final ObjectProvider<ClientRegistrationRepository> oauthRegistry;

    public HomeController(ObjectProvider<ClientRegistrationRepository> oauthRegistry) {
        this.oauthRegistry = oauthRegistry;
    }

    @GetMapping("/")
    public String index(Authentication auth) {
        if (auth != null && auth.isAuthenticated()
                && !"anonymousUser".equals(auth.getPrincipal())) {
            return "redirect:/lobby";
        }
        return "index";
    }

    @GetMapping("/login")
    public String login(Model model,
                        @RequestParam(value = "error", required = false) String error,
                        @RequestParam(value = "registered", required = false) String registered) {
        model.addAttribute("googleEnabled", oauthRegistry.getIfAvailable() != null);
        model.addAttribute("error", error != null);
        model.addAttribute("registered", registered != null);
        return "login";
    }
}

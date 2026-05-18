package org.pocketchess.online.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.pocketchess.online.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/register")
    public String registerForm(Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new RegisterForm("", ""));
        }
        return "register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("form") RegisterForm form,
                           BindingResult binding, Model model) {
        if (binding.hasErrors()) {
            return "register";
        }
        try {
            userService.register(form.displayName(), form.password());
        } catch (IllegalArgumentException e) {
            model.addAttribute("registrationError", e.getMessage());
            return "register";
        }
        return "redirect:/login?registered";
    }

    public record RegisterForm(
            @NotBlank @Size(min = 3, max = 24)
            @Pattern(regexp = "^[A-Za-z0-9_\\-]+$",
                    message = "Letters, digits, '-' or '_' only.")
            String displayName,
            @NotBlank @Size(min = 6, max = 100) String password
    ) {}
}

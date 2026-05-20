package org.pocketchess.online.web;

import org.pocketchess.online.domain.GameRecord;
import org.pocketchess.online.domain.User;
import org.pocketchess.online.repo.GameRecordRepository;
import org.pocketchess.online.repo.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@Controller
public class ProfileController {

    private final UserRepository users;
    private final GameRecordRepository games;

    public ProfileController(UserRepository users, GameRecordRepository games) {
        this.users = users;
        this.games = games;
    }

    @GetMapping("/profile/{name}")
    public String profile(@PathVariable("name") String name, Model model) {
        User user = users.findByDisplayName(name).orElse(null);
        if (user == null) return "redirect:/lobby?missing";

        List<GameRecord> recent = games.findRecentByUser(user, PageRequest.of(0, 20));

        int wins = 0, losses = 0, draws = 0;
        for (GameRecord g : games.findRecentByUser(user, PageRequest.of(0, 500))) {
            boolean white = g.getWhite().getId().equals(user.getId());
            switch (g.getOutcome()) {
                case WHITE_WIN -> { if (white) wins++; else losses++; }
                case BLACK_WIN -> { if (white) losses++; else wins++; }
                case DRAW      -> draws++;
            }
        }

        model.addAttribute("user", user);
        model.addAttribute("recent", recent.stream().map(g -> ProfileRow.of(g, user)).toList());
        model.addAttribute("wins", wins);
        model.addAttribute("losses", losses);
        model.addAttribute("draws", draws);
        return "profile";
    }

    public record ProfileRow(
            String sessionId,
            String opponent,
            String result,       // "win" / "loss" / "draw"
            int    eloDelta,
            String terminationCode,
            String variant,
            int    baseTimeSeconds,
            int    incrementSeconds,
            boolean unlimitedTime,
            String playedAt
    ) {
        static ProfileRow of(GameRecord g, User viewer) {
            boolean asWhite = g.getWhite().getId().equals(viewer.getId());
            String opponent = asWhite ? g.getBlack().getDisplayName() : g.getWhite().getDisplayName();
            String result;
            int delta;
            switch (g.getOutcome()) {
                case DRAW -> {
                    result = "draw";
                    delta = (asWhite ? g.getWhiteEloAfter() - g.getWhiteEloBefore()
                                     : g.getBlackEloAfter() - g.getBlackEloBefore());
                }
                case WHITE_WIN -> {
                    result = asWhite ? "win" : "loss";
                    delta = asWhite ? g.getWhiteEloAfter() - g.getWhiteEloBefore()
                                    : g.getBlackEloAfter() - g.getBlackEloBefore();
                }
                case BLACK_WIN -> {
                    result = asWhite ? "loss" : "win";
                    delta = asWhite ? g.getWhiteEloAfter() - g.getWhiteEloBefore()
                                    : g.getBlackEloAfter() - g.getBlackEloBefore();
                }
                default -> { result = "?"; delta = 0; }
            }
            return new ProfileRow(
                    g.getSessionId(),
                    opponent,
                    result,
                    delta,
                    g.getTerminationCode(),
                    g.getVariant(),
                    g.getBaseTimeSeconds(),
                    g.getIncrementSeconds(),
                    g.isUnlimitedTime(),
                    g.getPlayedAt().toString()
            );
        }
    }
}

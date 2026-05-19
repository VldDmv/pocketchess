package org.pocketchess.online.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.pocketchess.core.ai.difficulty.AIDifficulty;
import org.pocketchess.core.game.model.TimeControl;
import org.pocketchess.core.gamemode.GameModeType;
import org.pocketchess.online.game.GameService;
import org.pocketchess.online.game.GameSession;
import org.pocketchess.online.lobby.LobbyService;
import org.pocketchess.online.security.CurrentUser;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/play")
public class PlayController {

    private final GameService gameService;
    private final LobbyService lobbyService;

    public PlayController(GameService gameService, LobbyService lobbyService) {
        this.gameService = gameService;
        this.lobbyService = lobbyService;
    }

    @GetMapping("/lobby")
    public List<?> lobby() {
        return lobbyService.openGames();
    }

    @GetMapping("/pgn/{gameId}")
    public ResponseEntity<String> pgn(@org.springframework.web.bind.annotation.PathVariable("gameId") String gameId) {
        return gameService.find(gameId)
                .map(s -> ResponseEntity.ok()
                        .header("Content-Type", "text/plain; charset=UTF-8")
                        .body(s.engine().pgn(
                                s.white() == null ? "?" : s.white().name(),
                                s.black() == null ? "?" : s.black().name())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/bot")
    public Map<String, String> playBot(@Valid @RequestBody BotRequest req, Principal principal) {
        String me = CurrentUser.displayNameOf(principal);
        TimeControl tc = timeControlOf(req.baseSeconds(), req.increment(), req.unlimited());
        GameModeType variant = GameModeType.valueOf(req.variant());
        AIDifficulty diff = AIDifficulty.valueOf(req.difficulty());
        boolean white = !"black".equalsIgnoreCase(req.color());
        GameSession s = gameService.createVsBot(me, white, tc, variant, diff);
        return Map.of("gameId", s.id());
    }

    @PostMapping("/open")
    public Map<String, String> createOpen(@Valid @RequestBody OpenRequest req, Principal principal) {
        String me = CurrentUser.displayNameOf(principal);
        // Don't let the same user spam multiple open challenges — cancel any
        // existing one before creating a new one.
        gameService.cancelOpenChallengesBy(me);
        TimeControl tc = timeControlOf(req.baseSeconds(), req.increment(), req.unlimited());
        GameModeType variant = GameModeType.valueOf(req.variant());
        boolean white = !"black".equalsIgnoreCase(req.color());
        GameSession s = gameService.createOpen(me, white, tc, variant);
        lobbyService.broadcastUpdate();
        return Map.of("gameId", s.id());
    }

    @PostMapping("/cancel")
    public ResponseEntity<?> cancelMyOpenChallenge(Principal principal) {
        String me = CurrentUser.displayNameOf(principal);
        gameService.cancelOpenChallengesBy(me);
        lobbyService.broadcastUpdate();
        return ResponseEntity.ok(Map.of("cancelled", true));
    }

    @PostMapping("/join")
    public ResponseEntity<?> join(@RequestParam("gameId") String gameId, Principal principal) {
        String me = CurrentUser.displayNameOf(principal);
        GameSession s = gameService.find(gameId).orElse(null);
        if (s == null) return ResponseEntity.notFound().build();
        try {
            gameService.join(s, me);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
        lobbyService.broadcastUpdate();
        return ResponseEntity.ok(Map.of("gameId", s.id()));
    }

    private TimeControl timeControlOf(Integer baseSeconds, Integer increment, Boolean unlimited) {
        if (Boolean.TRUE.equals(unlimited)) return TimeControl.UNLIMITED;
        int base = baseSeconds == null ? 300 : baseSeconds;        // default 5 min
        int inc  = increment == null ? 0 : increment;
        return new TimeControl(base, inc);
    }

    public record BotRequest(
            @NotBlank String color,
            @NotBlank String variant,
            @NotBlank String difficulty,
            @Min(10) Integer baseSeconds,
            @Min(0) Integer increment,
            Boolean unlimited
    ) {}

    public record OpenRequest(
            @NotBlank String color,
            @NotBlank String variant,
            @Min(10) Integer baseSeconds,
            @Min(0) Integer increment,
            Boolean unlimited
    ) {}
}

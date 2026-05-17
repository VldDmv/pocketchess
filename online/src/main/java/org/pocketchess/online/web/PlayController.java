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

    @PostMapping("/bot")
    public Map<String, String> playBot(@Valid @RequestBody BotRequest req, Principal principal) {
        String me = CurrentUser.displayNameOf(principal);
        TimeControl tc = timeControlOf(req.minutes(), req.increment(), req.unlimited());
        GameModeType variant = GameModeType.valueOf(req.variant());
        AIDifficulty diff = AIDifficulty.valueOf(req.difficulty());
        boolean white = !"black".equalsIgnoreCase(req.color());
        GameSession s = gameService.createVsBot(me, white, tc, variant, diff);
        return Map.of("gameId", s.id());
    }

    @PostMapping("/open")
    public Map<String, String> createOpen(@Valid @RequestBody OpenRequest req, Principal principal) {
        String me = CurrentUser.displayNameOf(principal);
        TimeControl tc = timeControlOf(req.minutes(), req.increment(), req.unlimited());
        GameModeType variant = GameModeType.valueOf(req.variant());
        boolean white = !"black".equalsIgnoreCase(req.color());
        GameSession s = gameService.createOpen(me, white, tc, variant);
        lobbyService.broadcastUpdate();
        return Map.of("gameId", s.id());
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

    @PostMapping("/quick")
    public Map<String, String> quick(@Valid @RequestBody OpenRequest req, Principal principal) {
        String me = CurrentUser.displayNameOf(principal);
        // FIFO: pick the oldest open game matching the requested variant; otherwise create a new one.
        GameSession match = lobbyService.openGames().stream()
                .filter(e -> e.variant().equalsIgnoreCase(req.variant()))
                .map(e -> gameService.find(e.gameId()).orElse(null))
                .filter(s -> s != null && s.isOpenSeat()
                        && !(s.white() != null && me.equals(s.white().name()))
                        && !(s.black() != null && me.equals(s.black().name())))
                .findFirst().orElse(null);
        if (match != null) {
            gameService.join(match, me);
            lobbyService.broadcastUpdate();
            return Map.of("gameId", match.id());
        }
        TimeControl tc = timeControlOf(req.minutes(), req.increment(), req.unlimited());
        GameModeType variant = GameModeType.valueOf(req.variant());
        GameSession s = gameService.createOpen(me, true, tc, variant);
        lobbyService.broadcastUpdate();
        return Map.of("gameId", s.id());
    }

    private TimeControl timeControlOf(Integer minutes, Integer increment, Boolean unlimited) {
        if (Boolean.TRUE.equals(unlimited)) return TimeControl.UNLIMITED;
        int m = minutes == null ? 5 : minutes;
        int inc = increment == null ? 0 : increment;
        return new TimeControl(m * 60, inc);
    }

    public record BotRequest(
            @NotBlank String color,
            @NotBlank String variant,
            @NotBlank String difficulty,
            @Min(1) Integer minutes,
            @Min(0) Integer increment,
            Boolean unlimited
    ) {}

    public record OpenRequest(
            @NotBlank String color,
            @NotBlank String variant,
            @Min(1) Integer minutes,
            @Min(0) Integer increment,
            Boolean unlimited
    ) {}
}

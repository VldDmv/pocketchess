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
    public ResponseEntity<String> pgn(@org.springframework.web.bind.annotation.PathVariable String gameId) {
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

        // If the caller already has an open challenge, reuse it — clicking
        // "Quick match" twice shouldn't create duplicates.
        GameSession mine = gameService.findOpenChallengeBy(me).orElse(null);
        if (mine != null) {
            return Map.of("gameId", mine.id());
        }

        // FIFO: pick the oldest open game matching the requested variant.
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
        TimeControl tc = timeControlOf(req.baseSeconds(), req.increment(), req.unlimited());
        GameModeType variant = GameModeType.valueOf(req.variant());
        GameSession s = gameService.createOpen(me, true, tc, variant);
        lobbyService.broadcastUpdate();
        return Map.of("gameId", s.id());
    }

    /**
     * Starts a new game with the same time control / variant as the finished
     * game. PvE rematches keep the bot's difficulty; the human switches
     * colours. PvP rematches require the OTHER player's confirmation, but
     * for the MVP this endpoint just spins up a fresh open challenge.
     */
    @PostMapping("/rematch/{gameId}")
    public ResponseEntity<?> rematch(@org.springframework.web.bind.annotation.PathVariable String gameId,
                                     Principal principal) {
        String me = CurrentUser.displayNameOf(principal);
        GameSession finished = gameService.find(gameId).orElse(null);
        if (finished == null) return ResponseEntity.notFound().build();

        boolean meWasWhite = finished.white() != null && me.equals(finished.white().name());
        boolean meWasBlack = finished.black() != null && me.equals(finished.black().name());
        if (!meWasWhite && !meWasBlack) {
            return ResponseEntity.badRequest().body(Map.of("error", "You weren't in that game."));
        }

        boolean botWasOpponent = (meWasWhite && finished.black() != null && finished.black().bot())
                              || (meWasBlack && finished.white() != null && finished.white().bot());

        if (botWasOpponent) {
            // Flip colours for variety.
            GameSession s = gameService.createVsBot(
                    me, !meWasWhite,
                    finished.timeControl(), finished.variant(),
                    finished.aiDifficulty());
            return ResponseEntity.ok(Map.of("gameId", s.id()));
        }

        // PvP rematch — open a fresh challenge with same TC/variant; opponent
        // can pick it up from the lobby. Cancel any existing open one first.
        gameService.cancelOpenChallengesBy(me);
        GameSession s = gameService.createOpen(me, !meWasWhite,
                finished.timeControl(), finished.variant());
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

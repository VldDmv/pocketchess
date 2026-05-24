package org.pocketchess.online.service;

import org.pocketchess.core.game.model.GameStatus;
import org.pocketchess.online.domain.GameRecord;
import org.pocketchess.online.domain.User;
import org.pocketchess.online.game.GameSession;
import org.pocketchess.online.repo.GameRecordRepository;
import org.pocketchess.online.repo.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Persists finished rated games and updates each player's Elo rating.
 * Hooked into {@link org.pocketchess.online.game.GameService} at every
 * point that flips a session to {@code FINISHED}.
 *
 * <p>Skipped scenarios: PvE (bot in either seat), aborted games, and
 * non-terminal {@code GameStatus} values.
 */
@Service
public class GameHistoryService {

    private static final Logger log = LoggerFactory.getLogger(GameHistoryService.class);

    private final GameRecordRepository records;
    private final UserRepository users;
    private final EloService elo;

    public GameHistoryService(GameRecordRepository records, UserRepository users, EloService elo) {
        this.records = records;
        this.users = users;
        this.elo = elo;
    }

    @Transactional
    public void recordTerminal(GameSession session) {
        if (session.isReview()) return;                                  // PGN review
        if (session.white() == null || session.black() == null) return;
        if (session.white().bot() || session.black().bot()) return;     // not rated

        Double whiteScore = whiteScoreFor(session.engine().status());
        if (whiteScore == null) return;
        if (records.existsBySessionId(session.id())) return;             // idempotent

        User white = users.findByDisplayName(session.white().name()).orElse(null);
        User black = users.findByDisplayName(session.black().name()).orElse(null);
        if (white == null || black == null) {
            log.warn("Skipping persistence for session {} — player not found in DB", session.id());
            return;
        }

        String category = RatingCategory.of(session.variant(), session.timeControl());
        int whiteBefore = white.getRating(category);
        int blackBefore = black.getRating(category);
        int[] after = elo.apply(whiteBefore, blackBefore, whiteScore);
        // Berserk bonus: +1 if the berserker won outright. Matches lichess.
        if (session.whiteBerserked() && whiteScore == 1.0) after[0] += 1;
        if (session.blackBerserked() && whiteScore == 0.0) after[1] += 1;
        white.setRating(category, after[0]);
        black.setRating(category, after[1]);

        GameRecord r = new GameRecord();
        r.setSessionId(session.id());
        r.setWhite(white);
        r.setBlack(black);
        r.setOutcome(outcomeFor(whiteScore));
        r.setTerminationCode(session.engine().status().name());
        r.setBaseTimeSeconds(session.timeControl().baseTimeSeconds());
        r.setIncrementSeconds(session.timeControl().incrementSeconds());
        r.setUnlimitedTime(session.timeControl().isUnlimited());
        r.setVariant(session.variant().name());
        r.setCategory(category);
        r.setWhiteEloBefore(whiteBefore);
        r.setBlackEloBefore(blackBefore);
        r.setWhiteEloAfter(after[0]);
        r.setBlackEloAfter(after[1]);
        r.setPgn(session.engine().pgn(white.getDisplayName(), black.getDisplayName()));
        r.setPlayedAt(Instant.now());
        records.save(r);

        users.save(white);
        users.save(black);

        log.info("Rated game {} → outcome={} elo({} {} → {}) vs ({} {} → {})",
                session.id(), r.getOutcome(),
                white.getDisplayName(), whiteBefore, after[0],
                black.getDisplayName(), blackBefore, after[1]);
    }

    private static Double whiteScoreFor(GameStatus status) {
        return switch (status) {
            case WHITE_WIN, WHITE_WIN_ON_TIME, WHITE_WINS_BY_RESIGNATION -> 1.0;
            case BLACK_WIN, BLACK_WIN_ON_TIME, BLACK_WINS_BY_RESIGNATION -> 0.0;
            case STALEMATE, DRAW_AGREED, DRAW_THREEFOLD_REPETITION,
                 DRAW_50_MOVES, DRAW_INSUFFICIENT_MATERIAL              -> 0.5;
            default -> null;
        };
    }

    private static GameRecord.Outcome outcomeFor(double whiteScore) {
        if (whiteScore == 1.0) return GameRecord.Outcome.WHITE_WIN;
        if (whiteScore == 0.0) return GameRecord.Outcome.BLACK_WIN;
        return GameRecord.Outcome.DRAW;
    }
}

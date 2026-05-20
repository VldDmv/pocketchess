package org.pocketchess.online.service;

import org.springframework.stereotype.Service;

/**
 * Plain Elo rating math, K=32 for everyone. The expected score is
 * 1 / (1 + 10^((opponent − you) / 400)); the new rating is
 * old + K · (actual − expected). No K-factor floors or rating-class
 * bonuses — we want predictable behaviour from a small player base.
 */
@Service
public class EloService {

    public static final int K_FACTOR = 32;
    public static final int DEFAULT_RATING = 1200;

    public double expectedScore(int rating, int opponentRating) {
        return 1.0 / (1.0 + Math.pow(10.0, (opponentRating - rating) / 400.0));
    }

    public int updatedRating(int rating, int opponentRating, double actualScore) {
        double expected = expectedScore(rating, opponentRating);
        return (int) Math.round(rating + K_FACTOR * (actualScore - expected));
    }

    /** Convenience — returns {@code [whiteAfter, blackAfter]} for a finished game. */
    public int[] apply(int whiteRating, int blackRating, double whiteScore) {
        double blackScore = 1.0 - whiteScore;
        int newWhite = updatedRating(whiteRating, blackRating, whiteScore);
        int newBlack = updatedRating(blackRating, whiteRating, blackScore);
        return new int[]{ newWhite, newBlack };
    }
}

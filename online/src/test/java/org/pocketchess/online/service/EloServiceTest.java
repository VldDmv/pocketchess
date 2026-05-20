package org.pocketchess.online.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class EloServiceTest {

    private final EloService elo = new EloService();

    @Test
    void equalRatingsProduceAFifteenPointSwing() {
        // K=32, expected = 0.5, actual = 1.0 → +16; loser → −16.
        int[] r = elo.apply(1200, 1200, 1.0);
        assertThat(r[0]).isEqualTo(1216);
        assertThat(r[1]).isEqualTo(1184);
    }

    @Test
    void drawBetweenEqualsLeavesEveryoneUnchanged() {
        int[] r = elo.apply(1500, 1500, 0.5);
        assertThat(r[0]).isEqualTo(1500);
        assertThat(r[1]).isEqualTo(1500);
    }

    @Test
    void upsetTakesMoreFromTheFavourite() {
        // Strong white (1700) loses to weak black (1300).
        int[] r = elo.apply(1700, 1300, 0.0);
        // Expected white score ≈ 0.909; actual 0 → big swing.
        assertThat(r[0]).isLessThan(1700);
        assertThat(r[1]).isGreaterThan(1300);
        assertThat((1700 - r[0])).isEqualTo(r[1] - 1300)
                .as("zero-sum: the points white loses equal the points black wins");
    }

    @Test
    void expectedScoreCornersAreReasonable() {
        assertThat(elo.expectedScore(2000, 1000)).isCloseTo(0.997, within(0.005));
        assertThat(elo.expectedScore(1000, 2000)).isCloseTo(0.003, within(0.005));
        assertThat(elo.expectedScore(1500, 1500)).isCloseTo(0.5,   within(0.0001));
    }

    @Test
    void favouriteWinningGainsLittle() {
        int[] r = elo.apply(1800, 1200, 1.0);
        // Big rating gap, favourite wins → small gain.
        assertThat(r[0] - 1800).isLessThan(5);
        assertThat(1200 - r[1]).isLessThan(5);
    }
}

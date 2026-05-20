package org.pocketchess.online.engine;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UciMoveTest {

    @Test
    void parsesPlainMove() {
        UciMove m = UciMove.parse("e2e4");
        assertThat(m.fromCol()).isEqualTo(4);     // 'e'
        assertThat(m.fromRow()).isEqualTo(6);     // row index for rank 2
        assertThat(m.toCol()).isEqualTo(4);
        assertThat(m.toRow()).isEqualTo(4);
        assertThat(m.promotion()).isNull();
    }

    @Test
    void parsesPromotionSuffix() {
        UciMove m = UciMove.parse("e7e8q");
        assertThat(m.fromCol()).isEqualTo(4);
        assertThat(m.fromRow()).isEqualTo(1);     // rank 7 → row 1
        assertThat(m.toRow()).isEqualTo(0);       // rank 8 → row 0
        assertThat(m.promotion()).isEqualTo('q');

        assertThat(UciMove.parse("a2a1n").promotion()).isEqualTo('n');
        assertThat(UciMove.parse("h7h8r").promotion()).isEqualTo('r');
        assertThat(UciMove.parse("d7d8b").promotion()).isEqualTo('b');
    }

    @Test
    void rejectsBadInput() {
        assertThatThrownBy(() -> UciMove.parse(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UciMove.parse("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UciMove.parse("e2")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UciMove.parse("z1z2")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UciMove.parse("e2e9")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UciMove.parse("e2e4x")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void promotionCharIsCaseInsensitive() {
        assertThat(UciMove.parse("e7e8Q").promotion()).isEqualTo('q');
        assertThat(UciMove.parse("e7e8N").promotion()).isEqualTo('n');
    }
}

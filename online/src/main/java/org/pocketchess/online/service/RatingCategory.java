package org.pocketchess.online.service;

import org.pocketchess.core.game.model.TimeControl;
import org.pocketchess.core.gamemode.GameModeType;
import org.pocketchess.online.lobby.LobbyEntry;

/**
 * Maps a (variant, time control) pair to the rating bucket it counts toward.
 * Variants get their own pooled rating; standard chess is split by time
 * control, lichess-style.
 */
public final class RatingCategory {

    private RatingCategory() {}

    public static String of(GameModeType variant, TimeControl tc) {
        if (variant == GameModeType.CHESS960) return "CHESS960";
        if (variant == GameModeType.LAVA)     return "LAVA";
        return LobbyEntry.categorise(
                tc.baseTimeSeconds(), tc.incrementSeconds(), tc.isUnlimited());
    }

    /** All buckets, in display order, for the profile page. */
    public static java.util.List<String> all() {
        return java.util.List.of(
                "ULTRABULLET", "BULLET", "BLITZ", "RAPID", "CLASSICAL",
                "UNLIMITED", "CHESS960", "LAVA");
    }
}

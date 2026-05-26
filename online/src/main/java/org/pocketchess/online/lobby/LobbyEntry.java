package org.pocketchess.online.lobby;

/** A single row in the lobby's open-games table. */
public record LobbyEntry(
        String gameId,
        String creatorName,
        String creatorColour,    // "white" or "black"
        int baseTimeSeconds,
        int incrementSeconds,
        boolean unlimitedTime,
        String variant,
        String category          // ULTRABULLET, BULLET, BLITZ, RAPID, CLASSICAL, UNLIMITED
) {

    /**
     * Time-control bucketing by "estimated duration"
     * {@code base + 40 * increment} (40 ≈ moves in an average chess game).
     * Brackets:
     * <ul>
     *   <li>&lt; 30 s  — UltraBullet</li>
     *   <li>&lt; 3 min  — Bullet</li>
     *   <li>&lt; 8 min  — Blitz</li>
     *   <li>&lt; 25 min — Rapid</li>
     *   <li>≥ 25 min   — Classical</li>
     *   <li>no clock   — Unlimited</li>
     * </ul>
     */
    public static String categorise(int baseSeconds, int incrementSeconds, boolean unlimited) {
        if (unlimited) return "UNLIMITED";
        long estimated = (long) baseSeconds + 40L * Math.max(0, incrementSeconds);
        if (estimated < 30)   return "ULTRABULLET";
        if (estimated < 180)  return "BULLET";
        if (estimated < 480)  return "BLITZ";
        if (estimated < 1500) return "RAPID";
        return "CLASSICAL";
    }
}

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
        String category          // BULLET, BLITZ, RAPID, CLASSICAL, UNLIMITED
) {

    /**
     * Buckets a time control into the lichess-style category. Bucketing is
     * by base time alone; we treat unlimited as its own bucket so it sorts
     * to the end.
     */
    public static String categorise(int baseSeconds, boolean unlimited) {
        if (unlimited) return "UNLIMITED";
        if (baseSeconds < 180)  return "BULLET";    // < 3 min
        if (baseSeconds < 480)  return "BLITZ";     // 3–8 min
        if (baseSeconds < 1500) return "RAPID";     // 8–25 min
        return "CLASSICAL";
    }
}

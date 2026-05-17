package org.pocketchess.online.lobby;

/** A single row in the lobby's open-games table. */
public record LobbyEntry(
        String gameId,
        String creatorName,
        String creatorColour,    // "white", "black"
        int baseTimeSeconds,
        int incrementSeconds,
        boolean unlimitedTime,
        String variant
) {}

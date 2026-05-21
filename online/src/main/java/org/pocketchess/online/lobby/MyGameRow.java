package org.pocketchess.online.lobby;

/** Row in the "your games" panel — distinct from {@link LobbyEntry}. */
public record MyGameRow(
        String gameId,
        String opponentName,
        String variant,
        int baseTimeSeconds,
        int incrementSeconds,
        boolean unlimitedTime,
        String stage           // human-friendly text: "Waiting for opponent" / "In progress"
) {}

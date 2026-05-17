package org.pocketchess.online.game;

/** Wire DTOs used by the STOMP controllers. */
public final class Messages {
    private Messages() {}

    public record MoveRequest(String uci) {}
    public record ChatRequest(String text) {}
    public record ChatNotice(String from, String text, long timestamp) {}
    public record ErrorNotice(String reason) {}
}

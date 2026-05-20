package org.pocketchess.online.game;

import org.pocketchess.online.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tracks how many live WebSocket sessions each user has. When a user's
 * session count drops to zero, the {@link GameService} is notified so it
 * can start the disconnect grace timer; when the user reconnects (count
 * climbs back from zero), the service cancels the pending forfeit.
 *
 * <p>Each browser tab opens its own STOMP session, so the count handles
 * multi-tab use correctly.
 */
@Component
public class PresenceTracker {

    private static final Logger log = LoggerFactory.getLogger(PresenceTracker.class);

    private final GameService gameService;
    private final ConcurrentMap<String, AtomicInteger> liveSessions = new ConcurrentHashMap<>();

    public PresenceTracker(GameService gameService) {
        this.gameService = gameService;
    }

    @EventListener
    public void onConnect(SessionConnectedEvent e) {
        String name = CurrentUser.displayNameOf(e.getUser());
        if (name == null) return;
        int after = liveSessions.computeIfAbsent(name, k -> new AtomicInteger())
                .incrementAndGet();
        if (after == 1) {
            log.debug("{} came online", name);
            gameService.onPlayerReconnected(name);
        }
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent e) {
        String name = CurrentUser.displayNameOf(e.getUser());
        if (name == null) return;
        AtomicInteger counter = liveSessions.get(name);
        if (counter == null) return;
        int after = counter.decrementAndGet();
        if (after <= 0) {
            liveSessions.remove(name, counter);
            log.debug("{} went offline", name);
            gameService.onPlayerDisconnected(name);
        }
    }
}

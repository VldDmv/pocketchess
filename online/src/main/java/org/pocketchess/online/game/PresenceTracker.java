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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks live WebSocket sessions per user and notifies the
 * {@link GameService} when a player actually goes offline.
 *
 * <p><b>Why debounce?</b> Navigating between {@code /lobby} and
 * {@code /game/{id}} kills the page's SockJS connection and the next
 * page opens a fresh one. There's a 50–500 ms gap where the session
 * count drops to zero even though the user is still on the site. Without
 * debounce that would flap "Opponent disconnected — 120s to reconnect"
 * at the opponent every time someone clicked a link. The grace window
 * we wait before firing {@code onPlayerDisconnected} smooths that out.
 */
@Component
public class PresenceTracker {

    /** How long zero-session state has to persist before we tell the game service. */
    public static final long OFFLINE_DEBOUNCE_MS = 5_000;

    private static final Logger log = LoggerFactory.getLogger(PresenceTracker.class);

    private final GameService gameService;
    private final ScheduledExecutorService scheduler;
    private final ConcurrentMap<String, Presence> users = new ConcurrentHashMap<>();

    public PresenceTracker(GameService gameService) {
        this.gameService = gameService;
        this.scheduler = Executors.newScheduledThreadPool(1, namedDaemon());
    }

    @EventListener
    public void onConnect(SessionConnectedEvent e) {
        String name = CurrentUser.displayNameOf(e.getUser());
        if (name == null) return;
        Presence p = users.computeIfAbsent(name, k -> new Presence());
        boolean wasOffline;
        synchronized (p) {
            p.sessions++;
            if (p.pending != null) {
                p.pending.cancel(false);
                p.pending = null;
            }
            wasOffline = p.offline;
            p.offline = false;
        }
        if (wasOffline) {
            log.debug("{} reconnected (debounced)", name);
            gameService.onPlayerReconnected(name);
        }
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent e) {
        String name = CurrentUser.displayNameOf(e.getUser());
        if (name == null) return;
        Presence p = users.get(name);
        if (p == null) return;
        synchronized (p) {
            p.sessions = Math.max(0, p.sessions - 1);
            if (p.sessions == 0 && !p.offline && p.pending == null) {
                // Defer the "actually offline" callback — the user might be
                // navigating between pages of the same SPA.
                p.pending = scheduler.schedule(() -> markOffline(name),
                        OFFLINE_DEBOUNCE_MS, TimeUnit.MILLISECONDS);
            }
        }
    }

    /** Test seam — bypasses the 5-second wait. */
    void markOfflineNow(String name) {
        markOffline(name);
    }

    private void markOffline(String name) {
        Presence p = users.get(name);
        if (p == null) return;
        boolean fire = false;
        synchronized (p) {
            if (p.sessions > 0) return;       // they came back
            if (p.offline)     return;
            p.offline = true;
            p.pending = null;
            fire = true;
        }
        if (fire) {
            log.debug("{} stayed offline past debounce window", name);
            gameService.onPlayerDisconnected(name);
        }
    }

    private static ThreadFactory namedDaemon() {
        final AtomicLong n = new AtomicLong();
        return r -> {
            Thread t = new Thread(r, "pc-presence-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }

    private static class Presence {
        int sessions;
        boolean offline;
        ScheduledFuture<?> pending;
    }
}

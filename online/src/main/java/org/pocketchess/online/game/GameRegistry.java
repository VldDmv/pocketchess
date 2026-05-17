package org.pocketchess.online.game;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** In-memory registry of active game sessions. */
@Component
public class GameRegistry {

    private final ConcurrentMap<String, GameSession> sessions = new ConcurrentHashMap<>();

    public Optional<GameSession> find(String id) {
        return Optional.ofNullable(sessions.get(id));
    }

    public void put(GameSession session) {
        sessions.put(session.id(), session);
    }

    public void remove(String id) {
        sessions.remove(id);
    }

    public Collection<GameSession> all() {
        return Collections.unmodifiableCollection(sessions.values());
    }
}

package org.pocketchess.online.service;

import org.pocketchess.online.domain.User;
import org.pocketchess.online.repo.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class UserService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User register(String displayName, String rawPassword) {
        String normalisedName = displayName.trim();
        if (users.existsByDisplayName(normalisedName)) {
            throw new IllegalArgumentException("Display name is already taken.");
        }
        User u = new User();
        u.setDisplayName(normalisedName);
        u.setPasswordHash(passwordEncoder.encode(rawPassword));
        return users.save(u);
    }

    /**
     * Looks up an existing Google account by {@code sub}, creating one if
     * absent. Display name defaults to the Google profile name; a numeric
     * suffix is appended on collision.
     */
    @Transactional
    public User upsertGoogleUser(String googleSub, String preferredName) {
        Optional<User> existing = users.findByGoogleSub(googleSub);
        if (existing.isPresent()) return existing.get();

        User u = new User();
        u.setDisplayName(uniqueDisplayName(preferredName == null ? "player" : preferredName));
        u.setGoogleSub(googleSub);
        return users.save(u);
    }

    public Optional<User> findByDisplayName(String name) {
        return users.findByDisplayName(name);
    }

    private String uniqueDisplayName(String preferred) {
        String base = preferred.trim();
        if (base.isEmpty()) base = "player";
        String candidate = base;
        int suffix = 2;
        while (users.existsByDisplayName(candidate)) {
            candidate = base + suffix++;
        }
        return candidate;
    }
}

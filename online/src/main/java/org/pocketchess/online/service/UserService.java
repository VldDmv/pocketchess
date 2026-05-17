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
    public User register(String email, String displayName, String rawPassword) {
        String normalisedEmail = email.trim().toLowerCase();
        String normalisedName = displayName.trim();
        if (users.existsByEmail(normalisedEmail)) {
            throw new IllegalArgumentException("Email is already in use.");
        }
        if (users.existsByDisplayName(normalisedName)) {
            throw new IllegalArgumentException("Display name is already taken.");
        }
        User u = new User();
        u.setEmail(normalisedEmail);
        u.setDisplayName(normalisedName);
        u.setPasswordHash(passwordEncoder.encode(rawPassword));
        return users.save(u);
    }

    @Transactional
    public User upsertGoogleUser(String googleSub, String email, String name) {
        Optional<User> existing = users.findByGoogleSub(googleSub);
        if (existing.isPresent()) return existing.get();

        // Link by email if a local account already uses it.
        Optional<User> byEmail = users.findByEmail(email.toLowerCase());
        if (byEmail.isPresent()) {
            User u = byEmail.get();
            u.setGoogleSub(googleSub);
            return u;
        }

        User u = new User();
        u.setEmail(email.toLowerCase());
        u.setDisplayName(uniqueDisplayName(name == null ? email.split("@")[0] : name));
        u.setGoogleSub(googleSub);
        return users.save(u);
    }

    public Optional<User> findByEmail(String email) {
        return users.findByEmail(email.trim().toLowerCase());
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

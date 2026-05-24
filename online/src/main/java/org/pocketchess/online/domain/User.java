package org.pocketchess.online.domain;

import jakarta.persistence.Column;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "users",
        uniqueConstraints = @UniqueConstraint(columnNames = "displayName"))
public class User {

    public static final int DEFAULT_RATING = 1200;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String displayName;

    /** Null for users created via OAuth only. */
    @Column
    private String passwordHash;

    /** Google's "sub" claim, when the user signed in with Google. */
    @Column
    private String googleSub;

    /**
     * Per-category Elo. Keys are rating categories — ULTRABULLET, BULLET,
     * BLITZ, RAPID, CLASSICAL, UNLIMITED (for standard chess by time) plus
     * CHESS960 and LAVA for the variants. Missing keys default to 1200, so
     * losing a lava game never touches the blitz rating.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_ratings", joinColumns = @JoinColumn(name = "user_id"))
    @MapKeyColumn(name = "category")
    @Column(name = "rating")
    private Map<String, Integer> ratings = new HashMap<>();

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getGoogleSub() { return googleSub; }
    public void setGoogleSub(String googleSub) { this.googleSub = googleSub; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Map<String, Integer> getRatings() { return ratings; }

    public int getRating(String category) {
        return ratings.getOrDefault(category, DEFAULT_RATING);
    }

    public void setRating(String category, int value) {
        ratings.put(category, value);
    }

    /** Highest rating across categories (for a headline number); default if unrated. */
    public int getHeadlineRating() {
        return ratings.values().stream().max(Integer::compareTo).orElse(DEFAULT_RATING);
    }
}

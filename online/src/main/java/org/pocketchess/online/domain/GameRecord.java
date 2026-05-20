package org.pocketchess.online.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One finished rated PvP game. Bot games and aborted sessions are NOT
 * persisted — they don't move ratings and don't show up in player
 * profiles.
 */
@Entity
@Table(name = "game_records")
public class GameRecord {

    public enum Outcome { WHITE_WIN, BLACK_WIN, DRAW }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Original in-memory session id — useful for cross-referencing logs. */
    @Column(nullable = false, unique = true)
    private String sessionId;

    @ManyToOne(optional = false) @JoinColumn(name = "white_user_id")
    private User white;

    @ManyToOne(optional = false) @JoinColumn(name = "black_user_id")
    private User black;

    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private Outcome outcome;

    /** Final on-engine status string (CHECKMATE / RESIGNATION / TIME / etc.). */
    @Column(nullable = false)
    private String terminationCode;

    @Column(nullable = false) private int baseTimeSeconds;
    @Column(nullable = false) private int incrementSeconds;
    @Column(nullable = false) private boolean unlimitedTime;
    @Column(nullable = false) private String variant;

    @Column(nullable = false) private int whiteEloBefore;
    @Column(nullable = false) private int blackEloBefore;
    @Column(nullable = false) private int whiteEloAfter;
    @Column(nullable = false) private int blackEloAfter;

    @Column(nullable = false, length = 4096)
    private String pgn;

    @Column(nullable = false)
    private Instant playedAt = Instant.now();

    // ─── boilerplate ──────────────────────────────────────────────────────

    public Long getId() { return id; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public User getWhite() { return white; }
    public void setWhite(User white) { this.white = white; }
    public User getBlack() { return black; }
    public void setBlack(User black) { this.black = black; }
    public Outcome getOutcome() { return outcome; }
    public void setOutcome(Outcome outcome) { this.outcome = outcome; }
    public String getTerminationCode() { return terminationCode; }
    public void setTerminationCode(String terminationCode) { this.terminationCode = terminationCode; }
    public int getBaseTimeSeconds() { return baseTimeSeconds; }
    public void setBaseTimeSeconds(int baseTimeSeconds) { this.baseTimeSeconds = baseTimeSeconds; }
    public int getIncrementSeconds() { return incrementSeconds; }
    public void setIncrementSeconds(int incrementSeconds) { this.incrementSeconds = incrementSeconds; }
    public boolean isUnlimitedTime() { return unlimitedTime; }
    public void setUnlimitedTime(boolean unlimitedTime) { this.unlimitedTime = unlimitedTime; }
    public String getVariant() { return variant; }
    public void setVariant(String variant) { this.variant = variant; }
    public int getWhiteEloBefore() { return whiteEloBefore; }
    public void setWhiteEloBefore(int whiteEloBefore) { this.whiteEloBefore = whiteEloBefore; }
    public int getBlackEloBefore() { return blackEloBefore; }
    public void setBlackEloBefore(int blackEloBefore) { this.blackEloBefore = blackEloBefore; }
    public int getWhiteEloAfter() { return whiteEloAfter; }
    public void setWhiteEloAfter(int whiteEloAfter) { this.whiteEloAfter = whiteEloAfter; }
    public int getBlackEloAfter() { return blackEloAfter; }
    public void setBlackEloAfter(int blackEloAfter) { this.blackEloAfter = blackEloAfter; }
    public String getPgn() { return pgn; }
    public void setPgn(String pgn) { this.pgn = pgn; }
    public Instant getPlayedAt() { return playedAt; }
    public void setPlayedAt(Instant playedAt) { this.playedAt = playedAt; }
}

package com.cotizaia.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

/**
 * Single-use link that proves an account owner controls the mailbox they registered with (spec 16).
 *
 * <p>The mailed token never reaches the database: only its SHA-256 hash lives here, the same
 * rule the refresh tokens follow, so a backup leak cannot be replayed as a verification link.
 * {@code usedAt} is the single-use mark and {@code expiresAt} bounds the window to 24 hours.</p>
 */
@Entity
@Table(name = "email_verification_tokens", uniqueConstraints = @UniqueConstraint(
        name = "uq_email_verification_tokens_token_hash", columnNames = "token_hash"))
public class EmailVerificationToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    protected EmailVerificationToken() {
    }

    public EmailVerificationToken(AppUser user, String tokenHash, Instant expiresAt) {
        if (user == null || tokenHash == null || tokenHash.isBlank() || expiresAt == null) {
            throw new IllegalArgumentException("User, token hash and expiry are required");
        }
        this.user = user;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public Long getId() {
        return id;
    }

    public AppUser getUser() {
        return user;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    /** True only for a token that can still be redeemed: unused and inside its window. */
    public boolean isRedeemable(Instant now) {
        return !isUsed() && !isExpired(now);
    }
}

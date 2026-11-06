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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;

/**
 * One link in a refresh-token rotation chain (spec 02). The raw token lives
 * only in the HttpOnly refresh cookie: the database stores its SHA-256 hash,
 * so presentation evidence never becomes replay material. {@link #familyId}
 * groups the chain started by a single login; reuse of a rotated token kills
 * the whole family.
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    private static final SecureRandom RANDOM = new SecureRandom();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "family_id", nullable = false, length = 36)
    private String familyId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "replaced_by")
    private Long replacedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RefreshToken() {
    }

    public RefreshToken(AppUser user, String familyId, String token, Instant expiresAt, Instant createdAt) {
        if (user == null || familyId == null || familyId.isBlank() || token == null
                || token.isBlank() || expiresAt == null || createdAt == null) {
            throw new IllegalArgumentException("valid refresh token fields are required");
        }
        this.user = user;
        this.familyId = familyId;
        this.tokenHash = hash(token);
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    /** 32 random bytes, base64url without padding: cookie- and URL-safe. */
    public static String newTokenValue() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Presentation hash: SHA-256 as 64 lowercase hex characters, never the raw token. */
    public static String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public void revoke(Instant revokedAt, Long replacedBy) {
        this.revokedAt = revokedAt;
        this.replacedBy = replacedBy;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired(Instant now) {
        return expiresAt.isBefore(now);
    }

    public Long getId() {
        return id;
    }

    public AppUser getUser() {
        return user;
    }

    public String getFamilyId() {
        return familyId;
    }
}

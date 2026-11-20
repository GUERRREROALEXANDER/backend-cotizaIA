package com.cotizaia.domain;

import com.cotizaia.crypto.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;

/**
 * One row of the WhatsApp outbox. The backend writes it in the transaction of the event that caused it, and the
 * separate WhatsApp service claims it over HTTP and reports the outcome. The lease rules live here so the claim
 * query only has to load pending rows.
 */
@Entity
@Table(name = "whatsapp_messages")
public class WhatsappMessage {

    /** A claimed row with no outcome after this long is offered again: the service may have died mid-send. */
    public static final Duration LEASE = Duration.ofSeconds(60);

    public static final int MAX_ATTEMPTS = 3;

    public enum Status { PENDING, SENT, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(nullable = false, columnDefinition = "TEXT")
    private String phone;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(length = 200)
    private String error;

    protected WhatsappMessage() {
    }

    public WhatsappMessage(String phone, String body, Instant createdAt, Instant expiresAt) {
        if (phone == null || phone.isBlank() || body == null || body.isBlank() || createdAt == null
                || expiresAt == null || !expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("phone, body and a future expiry are required");
        }
        this.phone = phone;
        this.body = body;
        this.status = Status.PENDING;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    /**
     * Leases this pending row to the caller when it may be sent now. An expired row, or one that used up its
     * attempts, is closed as FAILED instead and never handed out.
     */
    public boolean take(Instant now) {
        if (status != Status.PENDING) {
            return false;
        }
        if (!expiresAt.isAfter(now)) {
            close(Status.FAILED, "expired", now);
            return false;
        }
        if (claimedAt != null && Duration.between(claimedAt, now).compareTo(LEASE) < 0) {
            return false;
        }
        if (attempts >= MAX_ATTEMPTS) {
            close(Status.FAILED, "too many attempts", now);
            return false;
        }
        claimedAt = now;
        attempts++;
        return true;
    }

    /** Records the outcome once; reporting a finished row again changes nothing. */
    public void finish(String failure, Instant now) {
        if (status == Status.PENDING) {
            close(failure == null ? Status.SENT : Status.FAILED, failure, now);
        }
    }

    private void close(Status outcome, String failure, Instant now) {
        status = outcome;
        error = failure;
        finishedAt = now;
        // The body may be a verification code, and whatever it announced is stored elsewhere.
        body = "-";
    }

    public Long getId() {
        return id;
    }

    public String getPhone() {
        return phone;
    }

    public String getBody() {
        return body;
    }

    public Status getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getClaimedAt() {
        return claimedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public String getError() {
        return error;
    }
}

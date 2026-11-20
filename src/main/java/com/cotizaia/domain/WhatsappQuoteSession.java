package com.cotizaia.domain;

import com.cotizaia.crypto.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;

/**
 * Where a user is in the WhatsApp chat that builds a quotation. Stored, not kept in memory, so a restart of the
 * backend does not lose a half-written quotation. A chat left alone for {@link #IDLE_TIMEOUT} starts over.
 */
@Entity
@Table(name = "whatsapp_quote_sessions")
public class WhatsappQuoteSession {

    public static final Duration IDLE_TIMEOUT = Duration.ofHours(2);

    /** The chat's steps, in order; QUESTION repeats while the agent needs clarifications. */
    public enum Step { IDLE, CLIENT_NAME, CLIENT_EMAIL, DESCRIPTION, QUESTION }

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Step step;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "client_name", columnDefinition = "TEXT")
    private String clientName;

    @Column(name = "client_id")
    private Long clientId;

    @Column(name = "brief_id")
    private Long briefId;

    @Column(name = "question_id")
    private Long questionId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected WhatsappQuoteSession() {
    }

    public WhatsappQuoteSession(Long userId, Instant now) {
        if (userId == null || now == null) {
            throw new IllegalArgumentException("user and time are required");
        }
        this.userId = userId;
        this.step = Step.IDLE;
        this.updatedAt = now;
    }

    /** The current step, or IDLE when the chat was left alone too long. */
    public Step stepAt(Instant now) {
        return updatedAt.plus(IDLE_TIMEOUT).isAfter(now) ? step : Step.IDLE;
    }

    public void reset(Instant now) {
        step = Step.IDLE;
        clientName = null;
        clientId = null;
        briefId = null;
        questionId = null;
        updatedAt = now;
    }

    public void askClientName(Instant now) {
        reset(now);
        step = Step.CLIENT_NAME;
    }

    public void askClientEmail(String name, Instant now) {
        clientName = name;
        step = Step.CLIENT_EMAIL;
        updatedAt = now;
    }

    /** The client was chosen; {@code name} is the stored one when the email matched an existing client. */
    public void askDescription(Long client, String name, Instant now) {
        clientId = client;
        clientName = name;
        step = Step.DESCRIPTION;
        updatedAt = now;
    }

    public void askQuestion(Long brief, Long question, Instant now) {
        briefId = brief;
        questionId = question;
        step = Step.QUESTION;
        updatedAt = now;
    }

    public Long getUserId() {
        return userId;
    }

    public Step getStep() {
        return step;
    }

    public String getClientName() {
        return clientName;
    }

    public Long getClientId() {
        return clientId;
    }

    public Long getBriefId() {
        return briefId;
    }

    public Long getQuestionId() {
        return questionId;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

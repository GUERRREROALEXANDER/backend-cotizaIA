package com.cotizaia.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A client brief after input-channel normalization. Whatever shape the channel
 * delivered (email body, WhatsApp message, web form fields), an Adapter reduces
 * it to this single entity, which is what lets every channel feed the same
 * downstream agent without the pipeline knowing where the brief came from.
 *
 * <p>{@code rawText} is the canonical, normalized brief text. {@code rawPayload}
 * keeps the original request verbatim for audit and provenance, so an operator
 * can always trace a quotation back to exactly what the client sent.
 */
@Entity
@Table(name = "briefs")
public class Brief {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private BriefChannel source;

    @Column(name = "raw_text", nullable = false)
    private String rawText;

    @Column(name = "raw_payload", nullable = false)
    private String rawPayload;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    protected Brief() {
    }

    public Brief(Client client, BriefChannel source, String rawText, String rawPayload, Instant receivedAt) {
        if (rawText == null || rawText.isBlank()) {
            throw new IllegalArgumentException("rawText must not be blank");
        }
        this.client = client;
        this.source = source;
        this.rawText = rawText;
        this.rawPayload = rawPayload;
        this.receivedAt = receivedAt;
    }

    @PrePersist
    void onCreate() {
        if (receivedAt == null) {
            receivedAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Client getClient() {
        return client;
    }

    public BriefChannel getSource() {
        return source;
    }

    public String getRawText() {
        return rawText;
    }

    public String getRawPayload() {
        return rawPayload;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }
}

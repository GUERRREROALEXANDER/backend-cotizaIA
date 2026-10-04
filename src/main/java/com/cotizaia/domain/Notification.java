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
import java.time.Instant;
import java.util.Objects;

/**
 * Persistent delivery record for an agency event (project.txt section 6: Observer).
 * Keeping one row per observer makes the feed and simulated channels auditable
 * in the same transaction as the proposal transition.
 */
@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agency_id", nullable = false)
    private Agency agency;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proposal_id")
    private Proposal proposal;

    @Column(nullable = false, length = 40)
    private String channel;

    @Column(name = "event_type", nullable = false, length = 60)
    private String eventType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    protected Notification() {
    }

    public Notification(Agency agency, Proposal proposal, String channel, String eventType,
            String payload, Instant sentAt) {
        if (agency == null || sentAt == null || invalid(channel, 40)
                || invalid(eventType, 60) || payload == null || payload.isBlank()) {
            throw new IllegalArgumentException("valid notification fields are required");
        }
        if (proposal != null && !Objects.equals(proposal.getBrief().getClient().getAgency().getId(),
                agency.getId())) {
            throw new IllegalArgumentException("proposal must belong to agency");
        }
        this.agency = agency;
        this.proposal = proposal;
        this.channel = channel;
        this.eventType = eventType;
        this.payload = payload;
        this.sentAt = sentAt;
    }

    private static boolean invalid(String value, int maximumLength) {
        return value == null || value.isBlank() || value.length() > maximumLength;
    }

    public Long getId() {
        return id;
    }

    public Agency getAgency() {
        return agency;
    }

    public Proposal getProposal() {
        return proposal;
    }

    public String getChannel() {
        return channel;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getSentAt() {
        return sentAt;
    }
}

package com.cotizaia.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * One structured requirement extracted from a {@link Brief}'s raw text by the
 * pipeline's Extract handler. It is a composed child of the brief and points at
 * the {@link RequirementType} it maps to in the agency catalog, so the next
 * step (estimation) has both the free-text description and the catalog anchor.
 *
 * <p>Design pattern - the constructor is a factory that enforces the domain
 * invariant: {@code ambiguityFlag} is derived from {@code confidence} instead
 * of being supplied by the caller, so a low-confidence extraction can never be
 * persisted as unambiguous. The threshold lives in the domain (not as a stored
 * generated column) because H2 and PostgreSQL cannot express it portably.
 */
@Entity
@Table(name = "extracted_requirements")
public class ExtractedRequirement {

    /** Extractions below this model confidence are surfaced as ambiguous. */
    public static final BigDecimal LOW_CONFIDENCE_THRESHOLD = new BigDecimal("0.60");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "brief_id", nullable = false)
    private Brief brief;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requirement_type_id", nullable = false)
    private RequirementType requirementType;

    @Column(nullable = false, length = 1000)
    private String description;

    @Column(name = "estimated_hours", precision = 10, scale = 2)
    private BigDecimal estimatedHours;

    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal confidence;

    @Column(name = "ambiguity_flag", nullable = false)
    private boolean ambiguityFlag;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ExtractedRequirement() {
    }

    public ExtractedRequirement(
            Brief brief,
            RequirementType requirementType,
            String description,
            BigDecimal estimatedHours,
            BigDecimal confidence) {
        if (brief == null) {
            throw new IllegalArgumentException("brief must not be null");
        }
        if (requirementType == null) {
            throw new IllegalArgumentException("requirementType must not be null");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("description must not be blank");
        }
        if (confidence == null) {
            throw new IllegalArgumentException("confidence must not be null");
        }
        if (confidence.compareTo(BigDecimal.ZERO) < 0 || confidence.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("confidence must be within [0, 1]");
        }
        this.brief = brief;
        this.requirementType = requirementType;
        this.description = description;
        this.estimatedHours = estimatedHours;
        this.confidence = confidence;
        this.ambiguityFlag = confidence.compareTo(LOW_CONFIDENCE_THRESHOLD) < 0;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Brief getBrief() {
        return brief;
    }

    public RequirementType getRequirementType() {
        return requirementType;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getEstimatedHours() {
        return estimatedHours;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public boolean isAmbiguityFlag() {
        return ambiguityFlag;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

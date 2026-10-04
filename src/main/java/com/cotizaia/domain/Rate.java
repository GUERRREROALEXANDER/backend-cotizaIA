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
import java.time.Instant;

/**
 * Hourly rate for a {@link Role}, in COP (Colombian pesos, whole numbers).
 * It belongs to one agency and one role; both scoping keys are stored so a
 * query can never leak across agencies.
 *
 * <p>Effective dating is optional: {@code effectiveFrom}/{@code effectiveTo}
 * may be null, meaning the rate has no start/end bound. Pricing picks the rate
 * valid at a point in time; a null window is the always-valid fallback.
 *
 * <p>The amount is a {@code long} on purpose: COP is quoted in whole pesos,
 * so there is no decimal scale to round and no {@code BigDecimal} needed.
 */
@Entity
@Table(name = "rates")
public class Rate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agency_id", nullable = false)
    private Agency agency;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @Column(name = "cop_per_hour", nullable = false)
    private long copPerHour;

    @Column(name = "effective_from")
    private Instant effectiveFrom;

    @Column(name = "effective_to")
    private Instant effectiveTo;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Rate() {
    }

    Rate(Role role, long copPerHour) {
        this.role = role;
        this.agency = role.getAgency();
        setCopPerHour(copPerHour);
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    /**
     * Domain guard mirroring the ck_rates_cop_positive DB constraint, so an
     * invalid amount fails fast in memory instead of at flush time.
     */
    public void setCopPerHour(long copPerHour) {
        if (copPerHour <= 0) {
            throw new IllegalArgumentException("copPerHour must be positive");
        }
        this.copPerHour = copPerHour;
    }

    /**
     * True when this rate is valid at {@code instant}. A null bound is
     * unbounded, so a rate with no window is valid at any instant.
     */
    public boolean isValidAt(Instant instant) {
        boolean afterStart = effectiveFrom == null || !instant.isBefore(effectiveFrom);
        boolean beforeEnd = effectiveTo == null || instant.isBefore(effectiveTo);
        return afterStart && beforeEnd;
    }

    public Long getId() {
        return id;
    }

    public Agency getAgency() {
        return agency;
    }

    public void setAgency(Agency agency) {
        this.agency = agency;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public long getCopPerHour() {
        return copPerHour;
    }

    public Instant getEffectiveFrom() {
        return effectiveFrom;
    }

    public void setEffectiveFrom(Instant effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }

    public Instant getEffectiveTo() {
        return effectiveTo;
    }

    public void setEffectiveTo(Instant effectiveTo) {
        this.effectiveTo = effectiveTo;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

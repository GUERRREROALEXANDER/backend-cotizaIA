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
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * A concrete deliverable/feature inside a {@link ServiceCatalog} entry
 * (e.g. "payment gateway", "responsive layout"). It is a composed child: the
 * catalog owns its lifecycle and uniqueness is only meaningful inside one
 * catalog entry.
 */
@Entity
@Table(
        name = "requirement_types",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_requirement_types_catalog_name",
                columnNames = {"service_catalog_id", "name"}))
public class RequirementType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_catalog_id", nullable = false)
    private ServiceCatalog serviceCatalog;

    @Column(nullable = false)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(name = "estimated_hours", precision = 10, scale = 2)
    private BigDecimal estimatedHours;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RequirementType() {
    }

    RequirementType(ServiceCatalog serviceCatalog, String name, String description, BigDecimal estimatedHours) {
        this.serviceCatalog = serviceCatalog;
        this.name = name;
        this.description = description;
        this.estimatedHours = estimatedHours;
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

    public ServiceCatalog getServiceCatalog() {
        return serviceCatalog;
    }

    public void setServiceCatalog(ServiceCatalog serviceCatalog) {
        this.serviceCatalog = serviceCatalog;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getEstimatedHours() {
        return estimatedHours;
    }

    public void setEstimatedHours(BigDecimal estimatedHours) {
        this.estimatedHours = estimatedHours;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

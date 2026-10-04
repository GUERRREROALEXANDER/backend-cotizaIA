package com.cotizaia.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Aggregate root for an agency's sellable services (web development, branding,
 * marketing...). It composes its {@link RequirementType} children, so a single
 * save persists the whole graph and removing the catalog removes them too —
 * the same aggregate-root style as {@link Agency}.
 *
 * <p>Delete rule: deleting a catalog entry cascades only to unquoted
 * requirement types. Once quoted items exist (proposal migration) they hold a
 * RESTRICT foreign key to requirement_types, so an entry already used in a
 * quotation cannot be deleted. That contract is documented in the V3 migration.
 */
@Entity
@Table(
        name = "service_catalog",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_service_catalog_agency_name", columnNames = {"agency_id", "name"}))
public class ServiceCatalog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agency_id", nullable = false)
    private Agency agency;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private ServiceCategory category;

    @Column(nullable = false)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "serviceCatalog", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RequirementType> requirementTypes = new ArrayList<>();

    protected ServiceCatalog() {
    }

    public ServiceCatalog(Agency agency, String name) {
        this.agency = agency;
        this.name = name;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    /**
     * Factory method on the aggregate: the child never exists without its
     * catalog, and the owning side of the association is set here.
     */
    public RequirementType addRequirementType(String name, String description, BigDecimal estimatedHours) {
        RequirementType requirementType = new RequirementType(this, name, description, estimatedHours);
        requirementTypes.add(requirementType);
        return requirementType;
    }

    public void removeRequirementType(RequirementType requirementType) {
        requirementTypes.remove(requirementType);
        requirementType.setServiceCatalog(null);
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

    public ServiceCategory getCategory() {
        return category;
    }

    public void setCategory(ServiceCategory category) {
        this.category = category;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<RequirementType> getRequirementTypes() {
        return requirementTypes;
    }
}

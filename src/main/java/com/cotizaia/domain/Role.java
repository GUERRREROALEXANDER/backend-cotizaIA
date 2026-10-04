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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A billable role inside an agency (designer, developer, PM...). It is the
 * grouping key of the {@link Rate} table: project.txt section 2 asks the owner
 * to define an hourly rate per role, so the role exists only inside one agency
 * and is unique by name there.
 *
 * <p>Aggregate-root style mirrors {@link ServiceCatalog}: the role composes its
 * rates, so a single save persists the graph and deleting the role removes its
 * rates. Rates are the historical timeline of a role, which is why they are
 * owned rather than referenced.
 */
@Entity
@Table(
        name = "roles",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_roles_agency_name", columnNames = {"agency_id", "name"}))
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agency_id", nullable = false)
    private Agency agency;

    @Column(nullable = false)
    private String name;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /**
     * Rate timeline for this role. Eagerly fetched because the pricing step
     * always needs the numbers together with the role name, and the set stays
     * small (one row per effective window).
     */
    @OneToMany(mappedBy = "role", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Rate> rates = new ArrayList<>();

    protected Role() {
    }

    public Role(Agency agency, String name) {
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
     * Factory method on the aggregate: a rate never exists without its role,
     * and the owning side of the association is set here so callers cannot
     * forget it.
     */
    public Rate addRate(long copPerHour) {
        Rate rate = new Rate(this, copPerHour);
        rates.add(rate);
        return rate;
    }

    public void removeRate(Rate rate) {
        rates.remove(rate);
        rate.setRole(null);
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<Rate> getRates() {
        return rates;
    }
}

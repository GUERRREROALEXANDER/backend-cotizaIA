package com.cotizaia.domain;

import com.cotizaia.pricing.PricingModel;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Agency aggregate root: it owns its staff ({@link AppUser}) and {@link Client}s.
 * Keeping both collections here lets a single save cascade the whole graph and
 * guarantees the one-owner rule is checked before touching the database.
 */
@Entity
@Table(name = "agencies")
public class Agency {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "pricing_model", nullable = false, length = 20)
    private PricingModel pricingModel = PricingModel.HOURLY;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "agency", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AppUser> users = new ArrayList<>();

    @OneToMany(mappedBy = "agency", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Client> clients = new ArrayList<>();

    protected Agency() {
    }

    public Agency(String name) {
        this.name = name;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    /**
     * Business rule: one owner per agency. Mirrors the DB constraint
     * uq_users_agency_owner so callers fail fast with a domain error.
     */
    public void addUser(AppUser user) {
        if (user.getRole() == UserRole.OWNER && hasOwner()) {
            throw new IllegalStateException("Agency already has an owner");
        }
        user.setAgency(this);
        users.add(user);
    }

    public void addClient(Client client) {
        client.setAgency(this);
        clients.add(client);
    }

    public boolean hasOwner() {
        return users.stream().anyMatch(user -> user.getRole() == UserRole.OWNER);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public PricingModel getPricingModel() {
        return pricingModel;
    }

    public void setPricingModel(PricingModel pricingModel) {
        if (pricingModel == null) {
            throw new IllegalArgumentException("pricingModel must not be null");
        }
        this.pricingModel = pricingModel;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<AppUser> getUsers() {
        return users;
    }

    public List<Client> getClients() {
        return clients;
    }
}

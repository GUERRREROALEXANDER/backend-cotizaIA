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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Locale;

/**
 * Owner or staff member of an {@link Agency}. The owner_flag discriminator is
 * derived from {@link UserRole} in the JPA lifecycle callbacks so the portable
 * UNIQUE(agency_id, owner_flag) constraint always sees TRUE for one owner only.
 */
@Entity
@Table(
        name = "users",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_users_agency_email", columnNames = {"agency_id", "email"}),
                @UniqueConstraint(name = "uq_users_agency_owner", columnNames = {"agency_id", "owner_flag"}),
                @UniqueConstraint(name = "uq_users_login_email", columnNames = "login_email")
        })
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agency_id", nullable = false)
    private Agency agency;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(nullable = false)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Column(name = "login_email", length = 255)
    private String loginEmail;

    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Column(name = "owner_flag")
    private Boolean ownerFlag;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AppUser() {
    }

    public AppUser(Agency agency, String fullName, String email, UserRole role) {
        this.agency = agency;
        this.fullName = fullName;
        this.email = email;
        this.role = role;
    }

    @PrePersist
    @PreUpdate
    void syncDerivedState() {
        this.ownerFlag = role == UserRole.OWNER ? Boolean.TRUE : null;
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public void assignCredentials(String loginEmail, String passwordHash) {
        if (loginEmail == null || loginEmail.isBlank() || passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("Login email and password hash are required");
        }
        this.loginEmail = loginEmail.trim().toLowerCase(Locale.ROOT);
        this.passwordHash = passwordHash;
    }

    public String getLoginEmail() {
        return loginEmail;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public boolean hasCredentials() {
        return loginEmail != null && passwordHash != null;
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

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public UserRole getRole() {
        return role;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }

    public Boolean getOwnerFlag() {
        return ownerFlag;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

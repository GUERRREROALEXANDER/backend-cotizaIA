package com.cotizaia.domain;

import com.cotizaia.crypto.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
import java.time.Instant;

/**
 * Client of an {@link Agency}. Each client belongs to exactly one agency
 * (FK clients.agency_id) and is unique by email within that agency.
 */
@Entity
@Table(
        name = "clients",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_clients_agency_email", columnNames = {"agency_id", "email"}))
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agency_id", nullable = false)
    private Agency agency;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(nullable = false)
    private String name;

    @Convert(converter = EncryptedStringConverter.class)
    @Column
    private String email;

    @Column(name = "email_hash", length = 64)
    private String emailHash;

    @Convert(converter = EncryptedStringConverter.class)
    @Column
    private String company;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Client() {
    }

    public Client(Agency agency, String name, String email) {
        this.agency = agency;
        this.name = name;
        this.email = email;
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

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getEmailHash() {
        return emailHash;
    }

    public void setEmailHash(String emailHash) {
        this.emailHash = emailHash;
    }

    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

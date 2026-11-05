package com.cotizaia.domain;

import com.cotizaia.crypto.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
 * One searchable past proposal: a PDF split into {@link KnowledgeChunk}s.
 * The file name is a business datum, so it is encrypted like every other
 * agency-visible string; the proposal linkage is kept as a bare id because
 * approved documents may outlive their source proposal.
 */
@Entity
@Table(name = "knowledge_documents")
public class KnowledgeDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agency_id", nullable = false)
    private Agency agency;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private KnowledgeDocumentKind kind;

    @Column(name = "source_proposal_id")
    private Long sourceProposalId;

    @Column(name = "page_count")
    private Integer pageCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected KnowledgeDocument() {
    }

    public KnowledgeDocument(Agency agency, String name, KnowledgeDocumentKind kind) {
        if (agency == null) {
            throw new IllegalArgumentException("agency is required");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        if (kind == null) {
            throw new IllegalArgumentException("kind is required");
        }
        this.agency = agency;
        this.name = name;
        this.kind = kind;
    }

    public void setSourceProposalId(Long sourceProposalId) {
        this.sourceProposalId = sourceProposalId;
    }

    public void setPageCount(Integer pageCount) {
        this.pageCount = pageCount;
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

    public String getName() {
        return name;
    }

    public KnowledgeDocumentKind getKind() {
        return kind;
    }

    public Long getSourceProposalId() {
        return sourceProposalId;
    }

    public Integer getPageCount() {
        return pageCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

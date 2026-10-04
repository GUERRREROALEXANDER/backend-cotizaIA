package com.cotizaia.domain;

import com.cotizaia.document.DocumentType;
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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Arrays;

/**
 * Persisted PDF child of a proposal (project.txt section 9): retains the exact rendered bytes
 * so later downloads reflect the version issued, even if proposal data changes.
 */
@Entity
@Table(name = "proposal_documents", uniqueConstraints = @UniqueConstraint(
        name = "uq_proposal_documents_proposal_type", columnNames = {"proposal_id", "type"}))
public class ProposalDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proposal_id", nullable = false)
    private Proposal proposal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DocumentType type;

    @Column(name = "file_name", nullable = false, length = 160)
    private String fileName;

    @Column(name = "content_type", nullable = false, length = 60)
    private String contentType = "application/pdf";

    @Column(nullable = false, columnDefinition = "BYTEA")
    private byte[] content;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    protected ProposalDocument() {
    }

    public ProposalDocument(Proposal proposal, DocumentType type, byte[] content, String fileName,
            Instant generatedAt) {
        if (proposal == null || type == null) {
            throw new IllegalArgumentException("proposal and type are required");
        }
        this.proposal = proposal;
        this.type = type;
        replaceContent(content, fileName, generatedAt);
    }

    public void replaceContent(byte[] content, String fileName, Instant generatedAt) {
        if (content == null || content.length == 0 || fileName == null || fileName.isBlank()
                || generatedAt == null) {
            throw new IllegalArgumentException("PDF content, file name and generation time are required");
        }
        this.content = Arrays.copyOf(content, content.length);
        this.fileName = fileName;
        this.generatedAt = generatedAt;
        this.sizeBytes = content.length;
    }

    public Long getId() {
        return id;
    }

    public Proposal getProposal() {
        return proposal;
    }

    public DocumentType getType() {
        return type;
    }

    public String getFileName() {
        return fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public byte[] getContent() {
        return Arrays.copyOf(content, content.length);
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }
}

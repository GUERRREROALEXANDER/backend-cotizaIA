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
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * A piece of a {@link KnowledgeDocument}: either a free-text passage from one
 * page, or a priced line item with its requirement type and approved hours.
 * Only the text is encrypted: the requirement type is catalog data and hours
 * is a number, so neither is agency-identifying on its own.
 */
@Entity
@Table(name = "knowledge_chunks")
public class KnowledgeChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private KnowledgeDocument document;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChunkKind kind;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(nullable = false, columnDefinition = "TEXT")
    private String text;

    @Column
    private Integer page;

    @Column(name = "requirement_type", length = 255)
    private String requirementType;

    @Column(precision = 10, scale = 2)
    private BigDecimal hours;

    @Column(nullable = false)
    private int ordinal;

    protected KnowledgeChunk() {
    }

    public KnowledgeChunk(KnowledgeDocument document, ChunkKind kind, String text, Integer page,
            String requirementType, BigDecimal hours, int ordinal) {
        if (document == null) {
            throw new IllegalArgumentException("document is required");
        }
        if (kind == null) {
            throw new IllegalArgumentException("kind is required");
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("text is required");
        }
        this.document = document;
        this.kind = kind;
        this.text = text;
        this.page = page;
        this.requirementType = requirementType;
        this.hours = hours;
        this.ordinal = ordinal;
    }

    public Long getId() {
        return id;
    }

    public KnowledgeDocument getDocument() {
        return document;
    }

    public ChunkKind getKind() {
        return kind;
    }

    public String getText() {
        return text;
    }

    public Integer getPage() {
        return page;
    }

    public String getRequirementType() {
        return requirementType;
    }

    public BigDecimal getHours() {
        return hours;
    }

    public int getOrdinal() {
        return ordinal;
    }
}

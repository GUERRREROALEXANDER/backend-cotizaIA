package com.cotizaia.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Arrays;

/**
 * Original RFP PDF kept beside the brief it produced. The bytes are retained
 * verbatim so the operator can always download exactly what the client sent,
 * even after the extracted text was truncated for the agent.
 */
@Entity
@Table(name = "brief_attachments")
public class BriefAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "brief_id", nullable = false, unique = true)
    private Brief brief;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "content_type", nullable = false, length = 60)
    private String contentType = "application/pdf";

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "page_count", nullable = false)
    private int pageCount;

    @Column(nullable = false, columnDefinition = "BYTEA")
    private byte[] content;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    protected BriefAttachment() {
    }

    public BriefAttachment(Brief brief, String fileName, String contentType, byte[] content, int pageCount,
            Instant uploadedAt) {
        if (brief == null) {
            throw new IllegalArgumentException("brief is required");
        }
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("file name is required");
        }
        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("content type is required");
        }
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("PDF content is required");
        }
        if (pageCount < 1) {
            throw new IllegalArgumentException("page count must be at least 1");
        }
        if (uploadedAt == null) {
            throw new IllegalArgumentException("upload time is required");
        }
        this.brief = brief;
        this.fileName = fileName;
        this.contentType = contentType;
        this.content = Arrays.copyOf(content, content.length);
        this.sizeBytes = content.length;
        this.pageCount = pageCount;
        this.uploadedAt = uploadedAt;
    }

    public Long getId() {
        return id;
    }

    public Brief getBrief() {
        return brief;
    }

    public String getFileName() {
        return fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public int getPageCount() {
        return pageCount;
    }

    public byte[] getContent() {
        return Arrays.copyOf(content, content.length);
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }
}

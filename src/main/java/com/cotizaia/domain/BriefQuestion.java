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
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Retains one clarification and its answer on a brief (project.txt section 2).
 * Resolution pairs the answer with its timestamp so later runs can reuse client evidence
 * without reopening a question that has already been answered.
 */
@Entity
@Table(name = "brief_questions")
public class BriefQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "brief_id", nullable = false)
    private Brief brief;

    @Column(nullable = false, length = 60)
    private String code;

    @Column(nullable = false, length = 1000)
    private String question;

    @Column(nullable = false)
    private boolean blocking;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QuestionStatus status;

    @Column(columnDefinition = "TEXT")
    private String answer;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    protected BriefQuestion() {
    }

    public static BriefQuestion open(Brief brief, String code, String question, boolean blocking) {
        if (brief == null) {
            throw new IllegalArgumentException("brief is required");
        }
        validateText(code, 60, "code");
        BriefQuestion result = new BriefQuestion();
        result.brief = brief;
        result.code = code;
        result.status = QuestionStatus.OPEN;
        result.refresh(question, blocking);
        return result;
    }

    public void resolve(String answer, Instant at) {
        requireOpen();
        if (answer == null || answer.isBlank()) {
            throw new IllegalArgumentException("answer must not be blank");
        }
        if (at == null) {
            throw new IllegalArgumentException("resolvedAt is required");
        }
        this.answer = answer;
        resolvedAt = at;
        status = QuestionStatus.RESOLVED;
    }

    public void refresh(String question, boolean blocking) {
        requireOpen();
        validateText(question, 1000, "question");
        this.question = question;
        this.blocking = blocking;
    }

    private void requireOpen() {
        if (status != QuestionStatus.OPEN) {
            throw new IllegalStateException("Question is already resolved");
        }
    }

    private static void validateText(String value, int limit, String field) {
        if (value == null || value.isBlank() || value.length() > limit) {
            throw new IllegalArgumentException(field + " must contain between 1 and " + limit + " characters");
        }
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

    public Brief getBrief() {
        return brief;
    }

    public String getCode() {
        return code;
    }

    public String getQuestion() {
        return question;
    }

    public boolean isBlocking() {
        return blocking;
    }

    public QuestionStatus getStatus() {
        return status;
    }

    public String getAnswer() {
        return answer;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }
}

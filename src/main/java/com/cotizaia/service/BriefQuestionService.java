package com.cotizaia.service;

import com.cotizaia.domain.Brief;
import com.cotizaia.domain.BriefQuestion;
import com.cotizaia.domain.QuestionStatus;
import com.cotizaia.repository.BriefQuestionRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reconciles clarification evidence across analyses (project.txt section 2).
 * Plain input records keep question persistence independent from the AI pipeline,
 * while resolved answers remain immutable evidence even if no longer detected.
 */
@Service
public class BriefQuestionService {

    private final BriefQuestionRepository questions;

    public BriefQuestionService(BriefQuestionRepository questions) {
        this.questions = questions;
    }

    @Transactional
    public List<BriefQuestion> sync(Brief brief, List<QuestionSpec> detected) {
        if (brief == null || brief.getId() == null || detected == null) {
            throw new IllegalArgumentException("Persisted brief and detected questions are required");
        }
        Map<String, QuestionSpec> pending = new LinkedHashMap<>();
        for (QuestionSpec spec : detected) {
            if (spec == null || pending.putIfAbsent(spec.code(), spec) != null) {
                throw new IllegalArgumentException("Detected question codes must be unique");
            }
        }
        for (BriefQuestion question : questions.findByBriefIdOrderByIdAsc(brief.getId())) {
            QuestionSpec spec = pending.remove(question.getCode());
            if (question.getStatus() == QuestionStatus.OPEN) {
                if (spec == null) {
                    questions.delete(question);
                } else {
                    question.refresh(spec.question(), spec.blocking());
                }
            }
        }
        for (QuestionSpec spec : pending.values()) {
            questions.save(BriefQuestion.open(brief, spec.code(), spec.question(), spec.blocking()));
        }
        questions.flush();
        return questions.findByBriefIdOrderByIdAsc(brief.getId());
    }

    @Transactional
    public BriefQuestion resolve(Long briefId, Long questionId, String answer) {
        BriefQuestion question = questions.findByIdAndBriefId(questionId, briefId)
                .orElseThrow(() -> new NoSuchElementException("Question not found: " + questionId));
        question.resolve(answer, Instant.now());
        return question;
    }

    @Transactional(readOnly = true)
    public List<ResolvedAnswer> answers(Long briefId) {
        return questions.findByBriefIdAndStatusOrderByIdAsc(briefId, QuestionStatus.RESOLVED).stream()
                .map(question -> new ResolvedAnswer(question.getCode(), question.getQuestion(), question.getAnswer()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BriefQuestion> list(Long briefId) {
        return questions.findByBriefIdOrderByIdAsc(briefId);
    }

    /** Describes one detected clarification without depending on agent types. */
    public record QuestionSpec(String code, String question, boolean blocking) {
    }

    /** Supplies durable client evidence for a later analysis. */
    public record ResolvedAnswer(String code, String question, String answer) {
    }
}

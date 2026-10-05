package com.cotizaia.repository;

import com.cotizaia.domain.BriefQuestion;
import com.cotizaia.domain.QuestionStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Reads clarifications within a brief (project.txt section 2) to prevent answers
 * from being applied to a question owned by another brief.
 */
public interface BriefQuestionRepository extends JpaRepository<BriefQuestion, Long> {

    List<BriefQuestion> findByBriefIdOrderByIdAsc(Long briefId);

    Optional<BriefQuestion> findByIdAndBriefId(Long id, Long briefId);

    List<BriefQuestion> findByBriefIdAndStatusOrderByIdAsc(Long briefId, QuestionStatus status);
}

package com.cotizaia.repository;

import com.cotizaia.domain.ExtractedRequirement;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository pattern: extracted requirements are read back per brief, and the
 * ambiguity query hands the approval UI its clarification questions directly
 * instead of filtering the full brief in memory.
 */
public interface ExtractedRequirementRepository extends JpaRepository<ExtractedRequirement, Long> {

    List<ExtractedRequirement> findByBriefIdOrderByIdAsc(Long briefId);

    List<ExtractedRequirement> findByBriefIdAndBriefClientAgencyIdOrderByIdAsc(Long briefId, Long agencyId);

    List<ExtractedRequirement> findByBriefIdAndAmbiguityFlagTrueOrderByIdAsc(Long briefId);

    long countByBriefId(Long briefId);

    long countByBriefIdAndAmbiguityFlagTrue(Long briefId);
}

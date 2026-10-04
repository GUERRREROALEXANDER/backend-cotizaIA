package com.cotizaia.repository;

import com.cotizaia.domain.Proposal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository pattern: proposals are read per brief (the approval queue) and
 * scoped by agency so one agency can never open another's quotation.
 */
public interface ProposalRepository extends JpaRepository<Proposal, Long> {

    List<Proposal> findByBriefIdOrderByIdAsc(Long briefId);

    Optional<Proposal> findByIdAndBriefClientAgencyId(Long id, Long agencyId);

    long countByBriefId(Long briefId);
}

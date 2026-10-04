package com.cotizaia.repository;

import com.cotizaia.domain.ProposalExtra;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository pattern for the structured extras of a proposal, ordered by
 * insertion so the audit trail reads in the order the decorators applied.
 */
public interface ProposalExtraRepository extends JpaRepository<ProposalExtra, Long> {

    List<ProposalExtra> findByProposalIdOrderByIdAsc(Long proposalId);
}

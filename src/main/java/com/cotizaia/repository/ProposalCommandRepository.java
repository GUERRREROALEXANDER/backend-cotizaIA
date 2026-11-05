package com.cotizaia.repository;

import com.cotizaia.domain.ProposalCommandRecord;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Command log per proposal: oldest first for the audit view, newest last for undo. */
public interface ProposalCommandRepository extends JpaRepository<ProposalCommandRecord, Long> {

    List<ProposalCommandRecord> findByProposalIdOrderByExecutedAtAscIdAsc(Long proposalId);

    Optional<ProposalCommandRecord> findFirstByProposalIdAndUndoneAtIsNullOrderByExecutedAtDescIdDesc(
            Long proposalId);
}

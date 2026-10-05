package com.cotizaia.repository;

import com.cotizaia.domain.Proposal;
import com.cotizaia.domain.ProposalStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository pattern: proposals are read per brief (the approval queue) and
 * scoped by agency so one agency can never open another's quotation.
 */
public interface ProposalRepository extends JpaRepository<Proposal, Long> {

    List<Proposal> findByBriefIdOrderByIdAsc(Long briefId);

    Optional<Proposal> findByIdAndBriefClientAgencyId(Long id, Long agencyId);

    long countByBriefId(Long briefId);

    List<Proposal> findByStatusAndBriefClientAgencyIdOrderByCreatedAtAsc(ProposalStatus status, Long agencyId);

    List<Proposal> findByBriefClientAgencyIdOrderByCreatedAtDesc(Long agencyId);

    List<Proposal> findByBriefIdOrderByIdDesc(Long briefId);

    @Query("select p.status as status, count(p) as count from Proposal p "
            + "where p.brief.client.agency.id = :agencyId group by p.status")
    List<StatusCount> countStatuses(@Param("agencyId") Long agencyId);

    @Query("select count(distinct p.id) from ProposalStatusChange h join h.proposal p "
            + "where p.brief.client.agency.id = :agencyId and h.toStatus = :status")
    long countReached(@Param("agencyId") Long agencyId, @Param("status") ProposalStatus status);

    @Query("select p.id as proposalId, b.receivedAt as receivedAt, min(h.changedAt) as sentAt "
            + "from ProposalStatusChange h join h.proposal p join p.brief b join b.client c "
            + "where c.agency.id = :agencyId and h.toStatus = :status group by p.id, b.receivedAt")
    List<SentTiming> firstSentTimes(@Param("agencyId") Long agencyId, @Param("status") ProposalStatus status);

    /** Current status totals for the agency dashboard. */
    interface StatusCount {

        ProposalStatus getStatus();

        long getCount();
    }

    /** First send evidence with the original intake timestamp. */
    interface SentTiming {

        Long getProposalId();

        Instant getReceivedAt();

        Instant getSentAt();
    }
}

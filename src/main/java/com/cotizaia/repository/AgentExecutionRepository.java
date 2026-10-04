package com.cotizaia.repository;

import com.cotizaia.domain.AgentExecution;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository pattern: the run log is read per brief (the panel's live feed) and
 * scoped by agency so one agency can never open another's pipeline history.
 */
public interface AgentExecutionRepository extends JpaRepository<AgentExecution, Long> {

    List<AgentExecution> findByBriefIdOrderByIdAsc(Long briefId);

    Optional<AgentExecution> findByIdAndBriefClientAgencyId(Long id, Long agencyId);

    long countByBriefId(Long briefId);
}

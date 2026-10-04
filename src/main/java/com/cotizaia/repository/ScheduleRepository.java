package com.cotizaia.repository;

import com.cotizaia.domain.Schedule;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository pattern: at most one schedule exists per proposal, so it is looked
 * up by that natural key rather than by its surrogate id.
 */
public interface ScheduleRepository extends JpaRepository<Schedule, Long> {

    Optional<Schedule> findByProposalId(Long proposalId);
}

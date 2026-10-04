package com.cotizaia.repository;

import com.cotizaia.domain.AgentStep;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository pattern: the ordered timeline query backs the GET execution
 * endpoint, so the panel receives steps in chain order without sorting in
 * memory.
 */
public interface AgentStepRepository extends JpaRepository<AgentStep, Long> {

    List<AgentStep> findByExecutionIdOrderByStepOrderAsc(Long executionId);

    long countByExecutionId(Long executionId);
}

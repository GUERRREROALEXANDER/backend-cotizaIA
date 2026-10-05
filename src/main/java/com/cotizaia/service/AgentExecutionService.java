package com.cotizaia.service;

import com.cotizaia.domain.AgentExecution;
import com.cotizaia.domain.AgentStep;
import com.cotizaia.repository.AgentExecutionRepository;
import com.cotizaia.repository.AgentStepRepository;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read side of the run log: the panel fetches one execution and its ordered step
 * timeline. Kept separate from {@code PipelineRunRecorder} so the write path
 * (own transaction, failure-safe) and the read path (read-only) have distinct
 * transactional contracts.
 */
@Service
public class AgentExecutionService {

    private final AgentExecutionRepository agentExecutionRepository;
    private final AgentStepRepository agentStepRepository;

    public AgentExecutionService(
            AgentExecutionRepository agentExecutionRepository,
            AgentStepRepository agentStepRepository) {
        this.agentExecutionRepository = agentExecutionRepository;
        this.agentStepRepository = agentStepRepository;
    }

    @Transactional(readOnly = true)
    public AgentExecution get(Long agencyId, Long id) {
        return agentExecutionRepository.findByIdAndBriefClientAgencyId(id, agencyId)
                .orElseThrow(() -> new NoSuchElementException("Agent execution not found: " + id));
    }

    @Transactional(readOnly = true)
    public AgentExecution get(Long id) {
        return agentExecutionRepository
                .findById(id)
                .orElseThrow(() -> new NoSuchElementException("Agent execution not found: " + id));
    }

    /** Steps in chain order — the timeline the live view renders. */
    @Transactional(readOnly = true)
    public List<AgentStep> timeline(Long executionId) {
        get(executionId);
        return agentStepRepository.findByExecutionIdOrderByStepOrderAsc(executionId);
    }

    @Transactional(readOnly = true)
    public List<AgentExecution> listByBrief(Long briefId) {
        return agentExecutionRepository.findByBriefIdOrderByIdAsc(briefId);
    }
}

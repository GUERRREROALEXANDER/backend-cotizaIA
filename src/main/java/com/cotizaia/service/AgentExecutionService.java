package com.cotizaia.service;

import com.cotizaia.domain.AgentExecution;
import com.cotizaia.domain.AgentStep;
import com.cotizaia.repository.AgentExecutionRepository;
import com.cotizaia.repository.AgentStepRepository;
import com.cotizaia.repository.BriefRepository;
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

    private final BriefRepository briefs;

    public AgentExecutionService(
            AgentExecutionRepository agentExecutionRepository,
            AgentStepRepository agentStepRepository, BriefRepository briefs) {
        this.agentExecutionRepository = agentExecutionRepository;
        this.agentStepRepository = agentStepRepository;
        this.briefs = briefs;
    }

    @Transactional(readOnly = true)
    public Timeline detail(Long agencyId, Long id) {
        AgentExecution execution = get(agencyId, id);
        return new Timeline(execution, agentStepRepository.findByExecutionIdOrderByStepOrderAsc(id));
    }

    @Transactional(readOnly = true)
    public List<Timeline> listByBrief(Long agencyId, Long briefId) {
        briefs.findByIdAndClientAgencyId(briefId, agencyId)
                .orElseThrow(() -> new NoSuchElementException("Brief not found: " + briefId));
        return agentExecutionRepository.findByBriefIdOrderByIdAsc(briefId).stream()
                .map(execution -> new Timeline(execution,
                        agentStepRepository.findByExecutionIdOrderByStepOrderAsc(execution.getId()))).toList();
    }

    /** Execution with its ordered steps loaded in the same read transaction. */
    public record Timeline(AgentExecution execution, List<AgentStep> steps) {
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

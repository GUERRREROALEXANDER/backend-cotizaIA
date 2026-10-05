package com.cotizaia.service;

import com.cotizaia.domain.AgentExecution;
import com.cotizaia.domain.Brief;
import com.cotizaia.domain.BriefQuestion;
import com.cotizaia.domain.Proposal;
import com.cotizaia.repository.AgentExecutionRepository;
import com.cotizaia.repository.BriefQuestionRepository;
import com.cotizaia.repository.ProposalRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assembles the intake review view (project.txt section 2).
 * The scoped brief lookup precedes every child query, including briefs with no proposal yet.
 */
@Service
@Transactional(readOnly = true)
public class BriefDetailService {

    private final BriefService briefs;

    private final BriefQuestionRepository questions;

    private final ProposalRepository proposals;

    private final AgentExecutionRepository executions;

    public BriefDetailService(BriefService briefs, BriefQuestionRepository questions,
            ProposalRepository proposals, AgentExecutionRepository executions) {
        this.briefs = briefs;
        this.questions = questions;
        this.proposals = proposals;
        this.executions = executions;
    }

    public Detail get(Long agencyId, Long id) {
        Brief brief = briefs.get(agencyId, id);
        Proposal latest = proposals.findByBriefIdOrderByIdDesc(id).stream().findFirst().orElse(null);
        return new Detail(brief, questions.findByBriefIdOrderByIdAsc(id), latest,
                executions.findByBriefIdOrderByIdAsc(id).stream().map(AgentExecution::getId).toList());
    }

    /** Scoped intake and its persisted analysis evidence. */
    public record Detail(Brief brief, List<BriefQuestion> questions, Proposal latest, List<Long> executionIds) {
    }
}

package com.cotizaia.facade;

import com.cotizaia.agent.AgentRun;
import com.cotizaia.agent.pipeline.AgentPipeline;
import com.cotizaia.agent.pipeline.Clarification;
import com.cotizaia.agent.pipeline.PipelineContext;
import com.cotizaia.domain.Brief;
import com.cotizaia.domain.BriefChannel;
import com.cotizaia.repository.BriefRepository;
import com.cotizaia.repository.ClientRepository;
import com.cotizaia.service.BriefQuestionService;
import com.cotizaia.service.BriefService;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Coordinates an end-to-end quotation (project.txt sections 2 and 6).
 * README's {@code CotizacionFacade.procesarBrief()} maps to {@link #processBrief(Long)}.
 *
 * <p>Design pattern - <b>Facade</b>: this entry point hides intake through {@link BriefService},
 * classification, extraction, estimation, pricing and generation through {@link AgentPipeline},
 * and persistence plus observer notifications through {@link ProposalLifecycleWriter}.
 *
 * <p>This is deliberately not one large transaction: any caller transaction is suspended.
 * The recorder commits its run log independently, including FAILED runs, before errors propagate.
 * Only a successful chain enters the writer's short business transaction, preventing half-written quotes.
 */
@Service
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class QuotationFacade {

    private final BriefService briefService;

    private final BriefRepository briefs;

    private final ClientRepository clients;

    private final BriefQuestionService questions;

    private final AgentPipeline pipeline;

    private final ProposalLifecycleWriter writer;

    public QuotationFacade(BriefService briefService, BriefRepository briefs, ClientRepository clients,
            BriefQuestionService questions, AgentPipeline pipeline, ProposalLifecycleWriter writer) {
        this.briefService = briefService;
        this.briefs = briefs;
        this.clients = clients;
        this.questions = questions;
        this.pipeline = pipeline;
        this.writer = writer;
    }

    public QuotationResult processBrief(Long briefId) {
        Long proposalId = writer.openForAnalysis(briefId);
        Brief brief = briefs.findWithClientById(briefId)
                .orElseThrow(() -> new NoSuchElementException("Brief not found: " + briefId));
        List<Clarification> answers = questions.answers(briefId).stream()
                .map(answer -> new Clarification(answer.code(), answer.question(), answer.answer())).toList();
        PipelineContext context = new PipelineContext(brief, brief.getClient().getAgency().getId(),
                brief.getClient().getAgency().getName(), brief.getClient().getName(), answers);
        AgentRun run = pipeline.run(context);
        return writer.applyOutcome(proposalId, run);
    }

    public QuotationResult processBrief(Long agencyId, Long briefId) {
        requireAgencyBrief(agencyId, briefId);
        return processBrief(briefId);
    }

    public QuotationResult ingestAndProcess(BriefChannel channel, Long clientId, Map<String, Object> payload) {
        return processBrief(briefService.ingest(channel, clientId, payload).getId());
    }

    public QuotationResult ingestAndProcess(Long agencyId, BriefChannel channel, Long clientId,
            Map<String, Object> payload) {
        clients.findById(clientId).filter(client -> client.getAgency().getId().equals(agencyId))
                .orElseThrow(() -> new NoSuchElementException("Client not found: " + clientId));
        Brief brief = briefService.ingest(channel, clientId, payload);
        return processBrief(agencyId, brief.getId());
    }

    public QuotationResult answerQuestion(Long briefId, Long questionId, String answer) {
        questions.resolve(briefId, questionId, answer);
        return processBrief(briefId);
    }

    public QuotationResult answerQuestion(Long agencyId, Long briefId, Long questionId, String answer) {
        requireAgencyBrief(agencyId, briefId);
        return answerQuestion(briefId, questionId, answer);
    }

    private void requireAgencyBrief(Long agencyId, Long briefId) {
        briefs.findByIdAndClientAgencyId(briefId, agencyId)
                .orElseThrow(() -> new NoSuchElementException("Brief not found: " + briefId));
    }
}

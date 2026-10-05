package com.cotizaia.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cotizaia.agent.AgentRun;
import com.cotizaia.agent.PipelineRunRecorder.PipelineFailedException;
import com.cotizaia.agent.pipeline.AgentPipeline;
import com.cotizaia.agent.pipeline.PipelineContext;
import com.cotizaia.agent.pipeline.QuoteDraft;
import com.cotizaia.document.DocumentType;
import com.cotizaia.domain.Agency;
import com.cotizaia.domain.Brief;
import com.cotizaia.domain.BriefChannel;
import com.cotizaia.domain.Client;
import com.cotizaia.domain.ContractStatus;
import com.cotizaia.domain.ExecutionStatus;
import com.cotizaia.domain.Notification;
import com.cotizaia.domain.Phase;
import com.cotizaia.domain.Proposal;
import com.cotizaia.domain.ProposalStatus;
import com.cotizaia.domain.QuestionStatus;
import com.cotizaia.domain.QuotedItem;
import com.cotizaia.domain.Role;
import com.cotizaia.domain.ServiceCatalog;
import com.cotizaia.repository.AgencyRepository;
import com.cotizaia.repository.AgentExecutionRepository;
import com.cotizaia.repository.AgentStepRepository;
import com.cotizaia.repository.BriefRepository;
import com.cotizaia.repository.ClientRepository;
import com.cotizaia.repository.ContractRepository;
import com.cotizaia.repository.ExtractedRequirementRepository;
import com.cotizaia.repository.NotificationRepository;
import com.cotizaia.repository.ProposalDocumentRepository;
import com.cotizaia.repository.ProposalRepository;
import com.cotizaia.repository.RoleRepository;
import com.cotizaia.repository.ServiceCatalogRepository;
import com.cotizaia.service.ApprovalQueueService;
import com.cotizaia.service.BriefQuestionService;
import com.lowagie.text.pdf.PdfReader;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = "llm.provider=stub")
@ActiveProfiles("test")
class QuotationFacadeIntegrationTests {

    private static final String RESTAURANT = "Hola, tengo un restaurante y necesito una pagina web donde los "
            + "clientes vean el menu y puedan hacer reservas en linea. Cuanto me sale?";

    @Autowired
    private QuotationFacade facade;

    @Autowired
    private AgentPipeline pipeline;

    @Autowired
    private ProposalLifecycleWriter writer;

    @Autowired
    private ApprovalQueueService approval;

    @Autowired
    private BriefQuestionService questions;

    @Autowired
    private AgencyRepository agencies;

    @Autowired
    private ClientRepository clients;

    @Autowired
    private BriefRepository briefs;

    @Autowired
    private RoleRepository roles;

    @Autowired
    private ServiceCatalogRepository catalogs;

    @Autowired
    private ProposalRepository proposals;

    @Autowired
    private ExtractedRequirementRepository requirements;

    @Autowired
    private ContractRepository contracts;

    @Autowired
    private ProposalDocumentRepository documents;

    @Autowired
    private AgentExecutionRepository executions;

    @Autowired
    private AgentStepRepository steps;

    @Autowired
    private NotificationRepository notifications;

    @Autowired
    private TransactionTemplate transactions;

    @Test
    void oneCallPersistsCompleteRestaurantQuotationAndObserverNotifications() throws Exception {
        Brief brief = fixture(RESTAURANT, true);
        QuotationResult result = facade.processBrief(brief.getClient().getAgency().getId(), brief.getId());
        assertThat(result.proposalStatus()).isEqualTo(ProposalStatus.IN_REVIEW);
        assertThat(result.executionStatus()).isEqualTo(ExecutionStatus.SUCCEEDED);
        assertThat(result.halted()).isFalse();
        assertThat(result.total()).isPositive();
        assertThat(result.pricingModel()).isNotNull();
        assertThat(result.documents()).hasSize(3);
        assertThat(result.questions()).hasSize(2).allSatisfy(question -> {
            assertThat(question.status()).isEqualTo(QuestionStatus.OPEN);
            assertThat(question.blocking()).isFalse();
        });
        assertThat(result.questions()).extracting(QuotationResult.QuestionView::code)
                .containsExactly("PAGE_COUNT_MISSING", "DEADLINE_MISSING");
        assertThat(steps.findByExecutionIdOrderByStepOrderAsc(result.executionId())).hasSize(7);
        assertThat(executions.findById(result.executionId()).orElseThrow().getStatus())
                .isEqualTo(ExecutionStatus.SUCCEEDED);
        transactions.executeWithoutResult(status -> {
            Proposal proposal = proposals.findById(result.proposalId()).orElseThrow();
            assertThat(proposal.getItems()).hasSize(3);
            assertThat(proposal.getSchedulePlan().getPhases()).hasSize(4);
            assertThat(proposal.getSchedulePlan().getPhases()).extracting(Phase::getStartWeek)
                    .containsExactly(1, 2, 3, 4);
            assertThat(contracts.findByProposalId(result.proposalId()).orElseThrow().getStatus())
                    .isEqualTo(ContractStatus.DRAFT);
        });
        assertThat(documents.findByProposalIdOrderByTypeAsc(result.proposalId())).hasSize(3);
        for (var document : documents.findByProposalIdOrderByTypeAsc(result.proposalId())) {
            PdfReader reader = new PdfReader(document.getContent());
            try {
                assertThat(reader.getNumberOfPages()).isPositive();
            } finally {
                reader.close();
            }
        }
        List<Notification> events = notifications.findByProposalIdOrderByIdAsc(result.proposalId());
        assertThat(events).hasSize(9);
        List<ProposalStatus> statuses = List.of(ProposalStatus.ANALYZING, ProposalStatus.QUOTED,
                ProposalStatus.IN_REVIEW);
        for (int index = 0; index < statuses.size(); index++) {
            List<Notification> transition = events.subList(index * 3, index * 3 + 3);
            assertThat(transition).extracting(Notification::getChannel)
                    .containsExactlyInAnyOrder("DASHBOARD", "EMAIL", "ANALYTICS");
            String expected = statuses.get(index).name();
            assertThat(transition).allSatisfy(event -> assertThat(event.getPayload()).contains(expected));
        }
    }

    @Test
    void vagueBriefHaltsAndAnswersRerunDownstreamHandlers() {
        Brief brief = fixture("hola, necesito ayuda con algo", true);
        QuotationResult halted = facade.processBrief(brief.getId());
        assertThat(halted.halted()).isTrue();
        assertThat(halted.proposalStatus()).isEqualTo(ProposalStatus.ANALYZING);
        assertThat(halted.documents()).isEmpty();
        assertThat(requirements.countByBriefId(brief.getId())).isZero();
        assertThat(documents.findByProposalIdOrderByTypeAsc(halted.proposalId())).isEmpty();
        assertThat(halted.questions().stream().filter(QuotationResult.QuestionView::blocking))
                .extracting(QuotationResult.QuestionView::code).containsExactly("PROJECT_TYPE_UNKNOWN", "NO_SCOPE");
        Long scopeId = questionId(halted, "NO_SCOPE");
        Long typeId = questionId(halted, "PROJECT_TYPE_UNKNOWN");
        QuotationResult partiallyAnswered = facade.answerQuestion(brief.getId(), scopeId, "Menu digital y reservas");
        assertThat(partiallyAnswered.halted()).isTrue();
        QuotationResult complete = facade.answerQuestion(brief.getId(), typeId,
                "Una pagina web con menu digital y reservas");
        assertThat(complete.proposalId()).isEqualTo(halted.proposalId());
        assertThat(complete.proposalStatus()).isEqualTo(ProposalStatus.IN_REVIEW);
        assertThat(complete.halted()).isFalse();
        assertThat(requirements.countByBriefId(brief.getId())).isEqualTo(3);
        assertThat(questions.answers(brief.getId())).hasSize(2);
        assertThat(questions.list(brief.getId()).stream().filter(question -> question.getId().equals(typeId)
                || question.getId().equals(scopeId))).allSatisfy(question -> {
                    assertThat(question.getStatus()).isEqualTo(QuestionStatus.RESOLVED);
                    assertThat(question.getAnswer()).isNotBlank();
                });
        assertThat(executions.findByBriefIdOrderByIdAsc(brief.getId())).hasSize(3);
        assertThat(steps.findByExecutionIdOrderByStepOrderAsc(complete.executionId())).hasSize(7);
    }

    @Test
    void nonBlockingAnswerRequotesWithoutReopeningAnsweredCodeAndClearsApproval() {
        Brief brief = fixture(RESTAURANT, true);
        Long agencyId = brief.getClient().getAgency().getId();
        QuotationResult first = facade.processBrief(brief.getId());
        approval.approve(agencyId, first.proposalId());
        Long questionId = questionId(first, "PAGE_COUNT_MISSING");
        QuotationResult second = facade.answerQuestion(agencyId, brief.getId(), questionId, "Tres paginas");
        assertThat(second.proposalId()).isEqualTo(first.proposalId());
        assertThat(second.proposalStatus()).isEqualTo(ProposalStatus.IN_REVIEW);
        assertThat(second.executionId()).isNotEqualTo(first.executionId());
        assertThat(second.questions()).filteredOn(question -> question.code().equals("PAGE_COUNT_MISSING"))
                .singleElement().satisfies(question -> {
                    assertThat(question.id()).isEqualTo(questionId);
                    assertThat(question.status()).isEqualTo(QuestionStatus.RESOLVED);
                });
        assertThat(approval.get(agencyId, first.proposalId()).isApproved()).isFalse();
        assertThat(requirements.countByBriefId(brief.getId())).isEqualTo(3);
        assertThat(documents.findByProposalIdOrderByTypeAsc(first.proposalId())).hasSize(3);
        transactions.executeWithoutResult(status -> {
            Proposal proposal = proposals.findById(first.proposalId()).orElseThrow();
            assertThat(proposal.getItems()).hasSize(3);
            assertThat(proposal.getSchedulePlan().getPhases()).hasSize(4);
        });
        assertThat(notifications.findByProposalIdOrderByIdAsc(first.proposalId())).hasSize(9);
    }

    @Test
    void failedPipelineLeavesDurableLogAndAnalyzingProposalWithoutBusinessRows() {
        Brief brief = fixture(RESTAURANT, false);
        assertThatThrownBy(() -> facade.processBrief(brief.getId())).isInstanceOf(PipelineFailedException.class);
        assertThat(executions.findByBriefIdOrderByIdAsc(brief.getId())).singleElement()
                .satisfies(execution -> assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.FAILED));
        Proposal proposal = proposals.findByBriefIdOrderByIdAsc(brief.getId()).get(0);
        assertThat(proposal.getStatus()).isEqualTo(ProposalStatus.ANALYZING);
        assertThat(requirements.countByBriefId(brief.getId())).isZero();
        assertThat(contracts.findByProposalId(proposal.getId())).isEmpty();
        assertThat(documents.findByProposalIdOrderByTypeAsc(proposal.getId())).isEmpty();
    }

    @Test
    void scopedEntryPointsRejectOtherAgencyBeforeWriting() {
        Brief brief = fixture(RESTAURANT, true);
        Long otherAgency = agencies.saveAndFlush(new Agency("Other " + UUID.randomUUID())).getId();
        assertThatThrownBy(() -> facade.processBrief(otherAgency, brief.getId()))
                .isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> facade.ingestAndProcess(otherAgency, BriefChannel.WEB_FORM,
                brief.getClient().getId(), Map.of("text", RESTAURANT))).isInstanceOf(NoSuchElementException.class);
        QuotationResult result = facade.processBrief(brief.getId());
        Long questionId = result.questions().get(0).id();
        assertThatThrownBy(() -> facade.answerQuestion(otherAgency, brief.getId(), questionId, "Three"))
                .isInstanceOf(NoSuchElementException.class);
        assertThat(questions.answers(brief.getId())).isEmpty();
        assertThat(executions.countByBriefId(brief.getId())).isEqualTo(1);
        assertThat(briefs.countByClientId(brief.getClient().getId())).isEqualTo(1);
    }

    @Test
    void sentProposalCannotBeReanalyzed() {
        Brief brief = fixture(RESTAURANT, true);
        Long agencyId = brief.getClient().getAgency().getId();
        QuotationResult result = facade.processBrief(brief.getId());
        approval.approve(agencyId, result.proposalId());
        approval.send(agencyId, result.proposalId());
        assertThatThrownBy(() -> facade.processBrief(brief.getId())).isInstanceOf(IllegalStateException.class)
                .hasMessage("Proposal " + result.proposalId() + " was already sent; it cannot be re-analyzed");
        assertThat(executions.countByBriefId(brief.getId())).isEqualTo(1);
    }

    @Test
    void scopedIngestionProcessesTheNewBriefInOneCall() {
        Brief fixture = fixture(RESTAURANT, true);
        Long agencyId = fixture.getClient().getAgency().getId();
        QuotationResult result = facade.ingestAndProcess(agencyId, BriefChannel.WEB_FORM,
                fixture.getClient().getId(), Map.of("description", RESTAURANT));
        assertThat(result.briefId()).isNotEqualTo(fixture.getId());
        assertThat(result.proposalStatus()).isEqualTo(ProposalStatus.IN_REVIEW);
        assertThat(briefs.findById(result.briefId()).orElseThrow().getRawText()).isEqualTo(RESTAURANT);
    }

    @Test
    void terminalProposalCreatesNewOfferAndKeepsRequirementsReferencedByTheOldOne() {
        Brief brief = fixture(RESTAURANT, true);
        Long agencyId = brief.getClient().getAgency().getId();
        QuotationResult first = facade.processBrief(brief.getId());
        approval.reject(agencyId, first.proposalId());
        QuotationResult second = facade.processBrief(brief.getId());
        assertThat(second.proposalId()).isNotEqualTo(first.proposalId());
        assertThat(second.proposalStatus()).isEqualTo(ProposalStatus.IN_REVIEW);
        assertThat(requirements.countByBriefId(brief.getId())).isEqualTo(6);
        transactions.executeWithoutResult(status -> {
            Proposal original = proposals.findById(first.proposalId()).orElseThrow();
            assertThat(original.getStatus()).isEqualTo(ProposalStatus.REJECTED);
            assertThat(original.getItems()).hasSize(3);
            assertThat(original.getTotal()).isEqualByComparingTo(first.total());
        });
    }

    @Test
    void failedBusinessWriteRestoresPreviousQuoteAndApprovalButKeepsTheRunLog() {
        Brief brief = fixture(RESTAURANT, true);
        Long agencyId = brief.getClient().getAgency().getId();
        QuotationResult first = facade.processBrief(brief.getId());
        approval.approve(agencyId, first.proposalId());
        List<Long> originalItemIds = transactions.execute(status -> proposals.findById(first.proposalId())
                .orElseThrow().getItems().stream().map(QuotedItem::getId).toList());
        var originalDocuments = documents.findByProposalIdOrderByTypeAsc(first.proposalId());
        Long originalScheduleId = transactions.execute(status -> proposals.findById(first.proposalId())
                .orElseThrow().getSchedulePlan().getId());
        PipelineContext context = new PipelineContext(brief, agencyId, brief.getClient().getAgency().getName(),
                brief.getClient().getName(), List.of());
        AgentRun run = pipeline.run(context);
        QuoteDraft draft = context.getDraft();
        context.setFindings(List.of());
        context.setDraft(new QuoteDraft(draft.projectType(), draft.requirements(), draft.quote(), draft.findings(),
                draft.summary(), Map.of(DocumentType.PROPOSAL, draft.documents().get(DocumentType.PROPOSAL)),
                draft.phasePlan()));
        assertThatThrownBy(() -> writer.applyOutcome(first.proposalId(), run))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Missing rendered PDF");
        transactions.executeWithoutResult(status -> {
            Proposal restored = proposals.findById(first.proposalId()).orElseThrow();
            assertThat(restored.getItems()).extracting(QuotedItem::getId).containsExactlyElementsOf(originalItemIds);
            assertThat(restored.getSchedulePlan().getId()).isEqualTo(originalScheduleId);
            assertThat(restored.getSchedulePlan().getPhases()).hasSize(4);
            assertThat(restored.getTotal()).isEqualByComparingTo(first.total());
            assertThat(restored.isApproved()).isTrue();
        });
        assertThat(questions.list(brief.getId())).hasSize(2);
        assertThat(requirements.countByBriefId(brief.getId())).isEqualTo(3);
        var restoredDocuments = documents.findByProposalIdOrderByTypeAsc(first.proposalId());
        for (int index = 0; index < restoredDocuments.size(); index++) {
            assertThat(restoredDocuments.get(index).getContent()).isEqualTo(originalDocuments.get(index).getContent());
        }
        assertThat(executions.findById(run.execution().getId()).orElseThrow().getStatus())
                .isEqualTo(ExecutionStatus.SUCCEEDED);
        assertThat(notifications.findByProposalIdOrderByIdAsc(first.proposalId())).hasSize(9);
    }

    private Long questionId(QuotationResult result, String code) {
        return result.questions().stream().filter(question -> question.code().equals(code))
                .findFirst().orElseThrow().id();
    }

    private Brief fixture(String text, boolean withRates) {
        String suffix = UUID.randomUUID().toString();
        Agency agency = agencies.saveAndFlush(new Agency("Facade " + suffix));
        Client client = clients.saveAndFlush(new Client(agency, "Client", suffix + "@example.com"));
        if (withRates) {
            Role role = new Role(agency, "Developer");
            role.addRate(100000);
            roles.saveAndFlush(role);
        }
        ServiceCatalog catalog = new ServiceCatalog(agency, "Desarrollo web");
        catalog.addRequirementType("Diseno responsive", "pagina, web, sitio", new BigDecimal("24.00"));
        catalog.addRequirementType("Menu digital", "menu, carta", new BigDecimal("12.00"));
        catalog.addRequirementType("Sistema de reservas", "reserva, reservas, agenda", new BigDecimal("32.00"));
        catalog.addRequirementType("Pasarela de pagos", "pago, pagos, checkout", new BigDecimal("20.00"));
        catalogs.saveAndFlush(catalog);
        return briefs.saveAndFlush(new Brief(client, BriefChannel.WEB_FORM, text, "{}", Instant.now()));
    }
}

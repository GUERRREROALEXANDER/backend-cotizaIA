package com.cotizaia.agent.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cotizaia.agent.AgentRun;
import com.cotizaia.agent.PipelineRunRecorder;
import com.cotizaia.agent.llm.LlmClient;
import com.cotizaia.agent.llm.LlmRequest;
import com.cotizaia.agent.llm.LlmResponse;
import com.cotizaia.agent.llm.LlmTask;
import com.cotizaia.agent.llm.PromptBuilder;
import com.cotizaia.agent.llm.StubLlmClient;
import com.cotizaia.document.DocumentFactory;
import com.cotizaia.domain.Agency;
import com.cotizaia.domain.AgentExecution;
import com.cotizaia.domain.AgentStep;
import com.cotizaia.domain.Brief;
import com.cotizaia.domain.BriefChannel;
import com.cotizaia.domain.Client;
import com.cotizaia.domain.ExecutionStatus;
import com.cotizaia.domain.Role;
import com.cotizaia.domain.ServiceCatalog;
import com.cotizaia.domain.StepStatus;
import com.cotizaia.pricing.PricingService;
import com.cotizaia.repository.AgencyRepository;
import com.cotizaia.repository.AgentExecutionRepository;
import com.cotizaia.repository.AgentStepRepository;
import com.cotizaia.repository.BriefRepository;
import com.cotizaia.repository.ClientRepository;
import com.cotizaia.repository.ExtractedRequirementRepository;
import com.cotizaia.repository.ProposalRepository;
import com.cotizaia.repository.RequirementTypeRepository;
import com.cotizaia.repository.RoleRepository;
import com.cotizaia.repository.ServiceCatalogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.pdf.PdfReader;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** Verifies that linked handlers and recorder commit complete logs without writing business rows. */
@SpringBootTest(properties = "llm.provider=stub")
@ActiveProfiles("test")
class AgentPipelineIntegrationTests {

    @Autowired
    private AgentPipeline pipeline;

    @Autowired
    private PipelineRunRecorder recorder;

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
    private AgentExecutionRepository executions;

    @Autowired
    private AgentStepRepository steps;

    @Autowired
    private ExtractedRequirementRepository extracted;

    @Autowired
    private ProposalRepository proposals;

    @Autowired
    private RequirementTypeRepository requirementTypes;

    @Autowired
    private PricingService pricing;

    @Autowired
    private DocumentFactory documentFactory;

    @Autowired
    private PipelineProperties properties;

    @Test
    void restaurantProducesFlaggedDraftAndThreeDocuments() throws Exception {
        PipelineContext context = context("Hola, tengo un restaurante y necesito una pagina web donde los clientes "
                + "vean el menu y puedan hacer reservas en linea. ¿Cuanto me sale?");
        AgentRun run = pipeline.run(context);
        List<AgentStep> timeline = steps.findByExecutionIdOrderByStepOrderAsc(run.execution().getId());
        assertThat(run.execution().getStatus()).isEqualTo(ExecutionStatus.SUCCEEDED);
        assertThat(run.result()).isSameAs(context);
        assertThat(timeline).extracting(AgentStep::getHandler).containsExactlyElementsOf(pipeline.handlerNames());
        assertThat(timeline.get(4).getOutputSummary()).startsWith("[FLAG]");
        assertThat(context.getDraft().requirements()).hasSize(3);
        assertThat(context.getDraft().requirements()).extracting(draft -> draft.getRequirementType().getName())
                .containsExactly("Diseno responsive", "Menu digital", "Sistema de reservas");
        assertThat(context.getDraft().quote().subtotal()).isPositive();
        assertThat(context.getDraft().findings()).extracting(AmbiguityFinding::code)
                .containsExactly("PAGE_COUNT_MISSING", "DEADLINE_MISSING");
        assertThat(context.getDraft().documents()).hasSize(3);
        assertThat(context.getDraft().findings()).noneMatch(AmbiguityFinding::blocking);
        for (byte[] bytes : context.getDraft().documents().values()) {
            PdfReader reader = new PdfReader(bytes);
            try {
                assertThat(reader.getNumberOfPages()).isPositive();
            } finally {
                reader.close();
            }
        }
    }

    @Test
    void vagueBriefHaltsButCommitsSevenSteps() {
        PipelineContext context = context("hola, necesito ayuda con algo");
        AgentRun run = pipeline.run(context);
        List<AgentStep> timeline = steps.findByExecutionIdOrderByStepOrderAsc(run.execution().getId());
        assertThat(run.execution().getStatus()).isEqualTo(ExecutionStatus.SUCCEEDED);
        assertThat(timeline).hasSize(7);
        assertThat(timeline.get(4).getOutputSummary()).startsWith("[HALT]");
        assertThat(timeline.get(5).getStatus()).isEqualTo(StepStatus.SKIPPED);
        assertThat(timeline.get(6).getStatus()).isEqualTo(StepStatus.SKIPPED);
        assertThat(context.getFindings()).extracting(AmbiguityFinding::code)
                .contains("PROJECT_TYPE_UNKNOWN", "NO_SCOPE");
        assertThat(context.getDocuments()).isEmpty();
        assertThat(context.getDraft()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = LlmTask.class, names = {"CLASSIFY", "SUMMARIZE"})
    void failureCommitsOnlyRunLog(LlmTask failingTask) {
        PipelineContext context = context("Necesito una web");
        long requirementsBefore = extracted.count();
        long proposalsBefore = proposals.count();
        LlmClient delegate = new StubLlmClient();
        LlmClient failing = new LlmClient() {
            @Override
            public LlmResponse complete(LlmRequest request) {
                if (request.task() == failingTask) {
                    throw new IllegalStateException("LLM unavailable");
                }
                return delegate.complete(request);
            }

            @Override
            public String provider() {
                return "failing";
            }
        };
        PromptBuilder prompts = new PromptBuilder();
        ObjectMapper mapper = new ObjectMapper();
        AgentPipeline failingPipeline = new AgentPipeline(new ClassifyHandler(failing, prompts, mapper),
                new ExtractHandler(failing, prompts, mapper, requirementTypes), new EstimateHandler(properties),
                new PriceHandler(pricing), new AmbiguityCheckHandler(new AmbiguityDetector(mapper)),
                new GenerateHandler(failing, prompts, documentFactory, properties), new ComposeHandler(), recorder);
        assertThatThrownBy(() -> failingPipeline.run(context))
                .isInstanceOf(PipelineRunRecorder.PipelineFailedException.class);
        AgentExecution execution = executions.findByBriefIdOrderByIdAsc(context.getBrief().getId()).get(0);
        assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.FAILED);
        List<AgentStep> timeline = steps.findByExecutionIdOrderByStepOrderAsc(execution.getId());
        assertThat(timeline).hasSize(failingTask == LlmTask.CLASSIFY ? 1 : 6);
        AgentStep failed = timeline.get(timeline.size() - 1);
        assertThat(failed.getStatus()).isEqualTo(StepStatus.FAILED);
        assertThat(failed.getHandler()).isEqualTo(failingTask == LlmTask.CLASSIFY ? "Classify" : "Generate");
        assertThat(context.getDocuments()).isEmpty();
        assertThat(context.getDraft()).isNull();
        assertThat(extracted.count()).isEqualTo(requirementsBefore);
        assertThat(proposals.count()).isEqualTo(proposalsBefore);
    }

    @Test
    void pricingFailureWithoutRatesStillCommitsTheFailedRunLog() {
        PipelineContext context = context("Necesito una pagina web con reservas", false);
        assertThatThrownBy(() -> pipeline.run(context))
                .isInstanceOf(PipelineRunRecorder.PipelineFailedException.class);
        AgentExecution execution = executions.findByBriefIdOrderByIdAsc(context.getBrief().getId()).get(0);
        assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(execution.getErrorMessage()).contains("no valid rates");
        List<AgentStep> timeline = steps.findByExecutionIdOrderByStepOrderAsc(execution.getId());
        assertThat(timeline.get(timeline.size() - 1).getHandler()).isEqualTo("Price");
        assertThat(timeline.get(timeline.size() - 1).getStatus()).isEqualTo(StepStatus.FAILED);
    }

    private PipelineContext context(String text) {
        return context(text, true);
    }

    private PipelineContext context(String text, boolean withRates) {
        String suffix = UUID.randomUUID().toString();
        Agency agency = agencies.saveAndFlush(new Agency("Pipeline " + suffix));
        Client client = clients.saveAndFlush(new Client(agency, "Client", suffix + "@example.com"));
        Brief brief = briefs.saveAndFlush(new Brief(client, BriefChannel.WEB_FORM, text, "{}", Instant.now()));
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
        return new PipelineContext(brief, agency.getId(), agency.getName(), client.getName(), List.of());
    }
}

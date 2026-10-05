package com.cotizaia.api;

import static com.cotizaia.api.TestTokens.forAgency;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cotizaia.agent.AgentRun;
import com.cotizaia.agent.PipelineRunRecorder;
import com.cotizaia.agent.PipelineStep;
import com.cotizaia.domain.Agency;
import com.cotizaia.domain.Brief;
import com.cotizaia.domain.BriefChannel;
import com.cotizaia.domain.Client;
import com.cotizaia.repository.AgencyRepository;
import com.cotizaia.repository.BriefRepository;
import com.cotizaia.repository.ClientRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * HTTP acceptance for issue #7: GET execution returns the ordered step timeline.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class AgentExecutionControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PipelineRunRecorder recorder;

    @Autowired
    private AgencyRepository agencyRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private BriefRepository briefRepository;

    private Brief brief;

    private Long agencyId;

    @BeforeEach
    void createBrief() {
        Agency agency = agencyRepository.saveAndFlush(new Agency("Agencia API Run"));
        agencyId = agency.getId();
        Client client = clientRepository.saveAndFlush(
                new Client(agency, "Restaurante API", "api@run.co"));
        brief = briefRepository.saveAndFlush(new Brief(
                client, BriefChannel.WHATSAPP, "Necesito una web", "{}", Instant.now()));
    }

    @Test
    void rejectsUnauthenticatedRequests() throws Exception {
        mockMvc.perform(get("/api/executions/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Authentication required"));
    }

    @Test
    void hidesOtherAgencyExecutions() throws Exception {
        AgentRun run = recorder.run(brief, List.of(step("ClassifyHandler", "Web")));
        Long otherAgencyId = agencyRepository.saveAndFlush(new Agency("Other agency")).getId();
        mockMvc.perform(get("/api/executions/{id}", run.execution().getId()).with(forAgency(otherAgencyId)))
                .andExpect(status().isNotFound());
    }

    @Test
    void returnsExecutionWithOrderedStepTimeline() throws Exception {
        AgentRun run = recorder.run(brief, List.of(
                step("ClassifyHandler", "Web"),
                step("ExtractHandler", "3 requirements"),
                step("GenerateHandler", "3 artifacts")));

        mockMvc.perform(get("/api/executions/{id}", run.execution().getId()).with(forAgency(agencyId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.briefId").value(brief.getId()))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.steps.length()").value(3))
                .andExpect(jsonPath("$.steps[0].stepOrder").value(0))
                .andExpect(jsonPath("$.steps[0].handler").value("ClassifyHandler"))
                .andExpect(jsonPath("$.steps[1].stepOrder").value(1))
                .andExpect(jsonPath("$.steps[1].handler").value("ExtractHandler"))
                .andExpect(jsonPath("$.steps[2].handler").value("GenerateHandler"));
    }

    @Test
    void returnsFailedExecutionWithItsSteps() throws Exception {
        PipelineStep boom = new PipelineStep() {
            @Override
            public String name() {
                return "PriceHandler";
            }

            @Override
            public Object execute(Object context) {
                throw new IllegalStateException("rate missing");
            }
        };
        Long executionId = null;
        try {
            recorder.run(brief, List.of(step("ClassifyHandler", "Web"), boom));
        } catch (PipelineRunRecorder.PipelineFailedException failure) {
            executionId = failure.getExecutionId();
        }
        assertThat(executionId).isNotNull();

        mockMvc.perform(get("/api/executions/{id}", executionId).with(forAgency(agencyId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.errorMessage").value("rate missing"))
                .andExpect(jsonPath("$.steps.length()").value(2))
                .andExpect(jsonPath("$.steps[1].handler").value("PriceHandler"))
                .andExpect(jsonPath("$.steps[1].status").value("FAILED"));
    }

    @Test
    void returnsNotFoundForUnknownExecution() throws Exception {
        mockMvc.perform(get("/api/executions/{id}", -1).with(forAgency(agencyId)))
                .andExpect(status().isNotFound());
    }

    private PipelineStep step(String name, String output) {
        return new PipelineStep() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public Object execute(Object context) {
                return output;
            }
        };
    }
}

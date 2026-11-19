package com.cotizaia.api;

import static com.cotizaia.api.TestTokens.forAgency;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cotizaia.agent.PipelineProgressRegistry;
import com.cotizaia.agent.PipelineRunRecorder;
import com.cotizaia.agent.PipelineStep;
import com.cotizaia.domain.Agency;
import com.cotizaia.domain.Brief;
import com.cotizaia.domain.BriefChannel;
import com.cotizaia.domain.Client;
import com.cotizaia.domain.StepStatus;
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
 * Spec 13 HTTP contract: GET /api/briefs/{id}/progress answers 200 with
 * {running, steps[{name, status}]} for the caller's own brief and 404 for a foreign
 * or unknown one, exactly like the other brief endpoints.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class BriefProgressControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PipelineRunRecorder recorder;

    @Autowired
    private PipelineProgressRegistry registry;

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
        Agency agency = agencyRepository.saveAndFlush(new Agency("Agencia Progress"));
        agencyId = agency.getId();
        Client client = clientRepository.saveAndFlush(
                new Client(agency, "Restaurante Progress", "progress@live.co"));
        brief = briefRepository.saveAndFlush(new Brief(
                client, BriefChannel.WHATSAPP, "Necesito una web", "{}", Instant.now()));
    }

    @Test
    void rejectsUnauthenticatedRequests() throws Exception {
        mockMvc.perform(get("/api/briefs/{id}/progress", brief.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void hidesBriefsOfAnotherAgency() throws Exception {
        registry.start(brief.getId());
        registry.step(brief.getId(), "Classify", StepStatus.SUCCEEDED);
        Long otherAgencyId = agencyRepository.saveAndFlush(new Agency("Otra agencia progress")).getId();

        mockMvc.perform(get("/api/briefs/{id}/progress", brief.getId()).with(forAgency(otherAgencyId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Brief not found: " + brief.getId()));
    }

    @Test
    void answersNotFoundForAnUnknownBrief() throws Exception {
        mockMvc.perform(get("/api/briefs/{id}/progress", -1).with(forAgency(agencyId)))
                .andExpect(status().isNotFound());
    }

    @Test
    void answersIdleShapeWhenNoRunIsRecorded() throws Exception {
        mockMvc.perform(get("/api/briefs/{id}/progress", brief.getId()).with(forAgency(agencyId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.running").value(false))
                .andExpect(jsonPath("$.steps.length()").value(0));
    }

    /**
     * The shape of a live run, published by the same bean the recorder writes to: no ids,
     * no summaries — handler name and status only.
     */
    @Test
    void returnsTheLiveStepsWithExactNamesAndStatuses() throws Exception {
        registry.start(brief.getId());
        registry.step(brief.getId(), "Classify", StepStatus.SUCCEEDED);
        registry.step(brief.getId(), "Extract", StepStatus.FAILED);

        String body = mockMvc.perform(
                        get("/api/briefs/{id}/progress", brief.getId()).with(forAgency(agencyId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.running").value(true))
                .andExpect(jsonPath("$.steps.length()").value(2))
                .andExpect(jsonPath("$.steps[0].name").value("Classify"))
                .andExpect(jsonPath("$.steps[0].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.steps[1].name").value("Extract"))
                .andExpect(jsonPath("$.steps[1].status").value("FAILED"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("briefId").doesNotContain("durationMs");
    }

    /** A completed run stays readable, with running=false, so the panel can settle. */
    @Test
    void returnsFinishedRunAfterTheRecorderCompletes() throws Exception {
        recorder.run(brief, List.of(step("Classify", "Web"), step("Price", "USD 1200")));

        mockMvc.perform(get("/api/briefs/{id}/progress", brief.getId()).with(forAgency(agencyId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.running").value(false))
                .andExpect(jsonPath("$.steps.length()").value(2))
                .andExpect(jsonPath("$.steps[0].name").value("Classify"))
                .andExpect(jsonPath("$.steps[1].name").value("Price"));
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

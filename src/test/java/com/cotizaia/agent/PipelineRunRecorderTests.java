package com.cotizaia.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cotizaia.domain.Agency;
import com.cotizaia.domain.AgentExecution;
import com.cotizaia.domain.AgentStep;
import com.cotizaia.domain.Brief;
import com.cotizaia.domain.BriefChannel;
import com.cotizaia.domain.Client;
import com.cotizaia.domain.ExecutionStatus;
import com.cotizaia.domain.StepStatus;
import com.cotizaia.repository.AgencyRepository;
import com.cotizaia.repository.AgentExecutionRepository;
import com.cotizaia.repository.AgentStepRepository;
import com.cotizaia.repository.BriefRepository;
import com.cotizaia.repository.ClientRepository;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Issue #7 acceptance: every pipeline run writes one execution + N step rows,
 * even on failure. The recorder commits its own transaction, so these tests run
 * without {@code @Transactional} to observe what is truly persisted.
 */
@SpringBootTest
@ActiveProfiles("test")
class PipelineRunRecorderTests {

    @Autowired
    private PipelineRunRecorder recorder;

    @Autowired
    private AgencyRepository agencyRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private BriefRepository briefRepository;

    @Autowired
    private AgentExecutionRepository agentExecutionRepository;

    @Autowired
    private AgentStepRepository agentStepRepository;

    private Brief brief;

    @BeforeEach
    void createBrief() {
        Agency agency = agencyRepository.saveAndFlush(new Agency("Agencia Recorder"));
        Client client = clientRepository.saveAndFlush(
                new Client(agency, "Restaurante Recorder", "recorder@run.co"));
        brief = briefRepository.saveAndFlush(new Brief(
                client, BriefChannel.EMAIL, "Necesito una web", "{}", Instant.now()));
    }

    @Test
    void successfulRunWritesOneExecutionAndNSteps() {
        AgentRun run = recorder.run(brief, List.of(
                step("ClassifyHandler", "Web"),
                step("ExtractHandler", "3 requirements"),
                step("PriceHandler", "USD 1200")));

        AgentExecution execution = agentExecutionRepository.findById(run.execution().getId()).orElseThrow();
        assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.SUCCEEDED);
        assertThat(execution.getFinishedAt()).isNotNull();
        assertThat(execution.getErrorMessage()).isNull();

        List<AgentStep> steps = agentStepRepository.findByExecutionIdOrderByStepOrderAsc(execution.getId());
        assertThat(steps).extracting(AgentStep::getHandler)
                .containsExactly("ClassifyHandler", "ExtractHandler", "PriceHandler");
        assertThat(steps).extracting(AgentStep::getStepOrder).containsExactly(0, 1, 2);
        assertThat(steps).allSatisfy(s -> assertThat(s.getStatus()).isEqualTo(StepStatus.SUCCEEDED));
    }

    @Test
    void failedRunStillWritesExecutionAndSteps() {
        PipelineStep boom = new PipelineStep() {
            @Override
            public String name() {
                return "EstimateHandler";
            }

            @Override
            public Object execute(Object context) {
                throw new IllegalStateException("LLM unavailable");
            }
        };

        assertThatThrownBy(() -> recorder.run(brief, List.of(
                step("ClassifyHandler", "Web"),
                step("ExtractHandler", "3 requirements"),
                boom)))
                .isInstanceOf(PipelineRunRecorder.PipelineFailedException.class);

        List<AgentExecution> executions = agentExecutionRepository.findByBriefIdOrderByIdAsc(brief.getId());
        assertThat(executions).hasSize(1);

        AgentExecution execution = executions.get(0);
        assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(execution.getFinishedAt()).isNotNull();
        assertThat(execution.getErrorMessage()).contains("LLM unavailable");

        // Two successful links plus the failing one are all in the log.
        List<AgentStep> steps = agentStepRepository.findByExecutionIdOrderByStepOrderAsc(execution.getId());
        assertThat(steps).extracting(AgentStep::getHandler)
                .containsExactly("ClassifyHandler", "ExtractHandler", "EstimateHandler");
        assertThat(steps.get(2).getStatus()).isEqualTo(StepStatus.FAILED);
        assertThat(steps.get(2).getExecution().getId()).isEqualTo(execution.getId());
    }

    @Test
    void recordsDurationForEachSuccessfulStep() {
        AtomicInteger calls = new AtomicInteger();
        PipelineStep counting = new PipelineStep() {
            @Override
            public String name() {
                return "SlowHandler";
            }

            @Override
            public Object execute(Object context) {
                calls.incrementAndGet();
                return "done";
            }
        };

        AgentRun run = recorder.run(brief, List.of(counting));

        assertThat(calls).hasValue(1);
        AgentStep step = agentStepRepository
                .findByExecutionIdOrderByStepOrderAsc(run.execution().getId())
                .get(0);
        assertThat(step.getDurationMs()).isNotNull().isGreaterThanOrEqualTo(0);
        assertThat(step.getOutputSummary()).isEqualTo("done");
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

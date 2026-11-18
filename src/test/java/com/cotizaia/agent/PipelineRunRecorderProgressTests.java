package com.cotizaia.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cotizaia.agent.pipeline.HandlerResult;
import com.cotizaia.agent.pipeline.PipelineContext;
import com.cotizaia.agent.pipeline.PipelineHandler;
import com.cotizaia.domain.Agency;
import com.cotizaia.domain.Brief;
import com.cotizaia.domain.BriefChannel;
import com.cotizaia.domain.Client;
import com.cotizaia.repository.AgencyRepository;
import com.cotizaia.repository.BriefRepository;
import com.cotizaia.repository.ClientRepository;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Spec 13: the recorder must publish progress as it goes, not only when its transaction
 * commits. The blocking step proves the difference — the assertion runs while the run is
 * still open, which is exactly what the durable log cannot offer.
 */
@SpringBootTest
@ActiveProfiles("test")
class PipelineRunRecorderProgressTests {

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

    @BeforeEach
    void createBrief() {
        Agency agency = agencyRepository.saveAndFlush(new Agency("Agencia Live View"));
        Client client = clientRepository.saveAndFlush(
                new Client(agency, "Cliente Live View", "live@view.co"));
        brief = briefRepository.saveAndFlush(new Brief(
                client, BriefChannel.EMAIL, "Necesito una tienda", "{}", Instant.now()));
    }

    @Test
    void blockingSecondStepShowsTheFirstStepWhileRunning() throws Exception {
        CountDownLatch secondStepEntered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        PipelineStep blocking = new PipelineStep() {
            @Override
            public String name() {
                return "Extract";
            }

            @Override
            public Object execute(Object context) {
                secondStepEntered.countDown();
                try {
                    release.await(20, TimeUnit.SECONDS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
                return "3 requirements";
            }
        };

        ExecutorService runner = Executors.newSingleThreadExecutor();
        try {
            Future<AgentRun> run = runner.submit(
                    () -> recorder.run(brief, List.of(step("Classify", "Web"), blocking)));
            assertThat(secondStepEntered.await(20, TimeUnit.SECONDS)).isTrue();

            PipelineProgressRegistry.Snapshot live = registry.snapshot(brief.getId());
            assertThat(live.running()).isTrue();
            assertThat(live.steps()).containsExactly(new PipelineProgressRegistry.Step("Classify", "SUCCEEDED"));

            release.countDown();
            run.get(30, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            runner.shutdownNow();
        }

        PipelineProgressRegistry.Snapshot finished = registry.snapshot(brief.getId());
        assertThat(finished.running()).isFalse();
        assertThat(finished.steps()).containsExactly(
                new PipelineProgressRegistry.Step("Classify", "SUCCEEDED"),
                new PipelineProgressRegistry.Step("Extract", "SUCCEEDED"));
    }

    @Test
    void failingRunPublishesTheFailingStepAndStopsRunning() {
        PipelineStep boom = new PipelineStep() {
            @Override
            public String name() {
                return "Estimate";
            }

            @Override
            public Object execute(Object context) {
                throw new IllegalStateException("LLM unavailable");
            }
        };

        assertThatThrownBy(() -> recorder.run(brief, List.of(step("Classify", "Web"), boom)))
                .isInstanceOf(PipelineRunRecorder.PipelineFailedException.class);

        PipelineProgressRegistry.Snapshot snapshot = registry.snapshot(brief.getId());
        assertThat(snapshot.running()).isFalse();
        assertThat(snapshot.steps()).containsExactly(
                new PipelineProgressRegistry.Step("Classify", "SUCCEEDED"),
                new PipelineProgressRegistry.Step("Estimate", "FAILED"));
    }

    /** A halted chain is a normal end: the run stops being "running" and skipped links are hidden. */
    @Test
    void haltedChainStopsRunningAndOmitsSkippedLinks() {
        PipelineHandler halt = handler("AmbiguityCheck", HandlerResult.halt("faltan datos"));
        PipelineHandler after = handler("Generate", HandlerResult.proceed("draft"));
        halt.linkWith(after);

        recorder.runChain(brief, halt, context());

        PipelineProgressRegistry.Snapshot snapshot = registry.snapshot(brief.getId());
        assertThat(snapshot.running()).isFalse();
        assertThat(snapshot.steps()).containsExactly(
                new PipelineProgressRegistry.Step("AmbiguityCheck", "SUCCEEDED"));
    }

    private PipelineContext context() {
        return new PipelineContext(brief, brief.getClient().getAgency().getId(), "Agencia Live View",
                "Cliente Live View", List.of());
    }

    private PipelineHandler handler(String name, HandlerResult result) {
        return new PipelineHandler() {
            @Override
            public String name() {
                return name;
            }

            @Override
            protected HandlerResult process(PipelineContext context) {
                return result;
            }
        };
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

package com.cotizaia.agent.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cotizaia.domain.Agency;
import com.cotizaia.domain.Brief;
import com.cotizaia.domain.BriefChannel;
import com.cotizaia.domain.Client;
import com.cotizaia.domain.StepStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Confirms successor forwarding and skipped logging after a deliberate halt. */
class PipelineHandlerTests {

    @Test
    void forwardsAndSkipsSuccessorAfterHalt() {
        PipelineHandler first = handler("First", HandlerResult.halt("question"));
        PipelineHandler second = handler("Second", HandlerResult.proceed("done"));
        first.linkWith(second);
        List<StepStatus> statuses = new ArrayList<>();
        PipelineContext context = context();
        first.handle(context, (name, input, output, duration, status) -> statuses.add(status));
        assertThat(statuses).containsExactly(StepStatus.SUCCEEDED, StepStatus.SKIPPED);
        assertThat(context.getHaltedBy()).isEqualTo("First");
    }

    @Test
    void forwardsOnContinue() {
        PipelineHandler first = handler("First", HandlerResult.proceed("done"));
        first.linkWith(handler("Second", HandlerResult.proceed("done")));
        List<String> names = new ArrayList<>();
        first.handle(context(), (name, input, output, duration, status) -> names.add(name));
        assertThat(names).containsExactly("First", "Second");
    }

    @Test
    void flagStillForwardsAndPrefixesOutput() {
        PipelineHandler first = handler("First", HandlerResult.flag("review"));
        first.linkWith(handler("Second", HandlerResult.proceed("done")));
        List<String> outputs = new ArrayList<>();
        first.handle(context(), (name, input, output, duration, status) -> outputs.add(output));
        assertThat(outputs).containsExactly("[FLAG] review", "done");
    }

    @Test
    void skippedHandlerDoesNotProcessContext() {
        PipelineContext context = context();
        context.halt("Earlier", "question");
        List<String> outputs = new ArrayList<>();
        failingHandler(new IllegalStateException("Must not run")).handle(context,
                (name, input, output, duration, status) -> {
                    outputs.add(output);
                    assertThat(status).isEqualTo(StepStatus.SKIPPED);
                });
        assertThat(outputs).containsExactly("skipped: halted by Earlier");
    }

    @Test
    void exceptionPropagatesUnchangedAndIdentifiesFailingHandler() {
        IllegalStateException failure = new IllegalStateException("Failed");
        PipelineContext context = context();
        PipelineHandler first = handler("First", HandlerResult.proceed("done"));
        first.linkWith(failingHandler(failure)).linkWith(handler("Last", HandlerResult.proceed("not reached")));
        List<String> names = new ArrayList<>();
        assertThatThrownBy(() -> first.handle(context,
                (name, input, output, duration, status) -> names.add(name))).isSameAs(failure);
        assertThat(names).containsExactly("First");
        assertThat(context.getCurrentHandler()).isEqualTo("Failing");
    }

    private PipelineHandler failingHandler(RuntimeException failure) {
        return new PipelineHandler() {
            @Override
            public String name() {
                return "Failing";
            }

            @Override
            protected HandlerResult process(PipelineContext context) {
                throw failure;
            }
        };
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

    private PipelineContext context() {
        Agency agency = new Agency("Agency");
        Client client = new Client(agency, "Client", "client@example.com");
        Brief brief = new Brief(client, BriefChannel.WEB_FORM, "web", "{}", Instant.now());
        return new PipelineContext(brief, 1L, "Agency", "Client", List.of());
    }
}

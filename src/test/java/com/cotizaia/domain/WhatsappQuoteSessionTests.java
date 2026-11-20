package com.cotizaia.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.cotizaia.domain.WhatsappQuoteSession.Step;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class WhatsappQuoteSessionTests {

    private static final Instant T0 = Instant.parse("2026-11-20T12:00:00Z");

    @Test
    void theStepsAdvanceAndResetClearsEverything() {
        WhatsappQuoteSession session = new WhatsappQuoteSession(1L, T0);
        session.askClientName(T0);
        session.askClientEmail("Panaderia", T0);
        session.askDescription(5L, "Panadería La Espiga", T0);
        session.askQuestion(7L, 9L, T0);

        assertThat(session.stepAt(T0)).isEqualTo(Step.QUESTION);
        assertThat(session.getClientName()).isEqualTo("Panadería La Espiga");
        assertThat(session.getClientId()).isEqualTo(5L);

        session.reset(T0);

        assertThat(session.getStep()).isEqualTo(Step.IDLE);
        assertThat(session.getClientName()).isNull();
        assertThat(session.getBriefId()).isNull();
        assertThat(session.getQuestionId()).isNull();
    }

    @Test
    void aChatLeftAloneStartsOver() {
        WhatsappQuoteSession session = new WhatsappQuoteSession(1L, T0);
        session.askClientName(T0);

        assertThat(session.stepAt(T0.plus(WhatsappQuoteSession.IDLE_TIMEOUT).minusSeconds(1)))
                .isEqualTo(Step.CLIENT_NAME);
        assertThat(session.stepAt(T0.plus(WhatsappQuoteSession.IDLE_TIMEOUT))).isEqualTo(Step.IDLE);
    }
}

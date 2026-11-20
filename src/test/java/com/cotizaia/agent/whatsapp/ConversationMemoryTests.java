package com.cotizaia.agent.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;

import com.cotizaia.agent.whatsapp.ConversationMemory.Line;
import com.cotizaia.agent.whatsapp.ConversationMemory.Speaker;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class ConversationMemoryTests {

    private static final String ANA = "+573001111111";
    private static final String LUIS = "+573002222222";

    private final MovableClock clock = new MovableClock();
    private final ConversationMemory memory = new ConversationMemory(clock);

    @Test
    void aNewChatHasNoHistory() {
        assertThat(memory.history(ANA)).isEmpty();
    }

    @Test
    void linesComeBackOldestFirst() {
        memory.add(ANA, Speaker.CLIENTE, "Hola");
        memory.add(ANA, Speaker.ASISTENTE, "¿Para qué negocio es la página?");

        assertThat(memory.history(ANA)).containsExactly(
                new Line(Speaker.CLIENTE, "Hola"),
                new Line(Speaker.ASISTENTE, "¿Para qué negocio es la página?"));
    }

    @Test
    void eachSenderHasTheirOwnChat() {
        memory.add(ANA, Speaker.CLIENTE, "Panadería");
        memory.add(LUIS, Speaker.CLIENTE, "Ferretería");

        assertThat(memory.history(ANA)).extracting(Line::text).containsExactly("Panadería");
        assertThat(memory.history(LUIS)).extracting(Line::text).containsExactly("Ferretería");
    }

    @Test
    void onlyTheLastTenExchangesAreKept() {
        for (int i = 1; i <= ConversationMemory.MAX_LINES + 5; i++) {
            memory.add(ANA, Speaker.CLIENTE, "mensaje " + i);
        }

        assertThat(memory.history(ANA)).hasSize(ConversationMemory.MAX_LINES)
                .first().extracting(Line::text).isEqualTo("mensaje 6");
    }

    @Test
    void aChatIsForgottenAfterThirtyIdleMinutes() {
        memory.add(ANA, Speaker.CLIENTE, "Hola");

        clock.advance(ConversationMemory.IDLE.minusSeconds(1));
        assertThat(memory.history(ANA)).hasSize(1);

        clock.advance(Duration.ofSeconds(1));
        assertThat(memory.history(ANA)).isEmpty();
    }

    @Test
    void aNewMessageKeepsTheChatAlive() {
        memory.add(ANA, Speaker.CLIENTE, "Hola");
        clock.advance(Duration.ofMinutes(20));
        memory.add(ANA, Speaker.CLIENTE, "¿Sigues ahí?");
        clock.advance(Duration.ofMinutes(20));

        assertThat(memory.history(ANA)).hasSize(2);
    }

    @Test
    void historyIsACopyThatCannotBeChanged() {
        memory.add(ANA, Speaker.CLIENTE, "Hola");
        var history = memory.history(ANA);
        memory.add(ANA, Speaker.CLIENTE, "Otra cosa");

        assertThat(history).hasSize(1);
    }

    private static final class MovableClock extends Clock {

        private Instant now = Instant.parse("2026-11-20T10:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}

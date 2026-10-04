package com.cotizaia.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cotizaia.domain.BriefChannel;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The registry is the extension seam: it is built from an arbitrary list of
 * {@link BriefSource} beans, so a new channel adapter is discovered without
 * editing the registry, service or controller.
 */
class BriefSourceRegistryTests {

    @Test
    void dispatchesEachChannelToItsAdapter() {
        BriefSourceRegistry registry = new BriefSourceRegistry(
                List.of(new EmailBriefAdapter(), new WhatsappBriefAdapter(), new WebFormBriefAdapter()));

        assertThat(registry.forChannel(BriefChannel.EMAIL)).isInstanceOf(EmailBriefAdapter.class);
        assertThat(registry.forChannel(BriefChannel.WHATSAPP)).isInstanceOf(WhatsappBriefAdapter.class);
        assertThat(registry.forChannel(BriefChannel.WEB_FORM)).isInstanceOf(WebFormBriefAdapter.class);
    }

    @Test
    void usesAnyAdapterThePortProvidesWithoutCodeChanges() {
        // A test double stands in for a brand-new adapter: the registry routes to
        // it because it depends on the port, not on any concrete channel class.
        BriefSource replacement = new StubEmailSource();
        BriefSourceRegistry registry = new BriefSourceRegistry(List.of(replacement));

        assertThat(registry.forChannel(BriefChannel.EMAIL)).isSameAs(replacement);
        assertThat(registry.forChannel(BriefChannel.EMAIL).normalize(Map.of("body", "x")).rawText())
                .isEqualTo("stubbed");
    }

    @Test
    void rejectsDuplicateAdapterForSameChannel() {
        assertThatThrownBy(() -> new BriefSourceRegistry(
                List.of(new EmailBriefAdapter(), new StubEmailSource())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate BriefSource");
    }

    @Test
    void failsLoudlyWhenChannelHasNoAdapter() {
        BriefSourceRegistry registry = new BriefSourceRegistry(List.of(new EmailBriefAdapter()));

        assertThatThrownBy(() -> registry.forChannel(BriefChannel.WHATSAPP))
                .isInstanceOf(UnsupportedChannelException.class)
                .hasMessageContaining("WHATSAPP");
    }

    /** Minimal {@link BriefSource} used to prove the port is the only dependency. */
    private static final class StubEmailSource implements BriefSource {

        @Override
        public BriefChannel channel() {
            return BriefChannel.EMAIL;
        }

        @Override
        public NormalizedBrief normalize(Map<String, Object> payload) {
            return new NormalizedBrief("stubbed");
        }
    }
}

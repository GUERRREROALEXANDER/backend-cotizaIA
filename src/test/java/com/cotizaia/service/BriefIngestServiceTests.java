package com.cotizaia.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cotizaia.domain.Agency;
import com.cotizaia.domain.Brief;
import com.cotizaia.domain.BriefChannel;
import com.cotizaia.domain.Client;
import com.cotizaia.repository.AgencyRepository;
import com.cotizaia.repository.BriefRepository;
import com.cotizaia.repository.ClientRepository;
import java.util.Map;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service-level acceptance for issue #5: different channel payloads produce an
 * identical normalized Brief, the raw payload is kept for audit, and no
 * channel-specific branching exists in the pipeline.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BriefIngestServiceTests {

    @Autowired
    private BriefService briefService;

    @Autowired
    private BriefRepository briefRepository;

    @Autowired
    private AgencyRepository agencyRepository;

    @Autowired
    private ClientRepository clientRepository;

    private Client client;

    @BeforeEach
    void createClient() {
        Agency agency = agencyRepository.saveAndFlush(new Agency("Agencia Ingest"));
        client = clientRepository.saveAndFlush(
                new Client(agency, "Restaurante El Sabor", "contacto@elsabor.co"));
    }

    @Test
    void differentChannelPayloadsProduceAnIdenticalNormalizedBrief() {
        Brief fromEmail = briefService.ingest(BriefChannel.EMAIL, client.getId(), Map.of(
                "subject", "Cotizacion web",
                "from", "cliente@elsabor.co",
                "body", "Necesito una pagina web para mi restaurante"));
        Brief fromWhatsapp = briefService.ingest(BriefChannel.WHATSAPP, client.getId(), Map.of(
                "from", "+573001112233",
                "message", "Necesito una pagina web para mi restaurante"));
        Brief fromForm = briefService.ingest(BriefChannel.WEB_FORM, client.getId(), Map.of(
                "projectType", "Web",
                "budget", "3000000",
                "description", "Necesito una pagina web para mi restaurante"));

        // Identical normalized text from three different payload shapes...
        assertThat(fromEmail.getRawText()).isEqualTo(fromWhatsapp.getRawText());
        assertThat(fromForm.getRawText()).isEqualTo("Web: " + fromWhatsapp.getRawText());

        // ...each tagged with its own channel.
        assertThat(fromEmail.getSource()).isEqualTo(BriefChannel.EMAIL);
        assertThat(fromWhatsapp.getSource()).isEqualTo(BriefChannel.WHATSAPP);
        assertThat(fromForm.getSource()).isEqualTo(BriefChannel.WEB_FORM);
        assertThat(briefRepository.findByClientIdOrderByIdAsc(client.getId())).hasSize(3);
    }

    @Test
    void storesTheOriginalPayloadVerbatimForAudit() {
        Brief brief = briefService.ingest(BriefChannel.EMAIL, client.getId(), Map.of(
                "subject", "Cotizacion web",
                "from", "cliente@elsabor.co",
                "body", "  Hola,   necesito  un logo  "));

        // Normalized text is collapsed...
        assertThat(brief.getRawText()).isEqualTo("Hola, necesito un logo");
        // ...but the audit payload keeps the original spacing and every field.
        assertThat(brief.getRawPayload())
                .contains("subject")
                .contains("Cotizacion web")
                .contains("cliente@elsabor.co")
                .contains("  Hola,   necesito  un logo  ");
    }

    @Test
    void linksTheBriefToItsClientAndStampsReceivedAt() {
        Brief brief = briefService.ingest(
                BriefChannel.WEB_FORM, client.getId(), Map.of("description", "Landing page"));

        assertThat(brief.getClient().getId()).isEqualTo(client.getId());
        assertThat(brief.getReceivedAt()).isNotNull();
    }

    @Test
    void rejectsUnknownClient() {
        assertThatThrownBy(() -> briefService.ingest(
                BriefChannel.EMAIL, -1L, Map.of("body", "texto")))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void rejectsPayloadMissingTheChannelRequiredField() {
        assertThatThrownBy(() -> briefService.ingest(
                BriefChannel.WHATSAPP, client.getId(), Map.of("from", "+57")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("message");
    }
}

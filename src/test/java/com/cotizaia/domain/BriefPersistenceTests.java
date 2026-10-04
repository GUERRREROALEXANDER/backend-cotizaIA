package com.cotizaia.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cotizaia.repository.AgencyRepository;
import com.cotizaia.repository.BriefRepository;
import com.cotizaia.repository.ClientRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BriefPersistenceTests {

    @Autowired
    private AgencyRepository agencyRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private BriefRepository briefRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void v5CreatesBriefsTable() {
        Integer applied = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '5' AND success = TRUE",
                Integer.class);
        assertThat(applied).isEqualTo(1);

        Integer table = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables"
                        + " WHERE LOWER(table_name) = 'briefs' AND LOWER(table_schema) = 'public'",
                Integer.class);
        assertThat(table).isEqualTo(1);
    }

    @Test
    void persistsBriefWithSourceRawTextAuditPayloadAndClient() {
        Agency agency = agencyRepository.saveAndFlush(new Agency("Agencia Brief"));
        Client client = clientRepository.saveAndFlush(
                new Client(agency, "Restaurante El Sabor", "contacto@elsabor.co"));
        Instant receivedAt = Instant.parse("2026-02-01T10:15:30Z");

        Brief brief = briefRepository.saveAndFlush(new Brief(
                client,
                BriefChannel.WHATSAPP,
                "Necesito una pagina web para mi restaurante",
                "{\"message\":\"Necesito una pagina web para mi restaurante\"}",
                receivedAt));

        assertThat(brief.getId()).isNotNull();
        Brief reloaded = briefRepository.findById(brief.getId()).orElseThrow();
        assertThat(reloaded.getSource()).isEqualTo(BriefChannel.WHATSAPP);
        assertThat(reloaded.getRawText()).isEqualTo("Necesito una pagina web para mi restaurante");
        assertThat(reloaded.getRawPayload()).contains("message");
        assertThat(reloaded.getReceivedAt()).isEqualTo(receivedAt);
        assertThat(reloaded.getClient().getId()).isEqualTo(client.getId());
    }

    @Test
    void scopesBriefLookupByClientAgency() {
        Agency agency = agencyRepository.saveAndFlush(new Agency("Agencia Scope Brief"));
        Client client = clientRepository.saveAndFlush(
                new Client(agency, "Cliente Uno", "uno@scope.co"));
        Brief brief = briefRepository.saveAndFlush(new Brief(
                client, BriefChannel.EMAIL, "texto", "{}", Instant.now()));

        assertThat(briefRepository.findByIdAndClientAgencyId(brief.getId(), agency.getId())).isPresent();
        assertThat(briefRepository.findByIdAndClientAgencyId(brief.getId(), -1L)).isEmpty();
        assertThat(briefRepository.findByClientIdOrderByIdAsc(client.getId())).hasSize(1);
    }

    @Test
    void rejectsBriefWithUnknownClientByForeignKey() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO briefs (client_id, source, raw_text, raw_payload, received_at)"
                        + " VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)",
                -1L, "EMAIL", "texto", "{}"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsUnknownSourceByCheckConstraint() {
        Agency agency = agencyRepository.saveAndFlush(new Agency("Agencia Check Brief"));
        Client client = clientRepository.saveAndFlush(
                new Client(agency, "Cliente Check", "check@brief.co"));

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO briefs (client_id, source, raw_text, raw_payload, received_at)"
                        + " VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)",
                client.getId(), "SMOKE_SIGNAL", "texto", "{}"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}

package com.cotizaia.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cotizaia.repository.AgencyRepository;
import com.cotizaia.repository.BriefRepository;
import com.cotizaia.repository.ClientRepository;
import com.cotizaia.repository.ExtractedRequirementRepository;
import com.cotizaia.repository.ServiceCatalogRepository;
import java.math.BigDecimal;
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
class ExtractedRequirementPersistenceTests {

    @Autowired
    private AgencyRepository agencyRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private BriefRepository briefRepository;

    @Autowired
    private ServiceCatalogRepository catalogRepository;

    @Autowired
    private ExtractedRequirementRepository extractedRequirementRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void v6CreatesExtractedRequirementsTable() {
        Integer applied = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '6' AND success = TRUE",
                Integer.class);
        assertThat(applied).isEqualTo(1);

        Integer table = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables"
                        + " WHERE LOWER(table_name) = 'extracted_requirements' AND LOWER(table_schema) = 'public'",
                Integer.class);
        assertThat(table).isEqualTo(1);
    }

    @Test
    void persistsRequirementLinkedToBriefAndCatalogType() {
        Agency agency = agencyRepository.saveAndFlush(new Agency("Agencia Extraccion"));
        Client client = clientRepository.saveAndFlush(
                new Client(agency, "Restaurante El Sabor", "contacto@elsabor.co"));
        Brief brief = briefRepository.saveAndFlush(new Brief(
                client,
                BriefChannel.WHATSAPP,
                "Necesito una pagina con menu y reservas",
                "{\"message\":\"Necesito una pagina con menu y reservas\"}",
                Instant.parse("2026-02-01T10:15:30Z")));

        ServiceCatalog catalog = new ServiceCatalog(agency, "Restaurant website");
        catalog.addRequirementType("Online reservations", "Booking flow", new BigDecimal("24.00"));
        catalog = catalogRepository.saveAndFlush(catalog);
        RequirementType type = catalog.getRequirementTypes().get(0);

        ExtractedRequirement saved = extractedRequirementRepository.saveAndFlush(
                new ExtractedRequirement(
                        brief, type, "Reservas en linea con confirmacion", new BigDecimal("20.00"),
                        new BigDecimal("0.9200")));

        assertThat(saved.getId()).isNotNull();
        ExtractedRequirement reloaded = extractedRequirementRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getBrief().getId()).isEqualTo(brief.getId());
        assertThat(reloaded.getRequirementType().getId()).isEqualTo(type.getId());
        assertThat(reloaded.getDescription()).isEqualTo("Reservas en linea con confirmacion");
        assertThat(reloaded.getEstimatedHours()).isEqualByComparingTo("20.00");
        assertThat(reloaded.getConfidence()).isEqualByComparingTo("0.9200");
        assertThat(reloaded.isAmbiguityFlag()).isFalse();
    }

    @Test
    void autoFlagsLowConfidenceRequirementAsAmbiguous() {
        Fixture fixture = fixture("Agencia Ambigua", "0.55");

        assertThat(fixture.requirement().isAmbiguityFlag()).isTrue();

        ExtractedRequirement reloaded =
                extractedRequirementRepository.findById(fixture.requirement().getId()).orElseThrow();
        assertThat(reloaded.isAmbiguityFlag()).isTrue();
    }

    @Test
    void doesNotFlagConfidenceAtOrAboveThreshold() {
        Fixture atThreshold = fixture("Agencia Limite", "0.60");
        assertThat(atThreshold.requirement().isAmbiguityFlag())
                .as("0.60 is the inclusive floor and stays unambiguous")
                .isFalse();
    }

    @Test
    void scopesRequirementsToTheBriefsAgency() {
        Fixture fixture = fixture("Agencia Duena", "0.90");

        assertThat(extractedRequirementRepository
                        .findByBriefIdAndBriefClientAgencyIdOrderByIdAsc(
                                fixture.brief().getId(), fixture.agency().getId()))
                .hasSize(1);
        assertThat(extractedRequirementRepository
                        .findByBriefIdAndBriefClientAgencyIdOrderByIdAsc(
                                fixture.brief().getId(), -1L))
                .isEmpty();
    }

    @Test
    void exposesOnlyAmbiguousRequirementsForApproval() {
        Agency agency = agencyRepository.saveAndFlush(new Agency("Agencia Aprobacion"));
        Client client = clientRepository.saveAndFlush(new Client(agency, "Cliente Mixto", "mixto@aprobacion.co"));
        Brief brief = briefRepository.saveAndFlush(new Brief(
                client, BriefChannel.EMAIL, "texto", "{}", Instant.now()));
        ServiceCatalog catalog = new ServiceCatalog(agency, "Web");
        catalog.addRequirementType("Login", null, null);
        catalog.addRequirementType("Payments", null, null);
        catalog = catalogRepository.saveAndFlush(catalog);
        RequirementType login = catalog.getRequirementTypes().get(0);
        RequirementType payments = catalog.getRequirementTypes().get(1);

        extractedRequirementRepository.saveAndFlush(new ExtractedRequirement(
                brief, login, "Login con email", null, new BigDecimal("0.90")));
        extractedRequirementRepository.saveAndFlush(new ExtractedRequirement(
                brief, payments, "Pasarela de pago", null, new BigDecimal("0.40")));

        assertThat(extractedRequirementRepository.findByBriefIdAndAmbiguityFlagTrueOrderByIdAsc(brief.getId()))
                .extracting(ExtractedRequirement::getDescription)
                .containsExactly("Pasarela de pago");
        assertThat(extractedRequirementRepository.countByBriefId(brief.getId())).isEqualTo(2);
        assertThat(extractedRequirementRepository.countByBriefIdAndAmbiguityFlagTrue(brief.getId())).isEqualTo(1);
    }

    @Test
    void rejectsRequirementWithUnknownBriefByForeignKey() {
        Agency agency = agencyRepository.saveAndFlush(new Agency("Agencia FK Brief"));
        ServiceCatalog catalog = catalogRepository.saveAndFlush(new ServiceCatalog(agency, "Web"));
        catalog.addRequirementType("Login", null, null);
        catalogRepository.saveAndFlush(catalog);
        Long typeId = catalog.getRequirementTypes().get(0).getId();

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO extracted_requirements"
                        + " (brief_id, requirement_type_id, description, estimated_hours, confidence,"
                        + " ambiguity_flag, created_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)",
                -1L, typeId, "Fantasma", null, new BigDecimal("0.9"), false))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsRequirementWithUnknownCatalogTypeByForeignKey() {
        Agency agency = agencyRepository.saveAndFlush(new Agency("Agencia FK Tipo"));
        Client client = clientRepository.saveAndFlush(new Client(agency, "Cliente FK", "fk@tipo.co"));
        Brief brief = briefRepository.saveAndFlush(new Brief(
                client, BriefChannel.WEB_FORM, "texto", "{}", Instant.now()));

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO extracted_requirements"
                        + " (brief_id, requirement_type_id, description, estimated_hours, confidence,"
                        + " ambiguity_flag, created_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)",
                brief.getId(), -1L, "Fantasma", null, new BigDecimal("0.9"), false))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsConfidenceOutsideZeroToOneByCheckConstraint() {
        Fixture fixture = fixture("Agencia Rango", "0.90");

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO extracted_requirements"
                        + " (brief_id, requirement_type_id, description, estimated_hours, confidence,"
                        + " ambiguity_flag, created_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)",
                fixture.brief().getId(), fixture.type().getId(), "Fuera de rango", null,
                new BigDecimal("1.5000"), false))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void constructorRejectsInvalidInput() {
        Fixture fixture = fixture("Agencia Validacion", "0.90");

        assertThatThrownBy(() -> new ExtractedRequirement(
                fixture.brief(), fixture.type(), " ", null, new BigDecimal("0.90")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ExtractedRequirement(
                fixture.brief(), fixture.type(), "Login", null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ExtractedRequirement(
                fixture.brief(), fixture.type(), "Login", null, new BigDecimal("1.50")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Fixture fixture(String agencyName, String confidence) {
        Agency agency = agencyRepository.saveAndFlush(new Agency(agencyName));
        Client client = clientRepository.saveAndFlush(
                new Client(agency, "Cliente " + agencyName, agencyName + "@test.co"));
        Brief brief = briefRepository.saveAndFlush(new Brief(
                client, BriefChannel.EMAIL, "texto", "{}", Instant.now()));
        ServiceCatalog catalog = new ServiceCatalog(agency, "Web " + agencyName);
        catalog.addRequirementType("Login", null, null);
        catalog = catalogRepository.saveAndFlush(catalog);
        RequirementType type = catalog.getRequirementTypes().get(0);
        ExtractedRequirement requirement = extractedRequirementRepository.saveAndFlush(
                new ExtractedRequirement(brief, type, "Login con email", null, new BigDecimal(confidence)));
        return new Fixture(agency, brief, type, requirement);
    }

    private record Fixture(
            Agency agency, Brief brief, RequirementType type, ExtractedRequirement requirement) {
    }
}

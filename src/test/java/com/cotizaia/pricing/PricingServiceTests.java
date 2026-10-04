package com.cotizaia.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cotizaia.config.RateConfiguration;
import com.cotizaia.domain.Agency;
import com.cotizaia.domain.Brief;
import com.cotizaia.domain.BriefChannel;
import com.cotizaia.domain.Client;
import com.cotizaia.domain.Proposal;
import com.cotizaia.domain.Role;
import com.cotizaia.repository.AgencyRepository;
import com.cotizaia.repository.BriefRepository;
import com.cotizaia.repository.ClientRepository;
import com.cotizaia.repository.ProposalRepository;
import com.cotizaia.repository.RoleRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PricingServiceTests {

    @Autowired
    private AgencyRepository agencyRepository;

    @Autowired
    private BriefRepository briefRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PricingService pricingService;

    @Autowired
    private ProposalRepository proposalRepository;

    @Autowired
    private RoleRepository roleRepository;

    @BeforeEach
    void clearRateCache() {
        RateConfiguration.getInstance().clear();
    }

    @Test
    void agencyDefaultSwitchesWithoutChangingTheCaller() {
        Agency agency = agencyRepository.saveAndFlush(new Agency("Pricing agency"));
        Role designer = new Role(agency, "Designer");
        designer.addRate(80_000L);
        roleRepository.saveAndFlush(designer);
        Role developer = new Role(agency, "Developer");
        developer.addRate(120_000L);
        roleRepository.saveAndFlush(developer);
        List<PricingLine> lines = lines();

        assertThat(pricingService.blendedRate(agency.getId(), Instant.now())).isEqualTo(100_000L);
        assertThat(pricingService.quote(agency.getId(), null, lines).subtotal())
                .isEqualByComparingTo("7000000.00");
        agency.setPricingModel(PricingModel.FIXED);
        assertThat(pricingService.quote(agency.getId(), null, lines).subtotal())
                .isEqualByComparingTo("8400000.00");
        assertThat(pricingService.quote(agency.getId(), PricingModel.PHASED, lines).subtotal())
                .isEqualByComparingTo("7700000.00");

        Client client = clientRepository.saveAndFlush(new Client(agency, "Client", "pricing@example.com"));
        Brief brief = briefRepository.saveAndFlush(new Brief(
                client, BriefChannel.WEB_FORM, "Need a website", "{}", Instant.now()));
        Proposal proposal = new Proposal.Builder().brief(brief).pricingModel(PricingModel.PHASED).build();
        proposalRepository.saveAndFlush(proposal);
        Long proposalId = proposal.getId();
        Long agencyId = agency.getId();
        entityManager.clear();

        assertThat(agencyRepository.findById(agencyId).orElseThrow().getPricingModel())
                .isEqualTo(PricingModel.FIXED);
        assertThat(proposalRepository.findById(proposalId).orElseThrow().getPricingModel())
                .isEqualTo(PricingModel.PHASED);
    }

    @Test
    void rejectsAgencyWithoutCurrentRates() {
        Agency agency = agencyRepository.saveAndFlush(new Agency("No rates"));
        assertThatThrownBy(() -> pricingService.quote(agency.getId(), null, lines()))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("Agency " + agency.getId() + " has no valid rates configured");
    }

    private List<PricingLine> lines() {
        return List.of(new PricingLine("a", "Discovery", new BigDecimal("40")),
                new PricingLine("b", "Build", new BigDecimal("20")),
                new PricingLine("c", "QA", new BigDecimal("10")));
    }
}

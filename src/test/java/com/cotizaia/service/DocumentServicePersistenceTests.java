package com.cotizaia.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.cotizaia.document.DocumentType;
import com.cotizaia.domain.Agency;
import com.cotizaia.domain.Brief;
import com.cotizaia.domain.BriefChannel;
import com.cotizaia.domain.Client;
import com.cotizaia.domain.ExtractedRequirement;
import com.cotizaia.domain.Proposal;
import com.cotizaia.domain.ProposalDocument;
import com.cotizaia.domain.ServiceCatalog;
import com.cotizaia.repository.AgencyRepository;
import com.cotizaia.repository.BriefRepository;
import com.cotizaia.repository.ClientRepository;
import com.cotizaia.repository.ExtractedRequirementRepository;
import com.cotizaia.repository.ProposalDocumentRepository;
import com.cotizaia.repository.ProposalRepository;
import com.cotizaia.repository.ServiceCatalogRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** Verifies BYTEA storage, proposal linkage and in-place regeneration through the application service. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DocumentServicePersistenceTests {

    @Autowired
    private AgencyRepository agencies;

    @Autowired
    private ClientRepository clients;

    @Autowired
    private BriefRepository briefs;

    @Autowired
    private ServiceCatalogRepository catalogs;

    @Autowired
    private ExtractedRequirementRepository requirements;

    @Autowired
    private ProposalRepository proposals;

    @Autowired
    private ProposalDocumentRepository documents;

    @Autowired
    private DocumentService service;

    @Test
    void generatesAndRegeneratesThreeLinkedDocuments() {
        Agency agency = agencies.saveAndFlush(new Agency("Agencia Demo"));
        Client client = clients.saveAndFlush(new Client(agency, "Restaurante El Sabor", "sabor@example.co"));
        Brief brief = briefs.saveAndFlush(new Brief(client, BriefChannel.WEB_FORM,
                "Sistema de reservas", "{}", Instant.now()));
        ServiceCatalog catalog = new ServiceCatalog(agency, "Web");
        catalog.addRequirementType("Reservations", "Booking", new BigDecimal("24.00"));
        catalog = catalogs.saveAndFlush(catalog);
        ExtractedRequirement requirement = requirements.saveAndFlush(new ExtractedRequirement(brief,
                catalog.getRequirementTypes().get(0), "Reservas", new BigDecimal("20.00"),
                new BigDecimal("0.9200")));
        Proposal proposal = new Proposal.Builder().brief(brief)
                .addItem(requirement, new BigDecimal("20"), new BigDecimal("100")).build();
        proposal.attachSchedule(new BigDecimal("40")).addPhase("Diseno", BigDecimal.ONE);
        proposal = proposals.saveAndFlush(proposal);
        Long proposalId = proposal.getId();

        List<ProposalDocument> first = service.generateFor(proposalId, "Primera version");
        assertThat(first).hasSize(3);
        assertThat(first).allSatisfy(document -> {
            assertThat(document.getProposal().getId()).isEqualTo(proposalId);
            assertThat(document.getContentType()).isEqualTo("application/pdf");
            assertThat(document.getSizeBytes()).isPositive();
        });
        Instant previous = first.get(0).getGeneratedAt();
        List<ProposalDocument> second = service.generateFor(proposalId, "Segunda version");
        documents.flush();
        assertThat(second).hasSize(3);
        assertThat(documents.findByProposalIdOrderByTypeAsc(proposalId)).hasSize(3);
        assertThat(second.get(0).getGeneratedAt()).isAfter(previous);
        assertThat(service.get(proposalId, DocumentType.PROPOSAL).getContent()).startsWith((byte) '%');
    }
}

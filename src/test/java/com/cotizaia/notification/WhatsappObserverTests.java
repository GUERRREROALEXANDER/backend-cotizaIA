package com.cotizaia.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.cotizaia.domain.Agency;
import com.cotizaia.domain.AppUser;
import com.cotizaia.domain.Brief;
import com.cotizaia.domain.BriefChannel;
import com.cotizaia.domain.Client;
import com.cotizaia.domain.Proposal;
import com.cotizaia.domain.ProposalStatus;
import com.cotizaia.domain.UserRole;
import com.cotizaia.domain.WhatsappContact;
import com.cotizaia.domain.WhatsappMessage;
import com.cotizaia.domain.state.ProposalEvent;
import com.cotizaia.repository.AgencyRepository;
import com.cotizaia.repository.AppUserRepository;
import com.cotizaia.repository.BriefRepository;
import com.cotizaia.repository.ClientRepository;
import com.cotizaia.repository.ProposalRepository;
import com.cotizaia.repository.WhatsappContactRepository;
import com.cotizaia.repository.WhatsappMessageRepository;
import com.cotizaia.service.ProposalWorkflowService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * The WhatsApp observer joined dispatch without touching the Subject: a state move the owner acts on queues one
 * message to their verified number, in the same transaction, and nothing goes out when they switched it off.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class WhatsappObserverTests {

    @Autowired
    private AgencyRepository agencies;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private BriefRepository briefs;

    @Autowired
    private ClientRepository clients;

    @Autowired
    private ProposalRepository proposals;

    @Autowired
    private WhatsappContactRepository contacts;

    @Autowired
    private WhatsappMessageRepository messages;

    @Autowired
    private ProposalWorkflowService workflow;

    @Autowired
    private ProposalSubject subject;

    @Test
    void theObserverIsRegisteredWithTheSubject() {
        assertThat(subject.observers()).extracting(ProposalObserver::channel).contains("WHATSAPP");
    }

    @Test
    void reviewReadyProposalMessagesTheOwnersVerifiedNumber() {
        AppUser owner = ownerOf("WhatsApp Agency");
        contacts.saveAndFlush(new WhatsappContact(owner.getId(), "+573001112233", Instant.now()));
        Proposal proposal = quotedProposal(owner.getAgency(), "Panaderia La Espiga");
        long before = messages.count();

        workflow.transition(proposal.getId(), ProposalEvent.SUBMIT_FOR_REVIEW);

        List<WhatsappMessage> queued = messages.findAll().stream()
                .filter(message -> message.getPhone().equals("+573001112233")).toList();
        assertThat(messages.count()).isEqualTo(before + 1);
        assertThat(queued).singleElement().satisfies(message -> {
            assertThat(message.getStatus()).isEqualTo(WhatsappMessage.Status.PENDING);
            assertThat(message.getBody()).contains("#" + proposal.getId(), "Panaderia La Espiga",
                    "lista para revisar", "Total:");
        });
    }

    @Test
    void nothingIsQueuedWhenTheOwnerSwitchedNoticesOff() {
        AppUser owner = ownerOf("Quiet Agency");
        WhatsappContact contact = new WhatsappContact(owner.getId(), "+573004445566", Instant.now());
        contact.setEnabled(false);
        contacts.saveAndFlush(contact);
        Proposal proposal = quotedProposal(owner.getAgency(), "Cliente");
        long before = messages.count();

        workflow.transition(proposal.getId(), ProposalEvent.SUBMIT_FOR_REVIEW);

        assertThat(messages.count()).isEqualTo(before);
    }

    @Test
    void nothingIsQueuedWithoutAVerifiedNumber() {
        AppUser owner = ownerOf("No Phone Agency");
        Proposal proposal = quotedProposal(owner.getAgency(), "Cliente");
        long before = messages.count();

        workflow.transition(proposal.getId(), ProposalEvent.SUBMIT_FOR_REVIEW);

        assertThat(messages.count()).isEqualTo(before);
    }

    @Test
    void onlyMovesTheOwnerActsOnHaveAText() {
        ProposalStateChangedEvent analyzing = event(ProposalStatus.RECEIVED, ProposalStatus.ANALYZING);
        ProposalStateChangedEvent accepted = event(ProposalStatus.SENT, ProposalStatus.ACCEPTED);

        assertThat(WhatsappObserver.text(analyzing)).isEmpty();
        assertThat(WhatsappObserver.text(accepted)).hasValueSatisfying(text ->
                assertThat(text).contains("aceptó", "#7", "Cliente", "2.000.000"));
    }

    private static ProposalStateChangedEvent event(ProposalStatus from, ProposalStatus to) {
        return new ProposalStateChangedEvent(7L, 1L, 1L, "Cliente", from, to, Instant.now(),
                new BigDecimal("2000000"));
    }

    private AppUser ownerOf(String agencyName) {
        Agency agency = agencies.saveAndFlush(new Agency(agencyName));
        return users.saveAndFlush(new AppUser(agency, "Owner", "owner@" + agencyName.replace(" ", "") + ".co",
                UserRole.OWNER));
    }

    private Proposal quotedProposal(Agency agency, String clientName) {
        Client client = clients.saveAndFlush(new Client(agency, clientName, "client@agency.co"));
        Brief brief = briefs.saveAndFlush(new Brief(client, BriefChannel.WEB_FORM, "A website", "{}", Instant.now()));
        return proposals.saveAndFlush(new Proposal.Builder().brief(brief).status(ProposalStatus.QUOTED).build());
    }
}

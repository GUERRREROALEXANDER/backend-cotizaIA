package com.cotizaia.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cotizaia.domain.state.InvalidStateTransitionException;
import com.cotizaia.repository.AgencyRepository;
import com.cotizaia.repository.BriefRepository;
import com.cotizaia.repository.ClientRepository;
import com.cotizaia.repository.ProposalRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** Verifies that State decisions leave an ordered audit trail (project.txt section 6 pattern 9). */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProposalStatusHistoryPersistenceTests {
    @Autowired
    private AgencyRepository agencies;

    @Autowired
    private ClientRepository clients;

    @Autowired
    private BriefRepository briefs;

    @Autowired
    private ProposalRepository proposals;

    @Autowired
    private EntityManager entityManager;

    @Test
    void persistsFiveChangesAndRejectsIllegalMoveWithoutMutation() {
        Agency agency = agencies.saveAndFlush(new Agency("History Agency"));
        Client client = clients.saveAndFlush(new Client(agency, "History Client", "history@example.co"));
        Brief brief = briefs.saveAndFlush(new Brief(client, BriefChannel.WEB_FORM,
                "A website", "{}", Instant.now()));
        Proposal proposal = proposals.saveAndFlush(new Proposal.Builder().brief(brief).build());
        Long id = proposal.getId();

        for (ProposalStatus target : new ProposalStatus[] {ProposalStatus.ANALYZING,
                ProposalStatus.QUOTED, ProposalStatus.IN_REVIEW, ProposalStatus.SENT, ProposalStatus.ACCEPTED}) {
            proposal.transitionTo(target);
        }
        entityManager.flush();
        entityManager.clear();

        Proposal reloaded = proposals.findById(id).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ProposalStatus.ACCEPTED);
        assertThat(reloaded.getStatusHistory()).hasSize(5);
        ProposalStatus[] sequence = {ProposalStatus.RECEIVED, ProposalStatus.ANALYZING,
                ProposalStatus.QUOTED, ProposalStatus.IN_REVIEW, ProposalStatus.SENT, ProposalStatus.ACCEPTED};
        for (int index = 0; index < 5; index++) {
            ProposalStatusChange change = reloaded.getStatusHistory().get(index);
            assertThat(change.getFromStatus()).isEqualTo(sequence[index]);
            assertThat(change.getToStatus()).isEqualTo(sequence[index + 1]);
            assertThat(change.getChangedAt()).isNotNull();
        }
        assertThatThrownBy(() -> reloaded.transitionTo(ProposalStatus.REJECTED))
                .isInstanceOf(InvalidStateTransitionException.class);
        assertThat(reloaded.getStatus()).isEqualTo(ProposalStatus.ACCEPTED);
        assertThat(reloaded.getStatusHistory()).hasSize(5);
    }
}

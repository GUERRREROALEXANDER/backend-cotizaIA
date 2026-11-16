package com.cotizaia.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.cotizaia.agent.knowledge.KnowledgeService;
import com.cotizaia.domain.Proposal;
import com.cotizaia.domain.ProposalStatus;
import com.cotizaia.service.AuthService;
import com.cotizaia.service.ClientService;
import com.cotizaia.service.ProposalService;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Loads the packaged demo agency end to end (stub LLM, H2) and checks it is created once. Uses its own in-memory
 * database so the demo agency never leaks into the shared test database.
 */
@SpringBootTest(properties = {
        "demo.seed=true",
        "spring.datasource.url=jdbc:h2:mem:demo-seed;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;"
                + "DEFAULT_NULL_ORDERING=HIGH"})
@ActiveProfiles("test")
class DemoSeederTests {

    @Autowired
    private DemoSeeder seeder;

    @Autowired
    private AuthService auth;

    @Autowired
    private ClientService clients;

    @Autowired
    private KnowledgeService knowledge;

    @Autowired
    private ProposalService proposals;

    @Test
    void loadsTheDemoAgencyOnceThroughTheServices() {
        // The startup listener may have loaded it already; seed() is synchronized, so this waits for it.
        seeder.seed();
        assertThat(seeder.seed()).as("second run finds the agency and does nothing").isFalse();

        Long agencyId = auth.login("demo@estudio-narino.test", "CotizaIA-demo-2026").agencyId();
        assertThat(clients.list(agencyId)).hasSize(30);
        // 24 past proposals, plus the demo proposals the app adds to memory as they are sent or accepted.
        assertThat(knowledge.list(agencyId)).hasSizeGreaterThanOrEqualTo(24);

        List<Proposal> all = proposals.list(agencyId, null);
        assertThat(all).hasSizeGreaterThanOrEqualTo(10);
        Set<ProposalStatus> statuses = all.stream().map(Proposal::getStatus).collect(Collectors.toSet());
        assertThat(statuses).contains(ProposalStatus.SENT, ProposalStatus.NEGOTIATING, ProposalStatus.REJECTED);
    }
}

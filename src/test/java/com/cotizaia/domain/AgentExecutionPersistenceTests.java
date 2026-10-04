package com.cotizaia.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cotizaia.repository.AgencyRepository;
import com.cotizaia.repository.AgentExecutionRepository;
import com.cotizaia.repository.AgentStepRepository;
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

/**
 * Persistence acceptance for issue #7: the two run-log tables exist and the
 * aggregate writes one execution with its ordered steps.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AgentExecutionPersistenceTests {

    @Autowired
    private AgencyRepository agencyRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private BriefRepository briefRepository;

    @Autowired
    private AgentExecutionRepository agentExecutionRepository;

    @Autowired
    private AgentStepRepository agentStepRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void v7CreatesRunLogTables() {
        Integer applied = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '7' AND success = TRUE",
                Integer.class);
        assertThat(applied).isEqualTo(1);

        for (String table : new String[] {"agent_executions", "agent_steps"}) {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables"
                            + " WHERE LOWER(table_name) = LOWER(?) AND LOWER(table_schema) = 'public'",
                    Integer.class,
                    table);
            assertThat(count).as("table %s exists", table).isEqualTo(1);
        }
    }

    @Test
    void persistsExecutionWithOrderedSteps() {
        Brief brief = brief("Agencia Run Log");

        AgentExecution execution = new AgentExecution(brief, Instant.parse("2026-02-01T10:00:00Z"));
        execution.addStep("ClassifyHandler", "brief", "Web", 12L, StepStatus.SUCCEEDED);
        execution.addStep("ExtractHandler", "Web", "3 requirements", 40L, StepStatus.SUCCEEDED);
        execution.succeed(Instant.parse("2026-02-01T10:00:01Z"));
        AgentExecution saved = agentExecutionRepository.saveAndFlush(execution);

        assertThat(saved.getId()).isNotNull();
        assertThat(agentStepRepository.findByExecutionIdOrderByStepOrderAsc(saved.getId()))
                .extracting(AgentStep::getHandler)
                .containsExactly("ClassifyHandler", "ExtractHandler");
        assertThat(agentStepRepository.countByExecutionId(saved.getId())).isEqualTo(2);
    }

    @Test
    void derivesGaplessStepOrderFromChainPosition() {
        AgentExecution execution = new AgentExecution(brief("Agencia Orden"), Instant.now());
        AgentStep first = execution.addStep("A", null, null, null, StepStatus.SUCCEEDED);
        AgentStep second = execution.addStep("B", null, null, null, StepStatus.SKIPPED);

        assertThat(first.getStepOrder()).isZero();
        assertThat(second.getStepOrder()).isEqualTo(1);
    }

    @Test
    void scopesExecutionLookupByBriefAgency() {
        Brief brief = brief("Agencia Scope Run");
        AgentExecution execution = agentExecutionRepository.saveAndFlush(
                new AgentExecution(brief, Instant.now()));

        assertThat(agentExecutionRepository
                        .findByIdAndBriefClientAgencyId(execution.getId(), brief.getClient().getAgency().getId()))
                .isPresent();
        assertThat(agentExecutionRepository.findByIdAndBriefClientAgencyId(execution.getId(), -1L))
                .isEmpty();
    }

    @Test
    void rejectsASecondStepWithTheSameOrder() {
        AgentExecution execution = agentExecutionRepository.saveAndFlush(
                new AgentExecution(brief("Agencia Unico"), Instant.now()));

        jdbcTemplate.update(
                "INSERT INTO agent_steps (execution_id, handler, step_order, status)"
                        + " VALUES (?, ?, 0, 'SUCCEEDED')",
                execution.getId(), "First");
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO agent_steps (execution_id, handler, step_order, status)"
                        + " VALUES (?, ?, 0, 'SUCCEEDED')",
                execution.getId(), "Second"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsUnknownExecutionForStepByForeignKey() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO agent_steps (execution_id, handler, step_order, status)"
                        + " VALUES (?, ?, 0, 'SUCCEEDED')",
                -1L, "Orphan"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsUnknownBriefForExecutionByForeignKey() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO agent_executions (brief_id, status, started_at)"
                        + " VALUES (?, 'RUNNING', CURRENT_TIMESTAMP)",
                -1L))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsUnknownStatusByCheckConstraint() {
        Brief brief = brief("Agencia Check Run");

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO agent_executions (brief_id, status, started_at)"
                        + " VALUES (?, 'EXPLODED', CURRENT_TIMESTAMP)",
                brief.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsFinishedRunWithoutFinishTimeByCheckConstraint() {
        Brief brief = brief("Agencia Check Finish");

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO agent_executions (brief_id, status, started_at, finished_at)"
                        + " VALUES (?, 'SUCCEEDED', CURRENT_TIMESTAMP, NULL)",
                brief.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void domainRejectsDoubleCloseAndBlankFailure() {
        AgentExecution execution = new AgentExecution(brief("Agencia Cierre"), Instant.now());
        execution.succeed(Instant.now());

        assertThatThrownBy(() -> execution.succeed(Instant.now()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new AgentExecution(brief("Agencia Falla"), Instant.now())
                        .fail(" ", Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Brief brief(String agencyName) {
        Agency agency = agencyRepository.saveAndFlush(new Agency(agencyName));
        Client client = clientRepository.saveAndFlush(
                new Client(agency, "Cliente " + agencyName, agencyName + "@run.co"));
        return briefRepository.saveAndFlush(new Brief(
                client, BriefChannel.EMAIL, "Necesito una web", "{}", Instant.now()));
    }
}

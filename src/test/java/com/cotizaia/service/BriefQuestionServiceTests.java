package com.cotizaia.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cotizaia.domain.Agency;
import com.cotizaia.domain.Brief;
import com.cotizaia.domain.BriefChannel;
import com.cotizaia.domain.BriefQuestion;
import com.cotizaia.domain.Client;
import com.cotizaia.domain.QuestionStatus;
import com.cotizaia.repository.AgencyRepository;
import com.cotizaia.repository.BriefQuestionRepository;
import com.cotizaia.repository.BriefRepository;
import com.cotizaia.repository.ClientRepository;
import com.cotizaia.service.BriefQuestionService.QuestionSpec;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
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
class BriefQuestionServiceTests {

    @Autowired
    private BriefQuestionService service;

    @Autowired
    private BriefQuestionRepository questions;

    @Autowired
    private AgencyRepository agencies;

    @Autowired
    private ClientRepository clients;

    @Autowired
    private BriefRepository briefs;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void syncCreatesRefreshesDeletesOpenAndPreservesResolved() {
        Brief brief = brief();
        List<BriefQuestion> initial = service.sync(brief, List.of(
                new QuestionSpec("KEEP", "Keep?", true), new QuestionSpec("DROP", "Drop?", false),
                new QuestionSpec("ANSWER", "Answer?", true)));
        assertThat(initial).hasSize(3);
        Long keptId = initial.get(0).getId();
        BriefQuestion answered = service.resolve(brief.getId(), initial.get(2).getId(), "Yes");
        List<BriefQuestion> synced = service.sync(brief, List.of(
                new QuestionSpec("KEEP", "Updated?", false), new QuestionSpec("NEW", "New?", false),
                new QuestionSpec("ANSWER", "Must not change", false)));
        assertThat(synced).extracting(BriefQuestion::getCode).containsExactly("KEEP", "ANSWER", "NEW");
        assertThat(synced.get(0).getId()).isEqualTo(keptId);
        assertThat(synced.get(0).getQuestion()).isEqualTo("Updated?");
        assertThat(synced.get(0).isBlocking()).isFalse();
        assertThat(answered.getQuestion()).isEqualTo("Answer?");
        assertThat(answered.getStatus()).isEqualTo(QuestionStatus.RESOLVED);
        assertThat(service.answers(brief.getId())).containsExactly(
                new BriefQuestionService.ResolvedAnswer("ANSWER", "Answer?", "Yes"));
        assertThat(service.sync(brief, List.of())).extracting(BriefQuestion::getCode).containsExactly("ANSWER");
        questions.flush();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM brief_questions WHERE brief_id = ? "
                + "AND status = 'RESOLVED' AND answer IS NOT NULL AND resolved_at IS NOT NULL",
                Integer.class, brief.getId())).isEqualTo(1);
    }

    @Test
    void resolveRejectsAnotherBrief() {
        Brief brief = brief();
        BriefQuestion question = service.sync(brief, List.of(new QuestionSpec("X", "Question?", true))).get(0);
        assertThatThrownBy(() -> service.resolve(brief().getId(), question.getId(), "Answer"))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void resolveRejectsBlankAnswer() {
        Brief brief = brief();
        BriefQuestion question = service.sync(brief, List.of(new QuestionSpec("X", "Question?", true))).get(0);
        assertThatThrownBy(() -> service.resolve(brief.getId(), question.getId(), " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void databaseRejectsOpenWithAnswer() {
        Brief brief = brief();
        BriefQuestion question = service.sync(brief, List.of(new QuestionSpec("X", "Question?", true))).get(0);
        questions.flush();
        assertThat(question.getCreatedAt()).isNotNull();
        assertThatThrownBy(() -> jdbc.update("UPDATE brief_questions SET answer = 'invalid' WHERE id = ?",
                question.getId())).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsResolvedWithoutTimestamp() {
        Brief brief = brief();
        BriefQuestion question = service.sync(brief, List.of(new QuestionSpec("X", "Question?", true))).get(0);
        questions.flush();
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE brief_questions SET status = 'RESOLVED', answer = 'answer' WHERE id = ?",
                question.getId())).isInstanceOf(DataIntegrityViolationException.class);
    }

    private Brief brief() {
        String suffix = UUID.randomUUID().toString();
        Agency agency = agencies.save(new Agency("Questions " + suffix));
        Client client = clients.save(new Client(agency, "Client", suffix + "@example.com"));
        return briefs.saveAndFlush(new Brief(client, BriefChannel.WEB_FORM, "Need help", "{}", Instant.now()));
    }
}

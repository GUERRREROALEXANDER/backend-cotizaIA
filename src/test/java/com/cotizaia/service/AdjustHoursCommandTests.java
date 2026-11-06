package com.cotizaia.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cotizaia.domain.BriefFixture;
import com.cotizaia.domain.ExtractedRequirement;
import com.cotizaia.domain.Proposal;
import com.cotizaia.domain.ProposalStatus;
import com.cotizaia.service.command.AdjustHoursCommand;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** Command: an hour edit is an object that can be executed, undone and described for the audit trail. */
class AdjustHoursCommandTests {

    @Test
    void executeAndUndoAreSymmetricEvenThoughEditsReplaceTheLine() {
        Proposal proposal = new Proposal.Builder().brief(BriefFixture.brief()).status(ProposalStatus.IN_REVIEW).build();
        ExtractedRequirement booking = BriefFixture.requirement();
        ReflectionTestUtils.setField(booking, "id", 42L);
        proposal.addItem(booking, new BigDecimal("32"), new BigDecimal("100"));
        proposal.approve(java.time.Instant.now());

        AdjustHoursCommand command = new AdjustHoursCommand(42L, new BigDecimal("32"), new BigDecimal("50"));
        command.execute(proposal);
        assertThat(proposal.getItems().get(0).getHours()).isEqualByComparingTo("50");
        assertThat(proposal.isApproved()).isFalse();

        command.undo(proposal);
        assertThat(proposal.getItems().get(0).getHours()).isEqualByComparingTo("32");
        assertThat(proposal.getItems().get(0).getRequirement()).isSameAs(booking);

        assertThat(command.type()).isEqualTo("ADJUST_HOURS");
        assertThat(command.description()).contains("32").contains("50");
        assertThat(command.payloadJson()).contains("42");
    }

    @Test
    void failsLoudlyWhenTheLineIsGone() {
        Proposal proposal = new Proposal.Builder().brief(BriefFixture.brief()).status(ProposalStatus.IN_REVIEW).build();
        AdjustHoursCommand command = new AdjustHoursCommand(7L, BigDecimal.ONE, BigDecimal.TEN);
        assertThatThrownBy(() -> command.execute(proposal)).isInstanceOf(IllegalStateException.class);
    }
}

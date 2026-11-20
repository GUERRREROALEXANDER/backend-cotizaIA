package com.cotizaia.domain.spec;

import com.cotizaia.domain.Proposal;
import com.cotizaia.domain.ProposalStatus;
import com.cotizaia.domain.QuotedItem;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * The business rules a proposal is judged by before a human approves it. Each rule is a small {@link Specification};
 * {@link #readyForApproval(Instant)} composes them, and {@link #recommended()} lists the advisory ones.
 */
public final class ProposalSpecifications {

    private ProposalSpecifications() {
    }

    public static Specification<Proposal> inReview() {
        return proposal -> proposal.getStatus() == ProposalStatus.IN_REVIEW;
    }

    public static Specification<Proposal> approved() {
        return Proposal::isApproved;
    }

    public static Specification<Proposal> hasQuotedItems() {
        return proposal -> !proposal.getItems().isEmpty();
    }

    public static Specification<Proposal> allHoursPositive() {
        return proposal -> proposal.getItems().stream().map(QuotedItem::getHours)
                .allMatch(hours -> hours != null && hours.signum() > 0);
    }

    public static Specification<Proposal> positiveTotal() {
        return proposal -> {
            BigDecimal total = proposal.getTotal();
            return total != null && total.signum() > 0;
        };
    }

    public static Specification<Proposal> hasNoExpiryDate() {
        return proposal -> proposal.getValidUntil() == null;
    }

    public static Specification<Proposal> validAfter(Instant now) {
        return proposal -> proposal.getValidUntil() != null && proposal.getValidUntil().isAfter(now);
    }

    public static Specification<Proposal> hasDraftedText() {
        return proposal -> proposal.getDraftedContent() != null && !proposal.getDraftedContent().isBlank();
    }

    /** Rules that must all hold before approval, in the order a reviewer would fix them. */
    public static List<NamedRule<Proposal>> required(Instant now) {
        return List.of(
                new NamedRule<>("IN_REVIEW", "The proposal is waiting in the review queue", inReview()),
                new NamedRule<>("NOT_YET_APPROVED", "The proposal has not been approved yet", approved().not()),
                new NamedRule<>("HAS_ITEMS", "At least one requirement is quoted", hasQuotedItems()),
                new NamedRule<>("POSITIVE_HOURS", "Every quoted item has more than zero hours", allHoursPositive()),
                new NamedRule<>("POSITIVE_TOTAL", "The total price is greater than zero", positiveTotal()),
                new NamedRule<>("STILL_VALID", "The offer has not passed its validity date",
                        hasNoExpiryDate().or(validAfter(now))));
    }

    /** Advisory rules: a failure is worth a warning but does not block approval. */
    public static List<NamedRule<Proposal>> recommended() {
        return List.of(new NamedRule<>("HAS_DRAFT", "The proposal has drafted text for the client document",
                hasDraftedText()));
    }

    public static Specification<Proposal> readyForApproval(Instant now) {
        return Specification.allOf(required(now));
    }
}

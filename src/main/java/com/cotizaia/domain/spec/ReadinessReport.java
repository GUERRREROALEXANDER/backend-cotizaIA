package com.cotizaia.domain.spec;

import com.cotizaia.domain.Proposal;
import java.time.Instant;
import java.util.List;

/** Outcome of judging a proposal against {@link ProposalSpecifications}: ready or not, and which rules failed. */
public record ReadinessReport(boolean ready, List<RuleResult> required, List<RuleResult> recommended) {

    public record RuleResult(String code, String description, boolean passed) {
    }

    public static ReadinessReport evaluate(Proposal proposal, Instant now) {
        List<RuleResult> required = judge(ProposalSpecifications.required(now), proposal);
        List<RuleResult> recommended = judge(ProposalSpecifications.recommended(), proposal);
        return new ReadinessReport(ProposalSpecifications.readyForApproval(now).isSatisfiedBy(proposal), required,
                recommended);
    }

    private static List<RuleResult> judge(List<NamedRule<Proposal>> rules, Proposal proposal) {
        return rules.stream()
                .map(rule -> new RuleResult(rule.code(), rule.description(), rule.isSatisfiedBy(proposal)))
                .toList();
    }
}

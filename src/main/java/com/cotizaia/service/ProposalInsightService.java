package com.cotizaia.service;

import com.cotizaia.document.DocumentNode;
import com.cotizaia.document.DocumentStats;
import com.cotizaia.document.DocumentStatsVisitor;
import com.cotizaia.document.DraftDocumentBuilder;
import com.cotizaia.document.MarkdownExportVisitor;
import com.cotizaia.document.ProposalDraft;
import com.cotizaia.domain.Proposal;
import com.cotizaia.domain.spec.ReadinessReport;
import java.time.Instant;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only views over one agency proposal: the drafted text exported to Markdown and measured (Visitor over the
 * document Composite), and an approval checklist (Specification). Nothing here changes the proposal.
 */
@Service
@Transactional(readOnly = true)
public class ProposalInsightService {

    private final ApprovalQueueService approval;

    public ProposalInsightService(ApprovalQueueService approval) {
        this.approval = approval;
    }

    public String draftMarkdown(Long agencyId, Long proposalId) {
        return MarkdownExportVisitor.export(draftTree(agencyId, proposalId));
    }

    public DocumentStats draftStats(Long agencyId, Long proposalId) {
        return DocumentStatsVisitor.measure(draftTree(agencyId, proposalId));
    }

    public ReadinessReport readiness(Long agencyId, Long proposalId) {
        return ReadinessReport.evaluate(approval.get(agencyId, proposalId), Instant.now());
    }

    private DocumentNode draftTree(Long agencyId, Long proposalId) {
        Proposal proposal = approval.get(agencyId, proposalId);
        ProposalDraft draft;
        try {
            draft = ProposalDraft.fromJson(proposal.getDraftedContent());
        } catch (IllegalArgumentException unreadable) {
            draft = null;
        }
        if (draft == null) {
            throw new NoSuchElementException("Proposal " + proposalId + " has no drafted text");
        }
        return DraftDocumentBuilder.build(draft);
    }
}

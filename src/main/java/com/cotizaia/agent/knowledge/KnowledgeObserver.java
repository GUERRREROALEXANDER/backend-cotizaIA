package com.cotizaia.agent.knowledge;

import com.cotizaia.agent.pipeline.EvidenceQueryBuilder;
import com.cotizaia.domain.KnowledgeDocument;
import com.cotizaia.domain.KnowledgeDocumentKind;
import com.cotizaia.domain.Proposal;
import com.cotizaia.domain.ProposalStatus;
import com.cotizaia.domain.QuotedItem;
import com.cotizaia.notification.ProposalObserver;
import com.cotizaia.notification.ProposalStateChangedEvent;
import com.cotizaia.repository.KnowledgeDocumentRepository;
import com.cotizaia.repository.ProposalRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * ConcreteObserver that turns a human-approved and sent proposal into agency memory (spec 04).
 *
 * <p>Trust boundary: only items a human corrected and sent are learned; a mere draft never pollutes memory.
 * The write happens after the surrounding transaction commits and in its own REQUIRES_NEW transaction, so the
 * send is already durable when learning starts. Every failure is logged and swallowed: memory is an enhancement
 * and must never block or roll back a send. This observer writes no notification rows.
 */
@Component
public class KnowledgeObserver implements ProposalObserver {

    private static final Logger LOGGER = LoggerFactory.getLogger(KnowledgeObserver.class);

    private final ProposalRepository proposals;

    private final KnowledgeDocumentRepository documents;

    private final VectorStore store;

    private final ApprovedProposalIngestor ingestor;

    private final EvidenceQueryBuilder queries;

    private final TransactionTemplate transaction;

    public KnowledgeObserver(ProposalRepository proposals, KnowledgeDocumentRepository documents, VectorStore store,
            ApprovedProposalIngestor ingestor, EvidenceQueryBuilder queries, PlatformTransactionManager transactions) {
        this.proposals = proposals;
        this.documents = documents;
        this.store = store;
        this.ingestor = ingestor;
        this.queries = queries;
        this.transaction = new TransactionTemplate(transactions);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public String channel() {
        return "MEMORY";
    }

    @Override
    public void onStateChanged(ProposalStateChangedEvent event) {
        if (event.to() != ProposalStatus.SENT) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    learn(event);
                }
            });
        } else {
            learn(event);
        }
    }

    private void learn(ProposalStateChangedEvent event) {
        try {
            transaction.executeWithoutResult(status -> learnNow(event));
        } catch (RuntimeException exception) {
            LOGGER.warn("Could not learn proposal {} into memory; the send stays committed", event.proposalId(),
                    exception);
        }
    }

    private void learnNow(ProposalStateChangedEvent event) {
        Proposal proposal = proposals.findById(event.proposalId()).orElse(null);
        if (proposal == null) {
            return;
        }
        for (KnowledgeDocument older : documents.findByAgencyIdAndSourceProposalId(event.agencyId(),
                event.proposalId())) {
            store.deleteDocument(event.agencyId(), older.getId());
            documents.delete(older);
        }
        List<ApprovedItem> approved = proposal.getItems().stream()
                .map(item -> approved(item, proposal))
                .filter(item -> item != null)
                .toList();
        if (approved.isEmpty()) {
            return;
        }
        IngestionSource source = new IngestionSource(event.agencyId(),
                "Propuesta #" + event.proposalId() + " (aprobada)", KnowledgeDocumentKind.APPROVED_PROPOSAL,
                null, event.proposalId(), List.of(), approved);
        ingestor.ingest(source);
    }

    private ApprovedItem approved(QuotedItem item, Proposal proposal) {
        if (item == null || item.getRequirement() == null || item.getHours() == null
                || item.getRequirement().getRequirementType() == null) {
            return null;
        }
        String requirementType = item.getRequirement().getRequirementType().getName();
        String text = queries.query(proposal.getBrief().getRawText(), item.getRequirement().getRequirementType());
        return new ApprovedItem(requirementType, text, item.getHours());
    }
}

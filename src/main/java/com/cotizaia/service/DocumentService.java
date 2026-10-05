package com.cotizaia.service;

import com.cotizaia.document.DocumentData;
import com.cotizaia.document.DocumentFactory;
import com.cotizaia.document.DocumentType;
import com.cotizaia.domain.DefaultContractClauses;
import com.cotizaia.domain.Proposal;
import com.cotizaia.domain.ProposalDocument;
import com.cotizaia.repository.ContractRepository;
import com.cotizaia.repository.ProposalDocumentRepository;
import com.cotizaia.repository.ProposalRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Orchestrates PDF rendering and regeneration for a proposal (project.txt section 6). */
@Service
public class DocumentService {

    private final ProposalRepository proposals;
    private final ContractRepository contracts;
    private final ProposalDocumentRepository documents;
    private final DocumentFactory factory;

    public DocumentService(ProposalRepository proposals, ContractRepository contracts,
            ProposalDocumentRepository documents, DocumentFactory factory) {
        this.proposals = proposals;
        this.contracts = contracts;
        this.documents = documents;
        this.factory = factory;
    }

    @Transactional
    public List<ProposalDocument> generateFor(Long proposalId, String summary) {
        Proposal proposal = proposals.findById(proposalId)
                .orElseThrow(() -> new NoSuchElementException("Proposal not found: " + proposalId));
        List<String> clauses = contracts.findByProposalId(proposalId)
                .map(contract -> contract.getClauses().stream().map(clause -> clause.getText()).toList())
                .orElse(DefaultContractClauses.DEFAULT_CLAUSES);
        return store(proposal, factory.renderAll(DocumentData.from(proposal, clauses, summary)));
    }

    @Transactional
    public List<ProposalDocument> store(Proposal proposal, Map<DocumentType, byte[]> rendered) {
        if (proposal == null || proposal.getId() == null || rendered == null) {
            throw new IllegalArgumentException("Persisted proposal and rendered PDFs are required");
        }
        List<ProposalDocument> stored = new ArrayList<>();
        Instant generatedAt = Instant.now();
        for (DocumentType type : DocumentType.values()) {
            byte[] bytes = rendered.get(type);
            if (bytes == null) {
                throw new IllegalArgumentException("Missing rendered PDF: " + type);
            }
            String fileName = type.fileNamePrefix() + "-" + proposal.getId() + ".pdf";
            ProposalDocument document = documents.findByProposalIdAndType(proposal.getId(), type)
                    .orElseGet(() -> new ProposalDocument(proposal, type, bytes, fileName, generatedAt));
            document.replaceContent(bytes, fileName, generatedAt);
            stored.add(documents.save(document));
        }
        return stored;
    }

    @Transactional(readOnly = true)
    public ProposalDocument get(Long agencyId, Long proposalId, DocumentType type) {
        requireAgencyProposal(agencyId, proposalId);
        return get(proposalId, type);
    }

    @Transactional(readOnly = true)
    public List<ProposalDocument> list(Long agencyId, Long proposalId) {
        requireAgencyProposal(agencyId, proposalId);
        return list(proposalId);
    }

    private void requireAgencyProposal(Long agencyId, Long proposalId) {
        proposals.findByIdAndBriefClientAgencyId(proposalId, agencyId)
                .orElseThrow(() -> new NoSuchElementException("Proposal not found: " + proposalId));
    }

    @Transactional(readOnly = true)
    public ProposalDocument get(Long proposalId, DocumentType type) {
        return documents.findByProposalIdAndType(proposalId, type)
                .orElseThrow(() -> new NoSuchElementException("Document not found: " + proposalId + "/" + type));
    }

    @Transactional(readOnly = true)
    public List<ProposalDocument> list(Long proposalId) {
        return documents.findByProposalIdOrderByTypeAsc(proposalId);
    }
}

package com.cotizaia.repository;

import com.cotizaia.document.DocumentType;
import com.cotizaia.domain.ProposalDocument;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Reads proposal documents by their proposal and unique type (project.txt section 9). */
public interface ProposalDocumentRepository extends JpaRepository<ProposalDocument, Long> {

    List<ProposalDocument> findByProposalIdOrderByTypeAsc(Long proposalId);

    Optional<ProposalDocument> findByProposalIdAndType(Long proposalId, DocumentType type);
}

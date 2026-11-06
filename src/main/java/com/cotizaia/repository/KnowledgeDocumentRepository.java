package com.cotizaia.repository;

import com.cotizaia.domain.KnowledgeDocument;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Loads past proposal documents, agency-scoped, for knowledge retrieval. */
public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, Long> {

    List<KnowledgeDocument> findByAgencyIdOrderByIdAsc(Long agencyId);

    java.util.Optional<KnowledgeDocument> findByIdAndAgencyId(Long id, Long agencyId);

    java.util.List<KnowledgeDocument> findByAgencyIdAndSourceProposalId(Long agencyId, Long sourceProposalId);
}

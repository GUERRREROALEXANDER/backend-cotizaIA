package com.cotizaia.repository;

import com.cotizaia.domain.KnowledgeDocument;
import org.springframework.data.jpa.repository.JpaRepository;

/** Loads past proposal documents, agency-scoped, for knowledge retrieval. */
public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, Long> {
}

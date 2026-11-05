package com.cotizaia.repository;

import com.cotizaia.domain.KnowledgeChunk;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Loads the chunks of a knowledge document, in reading order. */
public interface KnowledgeChunkRepository extends JpaRepository<KnowledgeChunk, Long> {

    List<KnowledgeChunk> findByDocumentIdOrderByOrdinalAsc(Long documentId);
}

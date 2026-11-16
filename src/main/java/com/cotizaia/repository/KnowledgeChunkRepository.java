package com.cotizaia.repository;

import com.cotizaia.domain.ChunkKind;
import com.cotizaia.domain.KnowledgeChunk;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Loads the chunks of a knowledge document, in reading order. */
public interface KnowledgeChunkRepository extends JpaRepository<KnowledgeChunk, Long> {

    List<KnowledgeChunk> findByDocumentIdOrderByOrdinalAsc(Long documentId);

    @Query("select c.document.id as documentId, c.kind as kind, count(c) as count from KnowledgeChunk c "
            + "where c.document.id in :documentIds group by c.document.id, c.kind")
    List<KindCount> countByDocumentIds(@Param("documentIds") Collection<Long> documentIds);

    /** Chunk total of one kind in one document. */
    interface KindCount {

        Long getDocumentId();

        ChunkKind getKind();

        long getCount();
    }
}

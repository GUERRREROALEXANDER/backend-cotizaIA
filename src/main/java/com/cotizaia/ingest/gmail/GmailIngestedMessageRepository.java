package com.cotizaia.ingest.gmail;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface GmailIngestedMessageRepository extends JpaRepository<GmailIngestedMessage, String> {

    /**
     * Atomic claim. {@code save()} would merge an entity with an assigned id
     * and silently overwrite, so the claim is an insert that does nothing on a
     * conflict; 1 means this caller owns the message, 0 means someone already
     * claimed it (poll and push racing, or a Pub/Sub redelivery).
     */
    @Modifying
    @Transactional
    @Query(value = "INSERT INTO gmail_ingested_messages (message_id, status, created_at, updated_at) "
            + "VALUES (:id, 'CLAIMED', :now, :now) ON CONFLICT DO NOTHING", nativeQuery = true)
    int claim(@Param("id") String messageId, @Param("now") Instant now);
}

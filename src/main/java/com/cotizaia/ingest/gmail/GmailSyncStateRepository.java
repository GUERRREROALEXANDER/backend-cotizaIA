package com.cotizaia.ingest.gmail;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GmailSyncStateRepository extends JpaRepository<GmailSyncState, String> {
}

package com.cotizaia.repository;

import com.cotizaia.domain.BriefAttachment;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Reads the single stored upload per brief; typed-channel briefs have none,
 * which the download endpoint reports as not found.
 */
public interface BriefAttachmentRepository extends JpaRepository<BriefAttachment, Long> {

    Optional<BriefAttachment> findByBriefId(Long briefId);
}

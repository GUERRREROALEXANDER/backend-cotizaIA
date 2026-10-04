package com.cotizaia.repository;

import com.cotizaia.domain.Notification;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Provides agency feed and proposal audit queries for notification records. */
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByAgencyIdOrderBySentAtDescIdDesc(Long agencyId);

    List<Notification> findByProposalIdOrderByIdAsc(Long proposalId);

    long countByProposalId(Long proposalId);
}

package com.cotizaia.repository;

import com.cotizaia.domain.WhatsappMessage;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** The WhatsApp outbox. Rows are locked while claimed so two service instances never send the same one. */
public interface WhatsappMessageRepository extends JpaRepository<WhatsappMessage, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from WhatsappMessage m where m.status = :status order by m.id")
    List<WhatsappMessage> lockByStatus(@Param("status") WhatsappMessage.Status status, Pageable page);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from WhatsappMessage m where m.id = :id")
    Optional<WhatsappMessage> lockById(@Param("id") Long id);

    long countByStatus(WhatsappMessage.Status status);
}

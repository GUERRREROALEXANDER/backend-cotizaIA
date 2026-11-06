package com.cotizaia.repository;

import com.cotizaia.domain.Brief;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository pattern: briefs are always read through client/agency scoping so
 * a caller cannot see another agency's intake history.
 */
public interface BriefRepository extends JpaRepository<Brief, Long> {

    List<Brief> findByClientIdOrderByIdAsc(Long clientId);

    long countByClientId(Long clientId);

    Optional<Brief> findByIdAndClientAgencyId(Long id, Long agencyId);

    /** Newest first, paged in SQL so the limit never loads more than it returns. */
    List<Brief> findByClientAgencyIdOrderByIdDesc(Long agencyId, Pageable pageable);

    @EntityGraph(attributePaths = {"client", "client.agency"})
    Optional<Brief> findWithClientById(Long id);
}

package com.cotizaia.repository;

import com.cotizaia.domain.Brief;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository pattern: briefs are always read through client/agency scoping so
 * a caller cannot see another agency's intake history.
 */
public interface BriefRepository extends JpaRepository<Brief, Long> {

    List<Brief> findByClientIdOrderByIdAsc(Long clientId);

    long countByClientId(Long clientId);

    Optional<Brief> findByIdAndClientAgencyId(Long id, Long agencyId);
}

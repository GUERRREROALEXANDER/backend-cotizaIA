package com.cotizaia.repository;

import com.cotizaia.domain.Role;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository pattern: agency scoping lives in the query methods, so callers
 * cannot read or mutate another agency's rate table by accident (same contract
 * as {@link ServiceCatalogRepository}).
 */
public interface RoleRepository extends JpaRepository<Role, Long> {

    List<Role> findByAgencyIdOrderByIdAsc(Long agencyId);

    Optional<Role> findByIdAndAgencyId(Long id, Long agencyId);

    Optional<Role> findByAgencyIdAndName(Long agencyId, String name);

    boolean existsByAgencyIdAndName(Long agencyId, String name);
}

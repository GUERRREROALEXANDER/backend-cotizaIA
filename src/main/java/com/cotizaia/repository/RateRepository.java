package com.cotizaia.repository;

import com.cotizaia.domain.Rate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository pattern: rates are always queried through agency/role keys so a
 * pricing call cannot pick up a foreign rate, and the Singleton cache is the
 * only component that reads them for pricing.
 */
public interface RateRepository extends JpaRepository<Rate, Long> {

    List<Rate> findByAgencyIdOrderByIdAsc(Long agencyId);

    List<Rate> findByRoleIdOrderByIdAsc(Long roleId);

    Optional<Rate> findByIdAndAgencyId(Long id, Long agencyId);

    boolean existsByRoleId(Long roleId);

    long countByRoleId(Long roleId);
}

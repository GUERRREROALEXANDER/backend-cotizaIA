package com.cotizaia.repository;

import com.cotizaia.domain.ServiceCatalog;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository pattern: agency scoping lives in the query methods, so callers
 * cannot read or mutate another agency's catalog by accident.
 */
public interface ServiceCatalogRepository extends JpaRepository<ServiceCatalog, Long> {

    List<ServiceCatalog> findByAgencyIdOrderByIdAsc(Long agencyId);

    List<ServiceCatalog> findByAgencyIdAndCategoryIdOrderByIdAsc(Long agencyId, Long categoryId);

    Optional<ServiceCatalog> findByIdAndAgencyId(Long id, Long agencyId);

    Optional<ServiceCatalog> findByAgencyIdAndName(Long agencyId, String name);

    boolean existsByAgencyIdAndName(Long agencyId, String name);
}

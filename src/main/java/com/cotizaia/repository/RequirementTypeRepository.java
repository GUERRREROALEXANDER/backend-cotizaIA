package com.cotizaia.repository;

import com.cotizaia.domain.RequirementType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RequirementTypeRepository extends JpaRepository<RequirementType, Long> {

    List<RequirementType> findByServiceCatalogAgencyIdOrderByIdAsc(Long agencyId);

    List<RequirementType> findByServiceCatalogIdOrderByIdAsc(Long serviceCatalogId);

    List<RequirementType> findByServiceCatalogAgencyIdAndServiceCatalogIdOrderByIdAsc(
            Long agencyId, Long serviceCatalogId);

    Optional<RequirementType> findByIdAndServiceCatalogId(Long id, Long serviceCatalogId);

    Optional<RequirementType> findByServiceCatalogIdAndName(Long serviceCatalogId, String name);

    boolean existsByServiceCatalogIdAndName(Long serviceCatalogId, String name);

    long countByServiceCatalogId(Long serviceCatalogId);
}

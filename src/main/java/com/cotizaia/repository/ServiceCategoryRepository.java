package com.cotizaia.repository;

import com.cotizaia.domain.ServiceCategory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceCategoryRepository extends JpaRepository<ServiceCategory, Long> {

    List<ServiceCategory> findByAgencyIdOrderByIdAsc(Long agencyId);

    Optional<ServiceCategory> findByIdAndAgencyId(Long id, Long agencyId);

    Optional<ServiceCategory> findByAgencyIdAndName(Long agencyId, String name);

    boolean existsByAgencyIdAndName(Long agencyId, String name);
}

package com.cotizaia.repository;

import com.cotizaia.domain.Agency;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository pattern: the domain depends on this interface, not on JPA,
 * so persistence details stay out of the aggregate.
 */
public interface AgencyRepository extends JpaRepository<Agency, Long> {
}

package com.cotizaia.repository;

import com.cotizaia.domain.AppUser;
import com.cotizaia.domain.UserRole;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    List<AppUser> findByAgencyIdOrderByIdAsc(Long agencyId);

    Optional<AppUser> findByAgencyIdAndRole(Long agencyId, UserRole role);

    Optional<AppUser> findByAgencyIdAndEmail(Long agencyId, String email);

    boolean existsByAgencyIdAndRole(Long agencyId, UserRole role);
}

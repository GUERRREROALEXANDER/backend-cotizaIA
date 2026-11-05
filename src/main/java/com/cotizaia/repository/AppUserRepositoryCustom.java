package com.cotizaia.repository;

import com.cotizaia.domain.AppUser;
import java.util.Optional;

public interface AppUserRepositoryCustom {
    Optional<AppUser> findByLoginEmail(String loginEmail);

    boolean existsByLoginEmail(String loginEmail);

    Optional<AppUser> findByAgencyIdAndEmail(Long agencyId, String email);
}
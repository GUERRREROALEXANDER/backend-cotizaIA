package com.cotizaia.repository;

import com.cotizaia.domain.Client;
import java.util.Optional;

public interface ClientRepositoryCustom {
    Optional<Client> findByAgencyIdAndEmail(Long agencyId, String email);
}
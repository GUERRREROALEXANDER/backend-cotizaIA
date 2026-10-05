package com.cotizaia.repository;

import com.cotizaia.domain.Client;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientRepository extends JpaRepository<Client, Long> {

    Optional<Client> findByIdAndAgencyId(Long id, Long agencyId);

    List<Client> findByAgencyIdOrderByIdAsc(Long agencyId);

    Optional<Client> findByAgencyIdAndEmail(Long agencyId, String email);
}

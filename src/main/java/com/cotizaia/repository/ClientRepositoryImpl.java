package com.cotizaia.repository;

import com.cotizaia.crypto.BlindIndex;
import com.cotizaia.domain.Client;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;

public class ClientRepositoryImpl implements ClientRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private BlindIndex blindIndex;

    @Override
    public Optional<Client> findByAgencyIdAndEmail(Long agencyId, String email) {
        String hash = blindIndex.hash(email);
        if (hash == null) {
            return Optional.empty();
        }
        List<Client> result = entityManager
                .createQuery("select c from Client c where c.agency.id = :a and c.emailHash = :h", Client.class)
                .setParameter("a", agencyId).setParameter("h", hash).getResultList();
        return result.stream().findFirst();
    }
}
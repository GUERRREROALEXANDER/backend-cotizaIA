package com.cotizaia.repository;

import com.cotizaia.crypto.BlindIndex;
import com.cotizaia.domain.AppUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;

public class AppUserRepositoryImpl implements AppUserRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private BlindIndex blindIndex;

    @Override
    public Optional<AppUser> findByLoginEmail(String loginEmail) {
        String hash = blindIndex.hash(loginEmail);
        if (hash == null) {
            return Optional.empty();
        }
        List<AppUser> result = entityManager
                .createQuery("select u from AppUser u where u.loginEmailHash = :h", AppUser.class)
                .setParameter("h", hash).getResultList();
        return result.stream().findFirst();
    }

    @Override
    public boolean existsByLoginEmail(String loginEmail) {
        return findByLoginEmail(loginEmail).isPresent();
    }

    @Override
    public Optional<AppUser> findByAgencyIdAndEmail(Long agencyId, String email) {
        String hash = blindIndex.hash(email);
        if (hash == null) {
            return Optional.empty();
        }
        List<AppUser> result = entityManager
                .createQuery("select u from AppUser u where u.agency.id = :a and u.emailHash = :h", AppUser.class)
                .setParameter("a", agencyId).setParameter("h", hash).getResultList();
        return result.stream().findFirst();
    }
}
package com.cotizaia.repository;

import com.cotizaia.domain.WhatsappContact;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Verified WhatsApp numbers, keyed by the user who verified them. */
public interface WhatsappContactRepository extends JpaRepository<WhatsappContact, Long> {

    /** The contact of this number, looked up by its blind index (see V32). */
    Optional<WhatsappContact> findByPhoneHash(String phoneHash);
}

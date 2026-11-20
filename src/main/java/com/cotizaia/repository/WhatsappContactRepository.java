package com.cotizaia.repository;

import com.cotizaia.domain.WhatsappContact;
import org.springframework.data.jpa.repository.JpaRepository;

/** Verified WhatsApp numbers, keyed by the user who verified them. */
public interface WhatsappContactRepository extends JpaRepository<WhatsappContact, Long> {
}

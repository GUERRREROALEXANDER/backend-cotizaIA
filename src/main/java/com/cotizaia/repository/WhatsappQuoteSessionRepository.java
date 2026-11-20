package com.cotizaia.repository;

import com.cotizaia.domain.WhatsappQuoteSession;
import org.springframework.data.jpa.repository.JpaRepository;

/** Each user's place in the WhatsApp quotation chat, keyed by user. */
public interface WhatsappQuoteSessionRepository extends JpaRepository<WhatsappQuoteSession, Long> {
}

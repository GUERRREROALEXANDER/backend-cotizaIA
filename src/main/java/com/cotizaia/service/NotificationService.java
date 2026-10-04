package com.cotizaia.service;

import com.cotizaia.domain.Agency;
import com.cotizaia.domain.Notification;
import com.cotizaia.domain.Proposal;
import com.cotizaia.repository.NotificationRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stores observer output as JSON for a durable agency feed (project.txt section 6: Observer).
 * Serialization failure aborts the caller's transition transaction rather than losing its audit record.
 */
@Service
public class NotificationService {

    private final NotificationRepository repository;
    private final ObjectMapper objectMapper;

    public NotificationService(NotificationRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Notification record(Agency agency, Proposal proposal, String channel,
            String eventType, Map<String, Object> payload) {
        if (payload == null) {
            throw new IllegalArgumentException("payload must not be null");
        }
        try {
            String json = objectMapper.writeValueAsString(payload);
            return repository.save(new Notification(agency, proposal, channel, eventType, json, Instant.now()));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize notification payload", exception);
        }
    }

    @Transactional(readOnly = true)
    public List<Notification> listForAgency(Long agencyId) {
        return repository.findByAgencyIdOrderBySentAtDescIdDesc(agencyId);
    }
}

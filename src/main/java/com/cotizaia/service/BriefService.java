package com.cotizaia.service;

import com.cotizaia.domain.Brief;
import com.cotizaia.domain.BriefChannel;
import com.cotizaia.domain.Client;
import com.cotizaia.ingest.BriefSource;
import com.cotizaia.ingest.BriefSourceRegistry;
import com.cotizaia.ingest.NormalizedBrief;
import com.cotizaia.repository.BriefRepository;
import com.cotizaia.repository.ClientRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ingest entry point for briefs. The service is channel-agnostic: it resolves
 * the {@link BriefSource} adapter from the registry, asks it to normalize the
 * payload, and persists the result. There is no {@code if}/{@code switch} on
 * the channel anywhere in the pipeline, which is what makes a new channel a new
 * adapter class only.
 */
@Service
public class BriefService {

    private final BriefRepository briefRepository;
    private final ClientRepository clientRepository;
    private final BriefSourceRegistry briefSourceRegistry;
    private final ObjectMapper objectMapper;

    public BriefService(
            BriefRepository briefRepository,
            ClientRepository clientRepository,
            BriefSourceRegistry briefSourceRegistry,
            ObjectMapper objectMapper) {
        this.briefRepository = briefRepository;
        this.clientRepository = clientRepository;
        this.briefSourceRegistry = briefSourceRegistry;
        this.objectMapper = objectMapper;
    }

    /**
     * Normalizes one raw channel payload into a persisted {@link Brief}.
     *
     * <p>The original payload is serialized verbatim into {@code rawPayload}
     * for audit, independently of the normalized {@code rawText}, so the
     * normalization can never destroy the evidence of what was sent.
     *
     * @throws NoSuchElementException when the client does not exist.
     * @throws IllegalArgumentException when the payload is missing a required field.
     */
    @Transactional
    public Brief ingest(BriefChannel channel, Long clientId, Map<String, Object> payload) {
        Client client = clientRepository
                .findById(clientId)
                .orElseThrow(() -> new NoSuchElementException("Client not found: " + clientId));

        BriefSource source = briefSourceRegistry.forChannel(channel);
        NormalizedBrief normalized = source.normalize(payload);

        Instant receivedAt = normalized.receivedAt() != null ? normalized.receivedAt() : Instant.now();
        Brief brief = new Brief(client, channel, normalized.rawText(), serialize(payload), receivedAt);
        return briefRepository.save(brief);
    }

    @Transactional(readOnly = true)
    public Brief get(Long id) {
        return briefRepository
                .findById(id)
                .orElseThrow(() -> new NoSuchElementException("Brief not found: " + id));
    }

    private String serialize(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Payload is not serializable for audit", e);
        }
    }
}

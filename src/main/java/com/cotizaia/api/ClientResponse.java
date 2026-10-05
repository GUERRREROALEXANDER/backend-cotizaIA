package com.cotizaia.api;

import com.cotizaia.domain.Client;
import java.time.Instant;

/** Public projection without persistence internals. */
public record ClientResponse(Long id, String name, String email, String company, Instant createdAt) {

    public static ClientResponse from(Client client) {
        return new ClientResponse(client.getId(), client.getName(), client.getEmail(), client.getCompany(),
                client.getCreatedAt());
    }
}

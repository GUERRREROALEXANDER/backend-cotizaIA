package com.cotizaia.api;

import jakarta.validation.constraints.NotBlank;

/** Body of POST /api/proposals/{id}/extras: the decorator kind to apply. */
public record AddExtraRequest(@NotBlank String type) {
}

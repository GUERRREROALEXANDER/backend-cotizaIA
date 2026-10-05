package com.cotizaia.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Validated input for agency operations. */
public record CatalogRequest(@NotBlank @Size(max = 255) String name,
        @Size(max = 1000) String description, @Positive Long categoryId) {
}

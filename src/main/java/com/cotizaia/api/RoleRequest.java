package com.cotizaia.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Validated input for agency operations. */
public record RoleRequest(@NotBlank @Size(max = 255) String name) {
}

package com.cotizaia.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Validated input for agency operations. */
public record ClientRequest(@NotBlank @Size(max = 255) String name,
        @Email @Size(max = 255) String email, @Size(max = 255) String company) {
}

package com.cotizaia.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Address asking for another verification link (spec 16); the endpoint never reveals whether it exists. */
public record ResendVerificationRequest(@NotBlank @Email @Size(max = 255) String email) {
}

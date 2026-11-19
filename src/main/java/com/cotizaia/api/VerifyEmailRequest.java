package com.cotizaia.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Verification link token from the mail (spec 16). It is never logged or echoed back. */
public record VerifyEmailRequest(@NotBlank @Size(max = 128) String token) {

    @Override
    public String toString() {
        return "VerifyEmailRequest[token=***]";
    }
}

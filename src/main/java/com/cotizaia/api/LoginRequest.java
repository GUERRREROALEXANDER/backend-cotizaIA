package com.cotizaia.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Login input with masked diagnostic output to avoid exposing passwords. */
public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {

    @Override
    public String toString() {
        return "LoginRequest[email=" + email + ", password=***]";
    }
}

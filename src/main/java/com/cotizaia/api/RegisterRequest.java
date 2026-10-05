package com.cotizaia.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Registration input with masked diagnostic output to avoid exposing passwords. */
public record RegisterRequest(
        @NotBlank @Size(max = 255) String agencyName,
        @NotBlank String ownerFullName,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 100) String password) {

    @Override
    public String toString() {
        return "RegisterRequest[agencyName=" + agencyName + ", ownerFullName=" + ownerFullName
                + ", email=" + email + ", password=***]";
    }
}

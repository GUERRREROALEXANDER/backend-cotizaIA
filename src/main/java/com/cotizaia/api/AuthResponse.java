package com.cotizaia.api;

import com.cotizaia.service.AuthService.LoginResult;
import com.cotizaia.service.AuthService.RegistrationResult;
import java.time.Instant;

/** Bearer token and public identity metadata returned after successful authentication. */
public record AuthResponse(String token, String tokenType, Instant expiresAt,
        Long agencyId, Long userId, String role) {

    public static AuthResponse from(RegistrationResult result) {
        return new AuthResponse(result.issuedToken().token(), "Bearer", result.issuedToken().expiresAt(),
                result.agencyId(), result.userId(), result.role());
    }

    public static AuthResponse from(LoginResult result) {
        return new AuthResponse(result.issuedToken().token(), "Bearer", result.issuedToken().expiresAt(),
                result.agencyId(), result.userId(), result.role());
    }
}

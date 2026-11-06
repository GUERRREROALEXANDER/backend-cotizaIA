package com.cotizaia.api;

import com.cotizaia.service.AuthService.LoginResult;
import com.cotizaia.service.AuthService.RegistrationResult;
import java.time.Instant;

/**
 * Public identity metadata returned after successful authentication (spec 02).
 * Credentials travel only in HttpOnly cookies the server sets itself, so no
 * token field appears in a JavaScript-readable body.
 */
public record AuthResponse(Instant expiresAt, Long agencyId, Long userId, String role) {

    public static AuthResponse from(RegistrationResult result) {
        return new AuthResponse(result.accessToken().expiresAt(), result.agencyId(), result.userId(),
                result.role());
    }

    public static AuthResponse from(LoginResult result) {
        return new AuthResponse(result.accessToken().expiresAt(), result.agencyId(), result.userId(),
                result.role());
    }
}

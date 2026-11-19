package com.cotizaia.api;

import com.cotizaia.service.AuthService.LoginResult;
import com.cotizaia.service.AuthService.RegistrationResult;
import java.time.Instant;

/**
 * Public identity metadata returned after successful authentication (spec 02).
 * Credentials travel only in HttpOnly cookies the server sets itself, so no
 * token field appears in a JavaScript-readable body.
 *
 * <p>Spec 16 adds {@code emailVerificationRequired}: the register response uses it to ask the client
 * for the "confirma tu correo" screen. A successful login always reports false, because it only
 * succeeds when verification (if the deployment enforces it) already happened.</p>
 */
public record AuthResponse(Instant expiresAt, Long agencyId, Long userId, String role,
        boolean emailVerificationRequired) {

    public static AuthResponse from(RegistrationResult result) {
        return new AuthResponse(result.accessToken().expiresAt(), result.agencyId(), result.userId(), result.role(),
                result.emailVerificationRequired());
    }

    public static AuthResponse from(LoginResult result) {
        return new AuthResponse(result.accessToken().expiresAt(), result.agencyId(), result.userId(), result.role(),
                false);
    }
}

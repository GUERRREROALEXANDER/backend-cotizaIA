package com.cotizaia.config;

import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Resolves the authenticated user behind the current call (spec 16). Services need it to address
 * a document email to the person who performed the action; the demo seeder and internal calls run
 * with no security context, which is why the answer is optional instead of an exception.
 */
@Component
public class CurrentActor {

    public Optional<Long> userId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwt) {
            try {
                return Optional.of(Long.valueOf(jwt.getToken().getSubject()));
            } catch (NumberFormatException | NullPointerException exception) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }
}
